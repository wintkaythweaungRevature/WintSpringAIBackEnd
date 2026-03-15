package com.example.service;

import com.example.entity.PostMetrics;
import com.example.entity.PublishJob;
import com.example.repository.PostMetricsRepository;
import com.example.repository.PublishJobRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fetches metrics, computes engagement, and generates AI insights.
 */
@Service
public class AnalyticsService {

    private final PostMetricsRepository metricsRepo;
    private final PublishJobRepository jobRepo;
    private final ChatModel chatModel;

    public AnalyticsService(PostMetricsRepository metricsRepo, PublishJobRepository jobRepo,
                            ChatModel chatModel) {
        this.metricsRepo = metricsRepo;
        this.jobRepo = jobRepo;
        this.chatModel = chatModel;
    }

    public List<PostMetrics> getMetricsForUser(Long userId) {
        List<PublishJob> jobs = jobRepo.findByUserIdOrderByCreatedAtDesc(userId);
        return jobs.stream()
            .filter(j -> j.getStatus() == PublishJob.JobStatus.SUCCESS)
            .flatMap(j -> metricsRepo.findByPublishJobId(j.getId()).stream())
            .toList();
    }

    public Map<String, Object> getEngagementSummary(Long userId) {
        List<PostMetrics> metrics = getMetricsForUser(userId);
        long totalViews = metrics.stream().mapToLong(m -> m.getViews() != null ? m.getViews() : 0).sum();
        long totalLikes = metrics.stream().mapToLong(m -> m.getLikes() != null ? m.getLikes() : 0).sum();
        long totalShares = metrics.stream().mapToLong(m -> m.getShares() != null ? m.getShares() : 0).sum();
        long totalComments = metrics.stream().mapToLong(m -> m.getComments() != null ? m.getComments() : 0).sum();

        Map<String, Long> byPlatform = metrics.stream()
            .collect(Collectors.groupingBy(PostMetrics::getPlatform, Collectors.summingLong(m -> m.getViews() != null ? m.getViews() : 0)));

        double avgEngagement = totalViews > 0
            ? (double) (totalLikes + totalShares + totalComments) / totalViews * 100
            : 0;

        return Map.of(
            "totalViews", totalViews,
            "totalLikes", totalLikes,
            "totalShares", totalShares,
            "totalComments", totalComments,
            "avgEngagementRate", Math.round(avgEngagement * 100) / 100.0,
            "byPlatform", byPlatform,
            "postCount", metrics.size()
        );
    }

    public String generateAiInsights(Long userId) {
        Map<String, Object> summary = getEngagementSummary(userId);
        List<PostMetrics> metrics = getMetricsForUser(userId);

        String prompt = """
            You are a social media analytics expert. Based on this data, give 3-5 actionable recommendations.

            Summary: %s
            Sample posts (platform, views, likes, engagement): %s

            Format: Short bullet points. Include:
            - Best performing content format (short vs long, platform)
            - Best posting time suggestions
            - Trending topic recommendations
            - Next content ideas
            """.formatted(summary, metrics.stream().limit(10)
                .map(m -> "%s: %d views, %.2f%% engagement".formatted(
                    m.getPlatform(), m.getViews() != null ? m.getViews() : 0,
                    m.getEngagementRate() != null ? m.getEngagementRate() : 0))
                .collect(Collectors.joining("; ")));

        return chatModel.call(new Prompt(prompt)).getResult().getOutput().getContent();
    }

    public void recordMetrics(Long publishJobId, String platform, String platformPostId,
                              long views, long likes, long shares, long comments) {
        PostMetrics m = metricsRepo.findByPlatformPostId(platformPostId)
            .orElse(new PostMetrics());
        m.setPublishJobId(publishJobId);
        m.setPlatform(platform);
        m.setPlatformPostId(platformPostId);
        m.setViews(views);
        m.setLikes(likes);
        m.setShares(shares);
        m.setComments(comments);
        m.setEngagementRate(views > 0 ? (double) (likes + shares + comments) / views * 100 : 0);
        m.setFetchedAt(LocalDateTime.now());
        metricsRepo.save(m);
    }
}
