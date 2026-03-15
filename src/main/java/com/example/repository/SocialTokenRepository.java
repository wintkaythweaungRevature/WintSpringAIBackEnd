package com.example.repository;

import com.example.entity.SocialToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialTokenRepository extends JpaRepository<SocialToken, Long> {
    Optional<SocialToken> findByUserIdAndPlatform(Long userId, String platform);
    List<SocialToken> findByUserId(Long userId);
    void deleteByUserIdAndPlatform(Long userId, String platform);
}
