package com.example.repository;

import com.example.entity.PostMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostMetricsRepository extends JpaRepository<PostMetrics, Long> {
    Optional<PostMetrics> findByPlatformPostId(String platformPostId);
    List<PostMetrics> findByPublishJobId(Long publishJobId);
}
