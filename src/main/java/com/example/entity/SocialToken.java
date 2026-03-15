package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "social_tokens", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "platform"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocialToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String platform;

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "platform_user_id")
    private String platformUserId;

    @Column(name = "platform_username")
    private String platformUsername;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @PrePersist
    public void prePersist() {
        this.connectedAt = LocalDateTime.now();
    }
}
