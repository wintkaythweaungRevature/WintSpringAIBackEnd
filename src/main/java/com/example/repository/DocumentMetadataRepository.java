package com.example.repository;

import com.example.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, String> {

    List<DocumentMetadata> findByUserIdOrderByUploadedAtDesc(Long userId);

    Optional<DocumentMetadata> findByIdAndUserId(String id, Long userId);

    void deleteByIdAndUserId(String id, Long userId);
}
