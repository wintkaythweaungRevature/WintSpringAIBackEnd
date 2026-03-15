package com.example.Controller;

import com.example.entity.PublishJob;
import com.example.entity.Video;
import com.example.entity.VideoVariant;
import com.example.service.*;
import com.example.service.AnalyticsService;
import com.example.service.ScheduleService;
import com.example.service.TrendingService;
import com.example.service.UserService;
import com.example.service.VideoPipelineService;
import com.example.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/video-content")
public class VideoContentController {

    private final VideoPipelineService pipelineService;
    private final UserService userService;
    private final ScheduleService scheduleService;
    private final AnalyticsService analyticsService;
    private final TrendingService trendingService;
    private final VideoPublishService publishService;
    private final SocialAuthService socialAuthService;
    private final com.example.repository.VideoRepository videoRepo;
    private final com.example.repository.VideoVariantRepository variantRepo;
    private final com.example.repository.PublishJobRepository jobRepo;

    public VideoContentController(VideoPipelineService pipelineService, UserService userService,
                                  ScheduleService scheduleService, AnalyticsService analyticsService,
                                  TrendingService trendingService, VideoPublishService publishService,
                                  SocialAuthService socialAuthService,
                                  com.example.repository.VideoRepository videoRepo,
                                  com.example.repository.VideoVariantRepository variantRepo,
                                  com.example.repository.PublishJobRepository jobRepo) {
        this.pipelineService = pipelineService;
        this.userService = userService;
        this.scheduleService = scheduleService;
        this.analyticsService = analyticsService;
        this.trendingService = trendingService;
        this.publishService = publishService;
        this.socialAuthService = socialAuthService;
        this.videoRepo = videoRepo;
        this.variantRepo = variantRepo;
        this.jobRepo = jobRepo;
    }

    /**
     * [1] Upload video → full AI pipeline (transcribe, 10 variants, S3)
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadVideo(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userService.findByEmail(userDetails.getUsername());
        if (!userService.hasActivePaidAccess(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("requiresSubscription", true, "message", "Member subscription required"));
        }
        try {
            Video video = pipelineService.processUpload(file, user.getId());
            return ResponseEntity.ok(Map.of(
                "videoId", video.getId(),
                "status", video.getStatus().name(),
                "transcript", video.getTranscript(),
                "srt", video.getSrtContent(),
                "variants", video.getVariants().stream().map(v -> Map.of(
                    "id", v.getId(),
                    "platform", v.getPlatform(),
                    "caption", v.getCaption(),
                    "hashtags", v.getHashtags(),
                    "status", v.getApprovalStatus().name()
                )).toList()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    /**
     * List user's videos
     */
    @GetMapping("/videos")
    public ResponseEntity<?> listVideos(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userService.findByEmail(userDetails.getUsername());
        List<Video> videos = videoRepo.findByUserIdOrderByCreatedAtDesc(user.getId());
        return ResponseEntity.ok(videos.stream().map(v -> Map.of(
            "id", v.getId(),
            "originalFilename", v.getOriginalFilename(),
            "status", v.getStatus().name(),
            "durationSeconds", v.getDurationSeconds() != null ? v.getDurationSeconds() : 0,
            "createdAt", v.getCreatedAt().toString(),
            "variantCount", v.getVariants().size()
        )).toList());
    }

    /**
     * Get video with variants
     */
    @GetMapping("/videos/{id}")
    public ResponseEntity<?> getVideo(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Video video = videoRepo.findById(id).orElse(null);
        if (video == null) return ResponseEntity.notFound().build();
        User user = userService.findByEmail(userDetails.getUsername());
        if (!video.getUserId().equals(user.getId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(Map.of(
            "id", video.getId(),
            "transcript", video.getTranscript(),
            "srt", video.getSrtContent() != null ? video.getSrtContent() : "",
            "variants", video.getVariants().stream().map(v -> Map.of(
                "id", v.getId(),
                "platform", v.getPlatform(),
                "caption", v.getCaption(),
                "hashtags", v.getHashtags(),
                "status", v.getApprovalStatus().name()
            )).toList()
        ));
    }

    /**
     * Schedule publish: user picks platform + datetime for each variant.
     * Example: Video A → YouTube tomorrow 4am, Instagram tomorrow 6pm
     * Body: { "platform": "youtube", "scheduledAt": "2026-03-16T04:00:00" }
     */
    @PostMapping("/variants/{id}/schedule")
    public ResponseEntity<?> schedulePublish(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userService.findByEmail(userDetails.getUsername());
        String platform = (String) body.get("platform");
        String scheduledAtStr = (String) body.get("scheduledAt");
        if (platform == null) return ResponseEntity.badRequest().body(Map.of("error", "platform required"));
        LocalDateTime scheduledAt = scheduledAtStr != null ? LocalDateTime.parse(scheduledAtStr) : LocalDateTime.now();
        try {
            PublishJob job = scheduleService.schedulePublish(id, user.getId(), platform, scheduledAt);
            return ResponseEntity.ok(Map.of(
                "jobId", job.getId(),
                "scheduledAt", job.getScheduledAt(),
                "platform", platform,
                "message", "Scheduled! Will publish at " + scheduledAt
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get user's scheduled jobs
     */
    @GetMapping("/schedule")
    public ResponseEntity<?> getScheduledJobs(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userService.findByEmail(userDetails.getUsername());
        var jobs = scheduleService.getScheduledJobsForUser(user.getId());
        return ResponseEntity.ok(jobs.stream().map(j -> {
            var v = variantRepo.findById(j.getVariantId()).orElse(null);
            return Map.of(
                "jobId", j.getId(),
                "variantId", j.getVariantId(),
                "platform", j.getPlatform(),
                "scheduledAt", j.getScheduledAt().toString(),
                "videoId", v != null ? v.getVideo().getId() : null
            );
        }).toList());
    }

    /**
     * Publish immediately
     */
    @PostMapping(value = "/publish/{platform}", consumes = "multipart/form-data")
    public ResponseEntity<?> publishNow(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String platform,
            @RequestParam("file") MultipartFile file,
            @RequestParam("caption") String caption,
            @RequestParam(value = "hashtags", defaultValue = "") String hashtags) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            String token = socialAuthService.getAccessToken(userDetails.getUsername(), platform);
            String postId = publishService.publish(platform, token, file, caption, hashtags);
            return ResponseEntity.ok(Map.of("success", true, "platform", platform, "postId", postId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Publish failed: " + e.getMessage()));
        }
    }

    /**
     * Viral trends, news for Upload step card. Frontend calls this.
     * Returns { trends: [...], news: [...] } — 4-5 trends, 2-3 news items.
     */
    @GetMapping("/trends")
    public ResponseEntity<?> getTrends(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(trendingService.getTrendsForUploadStep());
    }

    /**
     * Legacy: viral trends, news, content ideas (full format)
     */
    @GetMapping("/trending")
    public ResponseEntity<?> getTrending(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(trendingService.getTrendingInfo());
    }

    /**
     * Analytics & AI insights
     */
    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userService.findByEmail(userDetails.getUsername());
        return ResponseEntity.ok(analyticsService.getEngagementSummary(user.getId()));
    }

    @GetMapping("/analytics/insights")
    public ResponseEntity<?> getAiInsights(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userService.findByEmail(userDetails.getUsername());
        String insights = analyticsService.generateAiInsights(user.getId());
        return ResponseEntity.ok(Map.of("insights", insights));
    }
}
