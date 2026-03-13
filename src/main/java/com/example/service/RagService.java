package com.example.service;

import com.example.entity.DocumentMetadata;
import com.example.repository.DocumentMetadataRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) service.
 *
 * Ingest flow:
 *   PDF upload → extract text (PDFBox) → split into chunks (TokenTextSplitter)
 *   → embed each chunk (OpenAI text-embedding-3-small) → store in pgvector
 *
 * Query flow:
 *   User question → embed question → similarity search in pgvector (top-5, filtered by userId)
 *   → inject retrieved chunks as context → generate answer via GPT
 */
@Service
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_K = 5;

    private final VectorStore vectorStore;
    private final OpenAiChatModel chatModel;
    private final DocumentMetadataRepository docMetaRepo;
    private final TokenTextSplitter splitter;

    public RagService(VectorStore vectorStore,
                      OpenAiChatModel chatModel,
                      DocumentMetadataRepository docMetaRepo) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.docMetaRepo = docMetaRepo;
        this.splitter = new TokenTextSplitter();
    }

    // ─── Ingest ───────────────────────────────────────────────────────────────

    /**
     * Parses a PDF, splits it into chunks, embeds each chunk, and stores them
     * in pgvector with userId + docId metadata for per-user retrieval and deletion.
     *
     * @return the new DocumentMetadata id
     */
    @Transactional
    public String ingestPdf(Long userId, MultipartFile file) throws IOException {
        String text = extractTextFromPdf(file);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Could not extract text from the uploaded PDF.");
        }
        return ingestText(userId, file.getOriginalFilename(), text);
    }

    /**
     * Splits raw text into chunks, embeds them, and stores in pgvector.
     * Also saves DocumentMetadata so the document can be listed/deleted later.
     */
    @Transactional
    public String ingestText(Long userId, String filename, String text) {
        String docId = UUID.randomUUID().toString();

        // Split text into token-sized chunks
        Document sourceDoc = new Document(text);
        List<Document> chunks = splitter.apply(List.of(sourceDoc));

        // Tag each chunk with userId and docId so we can filter by user and delete by docId
        List<Document> taggedChunks = chunks.stream().map(chunk -> {
            String chunkId = UUID.randomUUID().toString();
            Map<String, Object> meta = new java.util.HashMap<>(chunk.getMetadata());
            meta.put("docId", docId);
            meta.put("userId", userId.toString());
            meta.put("filename", filename != null ? filename : "unknown");
            return new Document(chunkId, chunk.getContent(), meta);
        }).collect(Collectors.toList());

        // Embed and store in pgvector
        vectorStore.add(taggedChunks);

        // Save metadata so users can list and delete their documents
        List<String> chunkIds = taggedChunks.stream()
                .map(Document::getId)
                .collect(Collectors.toList());

        DocumentMetadata meta = new DocumentMetadata(docId, userId, filename != null ? filename : "unknown");
        meta.setChunkCount(chunks.size());
        meta.setChunkIds(chunkIds);
        docMetaRepo.save(meta);

        log.info("Ingested document '{}' for user {} — {} chunks", filename, userId, chunks.size());
        return docId;
    }

    // ─── Query ────────────────────────────────────────────────────────────────

    /**
     * Answers a user's question using only documents they have uploaded.
     *
     * Flow: embed question → similarity search (filtered by userId) → augment prompt → GPT answer
     */
    public String query(Long userId, String question) {
        // Retrieve top-K chunks relevant to the question, scoped to this user's documents
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<Document> relevant = vectorStore.similaritySearch(
                SearchRequest.query(question)
                        .withTopK(TOP_K)
                        .withFilterExpression(b.eq("userId", userId.toString()).build())
        );

        if (relevant.isEmpty()) {
            return "No relevant information found in your uploaded documents. " +
                   "Please upload a document first, then ask questions about it.";
        }

        // Build context from retrieved chunks
        String context = relevant.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n---\n\n"));

        // Augmented prompt: inject retrieved context before the question
        String augmentedPrompt = """
                You are a helpful assistant. Answer the question using ONLY the context provided below.
                If the answer is not in the context, say "I couldn't find that information in the uploaded documents."
                Do not use your general training knowledge — only use the provided context.

                Context from uploaded documents:
                %s

                Question: %s
                """.formatted(context, question);

        return chatModel.call(augmentedPrompt);
    }

    // ─── Document management ──────────────────────────────────────────────────

    /** Returns metadata for all documents uploaded by a user. */
    public List<DocumentMetadata> listDocuments(Long userId) {
        return docMetaRepo.findByUserIdOrderByUploadedAtDesc(userId);
    }

    /**
     * Deletes a document's chunks from pgvector and removes the metadata record.
     * Only the owning user can delete their document.
     */
    @Transactional
    public void deleteDocument(Long userId, String docId) {
        DocumentMetadata meta = docMetaRepo.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        // Delete all chunk vectors from pgvector
        if (!meta.getChunkIds().isEmpty()) {
            vectorStore.delete(meta.getChunkIds());
        }

        docMetaRepo.delete(meta);
        log.info("Deleted document '{}' for user {}", docId, userId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String extractTextFromPdf(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}
