package com.example.Controller;

import com.example.entity.PublishJob;
import com.example.entity.Video;
import com.example.entity.VideoVariant;
import com.example.service.*;
import com.example.service.ApprovalWorkflowService;
import com.example.service.AnalyticsService;
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
    private final ApprovalWorkflowService approvalService;
    private final AnalyticsService analyticsService;
    private final VideoPublishService publishService;
    private final SocialAuthService socialAuthService;
    private final com.example.repository.VideoRepository videoRepo;
    private final com.example.repository.VideoVariantRepository variantRepo;
    private final com.example.repository.PublishJobRepository jobRepo;

    public VideoContentController(VideoPipelineService pipelineService, UserService userService,
                                  ApprovalWorkflowService approvalService, AnalyticsService analyticsService,
                                  VideoPublishService publishService, SocialAuthService socialAuthService,
                                  com.example.repository.VideoRepository videoRepo,
                                  com.example.repository.VideoVariantRepository variantRepo,
                                  com.example.repository.PublishJobRepository jobRepo) {
        this.pipelineService = pipelineService;
        this.userService = userService;
        this.approvalService = approvalService;
        this.analyticsService = analyticsService;
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
                    "approvalStatus", v.getApprovalStatus().name()
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
                "approvalStatus", v.getApprovalStatus().name()
            )).toList()
        ));
    }

    /**
     * [3] Approval workflow: submit for review
     */
    @PostMapping("/variants/{id}/submit")
    public ResponseEntity<?> submitForReview(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userService.findByEmail(userDetails.getUsername());
        try {
            VideoVariant v = approvalService.submitForReview(id, user);
            return ResponseEntity.ok(Map.of("status", v.getApprovalStatus().name()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * [3] Approve variant (Manager)
     */
    @PostMapping("/variants/{id}/approve")
    public ResponseEntity<?> approve(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userService.findByEmail(userDetails.getUsername());
        try {
            VideoVariant v = approvalService.approve(id, user);
            return ResponseEntity.ok(Map.of("status", v.getApprovalStatus().name()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * [3] Reject variant
     */
    @PostMapping("/variants/{id}/reject")
    public ResponseEntity<?> reject(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userService.findByEmail(userDetails.getUsername());
        try {
            VideoVariant v = approvalService.reject(id, user);
            return ResponseEntity.ok(Map.of("status", v.getApprovalStatus().name()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * [4] Schedule publish (Buffer)
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
            PublishJob job = approvalService.schedulePublish(id, user.getId(), platform, scheduledAt);
            return ResponseEntity.ok(Map.of("jobId", job.getId(), "scheduledAt", job.getScheduledAt()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * [5] Publish immediately (uses existing VideoPublishService)
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
     * [6] Analytics & AI insights
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
