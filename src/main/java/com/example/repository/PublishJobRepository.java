package com.example.repository;

import com.example.entity.PublishJob;
import com.example.entity.PublishJob.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PublishJobRepository extends JpaRepository<PublishJob, Long> {
    List<PublishJob> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<PublishJob> findByStatusAndScheduledAtBefore(JobStatus status, LocalDateTime before);
    List<PublishJob> findByStatus(JobStatus status);
}
