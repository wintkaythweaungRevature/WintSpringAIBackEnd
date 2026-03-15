package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_variants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

    @Column(name = "hashtags", columnDefinition = "TEXT")
    private String hashtags;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "s3_key")
    private String s3Key;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    private ApprovalStatus approvalStatus = ApprovalStatus.DRAFT;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ApprovalStatus {
        DRAFT, PENDING_REVIEW, APPROVED, REJECTED, SCHEDULED, PUBLISHED
    }
}
