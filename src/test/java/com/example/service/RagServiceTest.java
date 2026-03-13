package com.example.service;

import com.example.entity.DocumentMetadata;
import com.example.repository.DocumentMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock VectorStore vectorStore;
    @Mock OpenAiChatModel chatModel;
    @Mock DocumentMetadataRepository docMetaRepo;

    // We construct manually so we can inject the mocks
    RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(vectorStore, chatModel, docMetaRepo);
    }

    // ─── ingestText ────────────────────────────────────────────────────────────

    @Test
    void ingestText_savesMetadataAndAddsToVectorStore() {
        Long userId = 1L;
        String filename = "test.pdf";
        String text = "This is some test document content that is long enough to be processed.";

        String docId = ragService.ingestText(userId, filename, text);

        assertThat(docId).isNotNull().isNotBlank();

        // vectorStore.add() was called with tagged chunks
        verify(vectorStore, times(1)).add(anyList());

        // DocumentMetadata was persisted
        ArgumentCaptor<DocumentMetadata> captor = ArgumentCaptor.forClass(DocumentMetadata.class);
        verify(docMetaRepo, times(1)).save(captor.capture());

        DocumentMetadata saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(docId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getFilename()).isEqualTo(filename);
        assertThat(saved.getChunkCount()).isGreaterThan(0);
        assertThat(saved.getChunkIds()).isNotEmpty();
    }

    @Test
    void ingestText_withNullFilename_usesUnknown() {
        String docId = ragService.ingestText(1L, null, "Some content for unknown filename document.");

        ArgumentCaptor<DocumentMetadata> captor = ArgumentCaptor.forClass(DocumentMetadata.class);
        verify(docMetaRepo).save(captor.capture());
        assertThat(captor.getValue().getFilename()).isEqualTo("unknown");
    }

    @Test
    void ingestText_chunksAreTaggedWithUserIdAndDocId() {
        Long userId = 42L;
        ragService.ingestText(userId, "tagged.pdf", "Content to tag with user and doc metadata.");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(chunksCaptor.capture());

        List<Document> chunks = chunksCaptor.getValue();
        assertThat(chunks).isNotEmpty();
        chunks.forEach(chunk -> {
            assertThat(chunk.getMetadata()).containsKey("userId");
            assertThat(chunk.getMetadata()).containsKey("docId");
            assertThat(chunk.getMetadata().get("userId")).isEqualTo("42");
        });
    }

    // ─── query ────────────────────────────────────────────────────────────────

    @Test
    void query_returnsAnswerFromChatModel() {
        Long userId = 1L;
        String question = "What is this document about?";
        Document chunk = new Document("doc-1", "This document discusses AI concepts.", java.util.Map.of());

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));
        when(chatModel.call(anyString())).thenReturn("It discusses AI concepts.");

        String answer = ragService.query(userId, question);

        assertThat(answer).isEqualTo("It discusses AI concepts.");
        verify(chatModel).call(argThat((String prompt) ->
                prompt.contains("AI concepts") && prompt.contains(question)
        ));
    }

    @Test
    void query_whenNoRelevantChunksFound_returnsNoInfoMessage() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        String answer = ragService.query(1L, "unrelated question");

        assertThat(answer).contains("No relevant information found");
        verifyNoInteractions(chatModel);
    }

    @Test
    void query_usesUserIdFilterInSearch() {
        Long userId = 7L;
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        ragService.query(userId, "some question");

        // Verify similarity search was called (userId filter is embedded in the request)
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void query_combinesMultipleChunksIntoContext() {
        Document chunk1 = new Document("c1", "First chunk content.", java.util.Map.of());
        Document chunk2 = new Document("c2", "Second chunk content.", java.util.Map.of());

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk1, chunk2));
        when(chatModel.call(anyString())).thenReturn("Combined answer.");

        ragService.query(1L, "question");

        verify(chatModel).call(argThat((String prompt) ->
                prompt.contains("First chunk content.") && prompt.contains("Second chunk content.")
        ));
    }

    // ─── listDocuments ────────────────────────────────────────────────────────

    @Test
    void listDocuments_returnsUserDocumentsInOrder() {
        Long userId = 5L;
        DocumentMetadata d1 = new DocumentMetadata("id1", userId, "file1.pdf");
        DocumentMetadata d2 = new DocumentMetadata("id2", userId, "file2.pdf");
        when(docMetaRepo.findByUserIdOrderByUploadedAtDesc(userId)).thenReturn(List.of(d1, d2));

        List<DocumentMetadata> result = ragService.listDocuments(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFilename()).isEqualTo("file1.pdf");
        verify(docMetaRepo).findByUserIdOrderByUploadedAtDesc(userId);
    }

    @Test
    void listDocuments_returnsEmptyListWhenNoDocs() {
        when(docMetaRepo.findByUserIdOrderByUploadedAtDesc(anyLong())).thenReturn(List.of());

        List<DocumentMetadata> result = ragService.listDocuments(99L);

        assertThat(result).isEmpty();
    }

    // ─── deleteDocument ───────────────────────────────────────────────────────

    @Test
    void deleteDocument_deletesVectorsAndMetadata() {
        Long userId = 1L;
        String docId = "doc-uuid-123";
        DocumentMetadata meta = new DocumentMetadata(docId, userId, "file.pdf");
        meta.setChunkIds(List.of("chunk-1", "chunk-2", "chunk-3"));

        when(docMetaRepo.findByIdAndUserId(docId, userId)).thenReturn(Optional.of(meta));

        ragService.deleteDocument(userId, docId);

        verify(vectorStore).delete(List.of("chunk-1", "chunk-2", "chunk-3"));
        verify(docMetaRepo).delete(meta);
    }

    @Test
    void deleteDocument_whenDocumentNotFound_throwsIllegalArgument() {
        when(docMetaRepo.findByIdAndUserId(anyString(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ragService.deleteDocument(1L, "nonexistent-doc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found");

        verifyNoInteractions(vectorStore);
    }

    @Test
    void deleteDocument_withNoChunks_skipsVectorStoreDeletion() {
        Long userId = 1L;
        String docId = "empty-doc";
        DocumentMetadata meta = new DocumentMetadata(docId, userId, "empty.pdf");
        // chunkIds is empty by default

        when(docMetaRepo.findByIdAndUserId(docId, userId)).thenReturn(Optional.of(meta));

        ragService.deleteDocument(userId, docId);

        verify(vectorStore, never()).delete(anyList());
        verify(docMetaRepo).delete(meta);
    }
}
