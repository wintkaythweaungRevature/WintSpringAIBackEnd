package com.example.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks documents uploaded by a user for RAG (Retrieval-Augmented Generation).
 * Each document is split into chunks that are stored in the pgvector vector_store table.
 * The chunkIds list maps back to those vector store rows so they can be deleted.
 */
@Entity
@Table(name = "document_metadata")
@Data
@NoArgsConstructor
public class DocumentMetadata {

    @Id
    private String id; // UUID, stored as metadata in vector_store too

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String filename;

    private int chunkCount;

    private LocalDateTime uploadedAt;

    /** IDs of vector_store rows created for this document's chunks. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "document_chunk_ids", joinColumns = @JoinColumn(name = "doc_id"))
    @Column(name = "chunk_id")
    private List<String> chunkIds = new ArrayList<>();

    public DocumentMetadata(String id, Long userId, String filename) {
        this.id = id;
        this.userId = userId;
        this.filename = filename;
        this.uploadedAt = LocalDateTime.now();
    }
}
