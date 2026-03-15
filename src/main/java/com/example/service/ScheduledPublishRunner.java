package com.example.service;

import com.example.entity.PublishJob;
import com.example.entity.VideoVariant;
import com.example.repository.PublishJobRepository;
import com.example.repository.VideoVariantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cron job: every minute, publish scheduled jobs that are due.
 */
@Service
public class ScheduledPublishRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublishRunner.class);

    private final PublishJobRepository jobRepo;
    private final VideoVariantRepository variantRepo;
    private final VideoPublishService publishService;
    private final SocialAuthService socialAuthService;
    private final S3StorageService s3Storage;
    private final UserService userService;

    public ScheduledPublishRunner(PublishJobRepository jobRepo, VideoVariantRepository variantRepo,
                                  UserService userService, VideoPublishService publishService,
                                  SocialAuthService socialAuthService, S3StorageService s3Storage) {
        this.jobRepo = jobRepo;
        this.variantRepo = variantRepo;
        this.userService = userService;
        this.publishService = publishService;
        this.socialAuthService = socialAuthService;
        this.s3Storage = s3Storage;
    }

    @Scheduled(cron = "0 * * * * *") // every minute
    @Transactional
    public void runScheduledPublishes() {
        List<PublishJob> due = jobRepo.findByStatusAndScheduledAtBefore(
            PublishJob.JobStatus.SCHEDULED, LocalDateTime.now());
        for (PublishJob job : due) {
            try {
                job.setStatus(PublishJob.JobStatus.PUBLISHING);
                jobRepo.save(job);

                VideoVariant variant = variantRepo.findById(job.getVariantId()).orElseThrow();
                String platform = mapPlatform(variant.getPlatform());
                String userEmail = userService.findById(job.getUserId()).getEmail();
                String token = socialAuthService.getAccessToken(userEmail, platform);

                byte[] videoBytes = variant.getS3Key() != null
                    ? s3Storage.download(variant.getS3Key())
                    : null;
                if (videoBytes == null) {
                    throw new IOException("No video file for variant " + variant.getId());
                }
                MultipartFile file = new ByteArrayMultipartFile(videoBytes, "video.mp4", "video/mp4");
                String postId = publishService.publish(platform, token, file,
                    variant.getCaption(), variant.getHashtags());

                job.setStatus(PublishJob.JobStatus.SUCCESS);
                job.setPublishedAt(LocalDateTime.now());
                job.setPlatformPostId(postId);
                jobRepo.save(job);

                variant.setApprovalStatus(VideoVariant.ApprovalStatus.PUBLISHED);
                variantRepo.save(variant);
            } catch (Exception e) {
                log.error("Scheduled publish failed for job {}", job.getId(), e);
                job.setStatus(PublishJob.JobStatus.FAILED);
                job.setErrorMessage(e.getMessage());
                jobRepo.save(job);
            }
        }
    }

    private String mapPlatform(String platform) {
        return switch (platform.toLowerCase()) {
            case "youtube shorts" -> "youtube";
            case "instagram post", "instagram reel" -> "instagram";
            case "x (twitter)" -> "x";
            default -> platform.split("\\s+")[0].toLowerCase();
        };
    }

    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String name;
        private final String contentType;

        ByteArrayMultipartFile(byte[] content, String name, String contentType) {
            this.content = content;
            this.name = name;
            this.contentType = contentType;
        }

        @Override
        public String getName() { return name; }
        @Override
        public String getOriginalFilename() { return name; }
        @Override
        public String getContentType() { return contentType; }
        @Override
        public boolean isEmpty() { return content == null || content.length == 0; }
        @Override
        public long getSize() { return content.length; }
        @Override
        public byte[] getBytes() { return content; }
        @Override
        public java.io.InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
