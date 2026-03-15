package com.example.repository;

import com.example.entity.VideoVariant;
import com.example.entity.VideoVariant.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoVariantRepository extends JpaRepository<VideoVariant, Long> {
    List<VideoVariant> findByVideoId(Long videoId);
    List<VideoVariant> findByVideoIdAndApprovalStatus(Long videoId, ApprovalStatus status);
}
