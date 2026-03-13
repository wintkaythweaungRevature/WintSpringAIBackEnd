package com.example.controller;

import com.example.entity.DocumentMetadata;
import com.example.entity.User;
import com.example.service.RagService;
import com.example.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST API for RAG (Retrieval-Augmented Generation).
 *
 * All endpoints require authentication. Upload and query require a MEMBER subscription.
 *
 * POST   /api/rag/upload              — upload a PDF and ingest into vector store
 * POST   /api/rag/query               — ask a question against your uploaded docs
 * GET    /api/rag/documents           — list your uploaded documents
 * DELETE /api/rag/documents/{docId}   — delete a document and its vectors
 */
@RestController
@RequestMapping("/api/rag")
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagController {

    private final RagService ragService;
    private final UserService userService;

    public RagController(RagService ragService, UserService userService) {
        this.ragService = ragService;
        this.userService = userService;
    }

    // ─── Upload document ──────────────────────────────────────────────────────

    /**
     * Upload a PDF document. It is parsed, chunked, embedded, and stored in pgvector.
     * Requires MEMBER subscription.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {

        ResponseEntity<?> authCheck = requireMember(userDetails);
        if (authCheck != null) return authCheck;

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only PDF files are supported"));
        }

        try {
            User user = userService.findByEmail(userDetails.getUsername());
            String docId = ragService.ingestPdf(user.getId(), file);
            return ResponseEntity.ok(Map.of(
                    "docId", docId,
                    "filename", filename,
                    "message", "Document uploaded and indexed successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process PDF: " + e.getMessage()));
        }
    }

    // ─── Query ────────────────────────────────────────────────────────────────

    /**
     * Ask a question. The answer is generated from your uploaded documents only.
     * Requires MEMBER subscription.
     */
    @PostMapping("/query")
    public ResponseEntity<?> query(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {

        ResponseEntity<?> authCheck = requireMember(userDetails);
        if (authCheck != null) return authCheck;

        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question is required"));
        }

        try {
            User user = userService.findByEmail(userDetails.getUsername());
            String answer = ragService.query(user.getId(), question);
            return ResponseEntity.ok(Map.of("answer", answer, "question", question));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Query failed: " + e.getMessage()));
        }
    }

    // ─── List documents ───────────────────────────────────────────────────────

    /** Returns all documents the authenticated user has uploaded. */
    @GetMapping("/documents")
    public ResponseEntity<?> listDocuments(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            List<DocumentMetadata> docs = ragService.listDocuments(user.getId());
            List<Map<String, Object>> result = docs.stream().map(d -> Map.<String, Object>of(
                    "docId", d.getId(),
                    "filename", d.getFilename(),
                    "chunkCount", d.getChunkCount(),
                    "uploadedAt", d.getUploadedAt().toString()
            )).toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Could not list documents"));
        }
    }

    // ─── Delete document ──────────────────────────────────────────────────────

    /** Deletes a document and all its vectors from pgvector. */
    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<?> deleteDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String docId) {

        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            ragService.deleteDocument(user.getId(), docId);
            return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Could not delete document"));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<?> requireMember(UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            if (!userService.hasActivePaidAccess(user)) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Member subscription required to use Document Q&A"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        return null;
    }
}
