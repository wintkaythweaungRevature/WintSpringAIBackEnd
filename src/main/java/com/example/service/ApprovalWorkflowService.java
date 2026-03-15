package com.example.service;

import com.example.entity.PublishJob;
import com.example.entity.User;
import com.example.entity.VideoVariant;
import com.example.entity.VideoVariant.ApprovalStatus;
import com.example.entity.PublishJob.JobStatus;
import com.example.repository.PublishJobRepository;
import com.example.repository.VideoVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Role-based approval: Creator submits, Manager approves, Buffer schedules.
 * Roles: ROLE_CREATOR, ROLE_MANAGER, ROLE_BUFFER (or ADMIN can do all)
 */
@Service
public class ApprovalWorkflowService {

    private final VideoVariantRepository variantRepo;
    private final PublishJobRepository jobRepo;

    public ApprovalWorkflowService(VideoVariantRepository variantRepo, PublishJobRepository jobRepo) {
        this.variantRepo = variantRepo;
        this.jobRepo = jobRepo;
    }

    public boolean canSubmit(User user) {
        return hasRole(user, "CREATOR") || hasRole(user, "ADMIN");
    }

    public boolean canApprove(User user) {
        return hasRole(user, "MANAGER") || hasRole(user, "ADMIN");
    }

    public boolean canSchedule(User user) {
        return hasRole(user, "BUFFER") || hasRole(user, "ADMIN");
    }

    private boolean hasRole(User user, String role) {
        if (user == null || user.getRole() == null) return false;
        return user.getRole().toUpperCase().contains(role);
    }

    @Transactional
    public VideoVariant submitForReview(Long variantId, User submitter) {
        VideoVariant v = variantRepo.findById(variantId).orElseThrow();
        if (!canSubmit(submitter)) throw new IllegalArgumentException("Not allowed to submit");
        v.setApprovalStatus(ApprovalStatus.PENDING_REVIEW);
        return variantRepo.save(v);
    }

    @Transactional
    public VideoVariant approve(Long variantId, User approver) {
        VideoVariant v = variantRepo.findById(variantId).orElseThrow();
        if (!canApprove(approver)) throw new IllegalArgumentException("Not allowed to approve");
        v.setApprovalStatus(ApprovalStatus.APPROVED);
        v.setApprovedBy(approver.getId());
        v.setApprovedAt(LocalDateTime.now());
        return variantRepo.save(v);
    }

    @Transactional
    public VideoVariant reject(Long variantId, User approver) {
        VideoVariant v = variantRepo.findById(variantId).orElseThrow();
        if (!canApprove(approver)) throw new IllegalArgumentException("Not allowed to reject");
        v.setApprovalStatus(ApprovalStatus.REJECTED);
        return variantRepo.save(v);
    }

    @Transactional
    public PublishJob schedulePublish(Long variantId, Long userId, String platform, LocalDateTime scheduledAt) {
        VideoVariant v = variantRepo.findById(variantId).orElseThrow();
        if (v.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalArgumentException("Variant must be approved first");
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

    public List<VideoVariant> getPendingReview() {
        return variantRepo.findAll().stream()
            .filter(v -> v.getApprovalStatus() == ApprovalStatus.PENDING_REVIEW)
            .toList();
    }

    public List<PublishJob> getScheduledJobs() {
        List<PublishJob> scheduled = jobRepo.findByStatus(JobStatus.SCHEDULED);
        List<PublishJob> pending = jobRepo.findByStatus(JobStatus.PENDING);
        return java.util.stream.Stream.concat(scheduled.stream(), pending.stream()).toList();
    }
}
