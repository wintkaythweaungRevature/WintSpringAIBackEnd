package com.example.repository;

import com.example.entity.Video;
import com.example.entity.Video.VideoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoRepository extends JpaRepository<Video, Long> {
    List<Video> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Video> findByUserIdAndStatus(Long userId, VideoStatus status);
}
