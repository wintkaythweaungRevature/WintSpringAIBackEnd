package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "publish_job_id")
    private Long publishJobId;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "platform_post_id")
    private String platformPostId;

    @Column(name = "views")
    private Long views = 0L;

    @Column(name = "likes")
    private Long likes = 0L;

    @Column(name = "shares")
    private Long shares = 0L;

    @Column(name = "comments")
    private Long comments = 0L;

    @Column(name = "engagement_rate")
    private Double engagementRate;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
