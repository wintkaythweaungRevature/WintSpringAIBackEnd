package com.example.service;

import com.example.entity.Video;
import com.example.entity.VideoVariant;
import com.example.entity.Video.VideoStatus;
import com.example.entity.VideoVariant.ApprovalStatus;
import com.example.repository.VideoRepository;
import com.example.repository.VideoVariantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Full pipeline: Upload → Transcribe → Generate variants (10 platforms) → Store in DB.
 */
@Service
public class VideoPipelineService {

    private static final Logger log = LoggerFactory.getLogger(VideoPipelineService.class);

    private static final List<String> PLATFORMS = List.of(
        "YouTube", "YouTube Shorts", "Instagram Post", "Instagram Reel", "TikTok",
        "LinkedIn", "Facebook", "X (Twitter)", "Threads", "Pinterest"
    );

    private final S3StorageService s3Storage;
    private final VideoProcessingService videoProcessing;
    private final ContentGenerationService contentGeneration;
    private final VideoRepository videoRepo;
    private final VideoVariantRepository variantRepo;

    public VideoPipelineService(S3StorageService s3Storage, VideoProcessingService videoProcessing,
                                ContentGenerationService contentGeneration, VideoRepository videoRepo,
                                VideoVariantRepository variantRepo) {
        this.s3Storage = s3Storage;
        this.videoProcessing = videoProcessing;
        this.contentGeneration = contentGeneration;
        this.videoRepo = videoRepo;
        this.variantRepo = variantRepo;
    }

    @Transactional
    public Video processUpload(MultipartFile file, Long userId) throws IOException {
        Video video = new Video();
        video.setUserId(userId);
        video.setOriginalFilename(file.getOriginalFilename());
        video.setStatus(VideoStatus.PROCESSING);
        video = videoRepo.save(video);

        try {
            String s3Key = s3Storage.uploadVideo(file, userId);
            video.setS3Key(s3Key);
            video.setS3Bucket(s3Storage.getBucket());

            File tempFile = File.createTempFile("vid", getExt(file.getOriginalFilename()));
            file.transferTo(tempFile);

            String transcript = videoProcessing.transcribe(tempFile);
            video.setTranscript(transcript);
            video.setSrtContent(videoProcessing.generateSrt(transcript, 8));
            video.setDurationSeconds(videoProcessing.getDurationSeconds(tempFile));
            tempFile.delete();

            Map<String, String> allContent = contentGeneration.generateForAllPlatforms(transcript, file.getOriginalFilename());

            for (String platform : PLATFORMS) {
                VideoVariant v = new VideoVariant();
                v.setVideo(video);
                v.setPlatform(platform);
                v.setCaption(allContent.getOrDefault(platform + "_caption", ""));
                v.setHashtags(allContent.getOrDefault(platform + "_hashtags", ""));
                v.setDescription(allContent.getOrDefault(platform + "_description", ""));
                v.setApprovalStatus(ApprovalStatus.DRAFT);
                v.setWidth(getWidth(platform));
                v.setHeight(getHeight(platform));
                v.setDurationSeconds(getMaxDuration(platform));
                v.setS3Key(s3Key); // Use original for now; FFmpeg processing can set platform-specific keys later
                v = variantRepo.save(v);
                video.getVariants().add(v);
            }

            video.setStatus(VideoStatus.READY);
            return videoRepo.save(video);
        } catch (Exception e) {
            log.error("Pipeline failed", e);
            video.setStatus(VideoStatus.UPLOADED);
            videoRepo.save(video);
            throw new IOException("Processing failed: " + e.getMessage());
        }
    }

    private int getWidth(String platform) {
        return switch (platform.toLowerCase()) {
            case "youtube shorts", "instagram reel", "tiktok" -> 1080;
            case "instagram post", "pinterest" -> 1080;
            default -> 1920;
        };
    }

    private int getHeight(String platform) {
        return switch (platform.toLowerCase()) {
            case "youtube shorts", "instagram reel", "tiktok" -> 1920;
            case "instagram post" -> 1080;
            case "pinterest" -> 1500;
            default -> 1080;
        };
    }

    private Integer getMaxDuration(String platform) {
        return switch (platform.toLowerCase()) {
            case "tiktok" -> 60;
            case "instagram reel" -> 90;
            case "youtube shorts" -> 60;
            case "x (twitter)" -> 140;
            default -> null;
        };
    }

    private String getExt(String name) {
        if (name == null) return ".mp4";
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i) : ".mp4";
    }
}
