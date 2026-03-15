package com.example.service;

import com.example.entity.PublishJob;
import com.example.entity.VideoVariant;
import com.example.entity.VideoVariant.ApprovalStatus;
import com.example.entity.PublishJob.JobStatus;
import com.example.repository.PublishJobRepository;
import com.example.repository.VideoRepository;
import com.example.repository.VideoVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Simple scheduling: user schedules their own video per platform.
 * No roles, no approval - user owns their content and chooses when to post.
 */
@Service
public class ScheduleService {

    private final VideoVariantRepository variantRepo;
    private final PublishJobRepository jobRepo;
    private final VideoRepository videoRepo;

    public ScheduleService(VideoVariantRepository variantRepo, PublishJobRepository jobRepo,
                           VideoRepository videoRepo) {
        this.variantRepo = variantRepo;
        this.jobRepo = jobRepo;
        this.videoRepo = videoRepo;
    }

    /**
     * Schedule a variant to publish at a specific time.
     * User must own the video. No approval required.
     */
    @Transactional
    public PublishJob schedulePublish(Long variantId, Long userId, String platform, LocalDateTime scheduledAt) {
        VideoVariant v = variantRepo.findById(variantId).orElseThrow(
            () -> new IllegalArgumentException("Variant not found"));
        var video = videoRepo.findById(v.getVideo().getId()).orElseThrow();
        if (!video.getUserId().equals(userId)) {
            throw new IllegalArgumentException("You can only schedule your own videos");
        }
        PublishJob job = new PublishJob();
        job.setVariantId(variantId);
        job.setUserId(userId);
        job.setPlatform(platform);
        job.setStatus(JobStatus.SCHEDULED);
        job.setScheduledAt(scheduledAt);
        job = jobRepo.save(job);
        v.setApprovalStatus(ApprovalStatus.SCHEDULED);
        variantRepo.save(v);
        return job;
    }

    public List<PublishJob> getScheduledJobsForUser(Long userId) {
        return jobRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .filter(j -> j.getStatus() == JobStatus.SCHEDULED || j.getStatus() == JobStatus.PENDING)
            .toList();
    }
}
