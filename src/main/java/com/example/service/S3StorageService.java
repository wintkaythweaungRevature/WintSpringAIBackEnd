package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;
    private final S3Presigner presigner;

    @Value("${aws.s3.bucket:springai-videos}")
    private String bucket;

    public S3StorageService(S3Client s3Client, S3Presigner presigner) {
        this.s3Client = s3Client;
        this.presigner = presigner;
    }

    public String uploadVideo(MultipartFile file, Long userId) throws IOException {
        String key = "videos/%d/%s/%s".formatted(userId, UUID.randomUUID(), sanitizeFilename(file.getOriginalFilename()));
        PutObjectRequest req = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(file.getContentType())
            .contentLength(file.getSize())
            .build();
        s3Client.putObject(req, RequestBody.fromBytes(file.getBytes()));
        log.info("Uploaded video to s3://{}/{}", bucket, key);
        return key;
    }

    public String uploadVariant(byte[] videoBytes, String contentType, Long userId, Long videoId, String platform) {
        String ext = getExtension(contentType);
        String key = "variants/%d/%d/%s%s".formatted(userId, videoId, platform.replaceAll("\\s+", "_"), ext);
        PutObjectRequest req = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .contentLength((long) videoBytes.length)
            .build();
        s3Client.putObject(req, RequestBody.fromBytes(videoBytes));
        return key;
    }

    public String getPresignedUrl(String key, Duration expiresIn) {
        GetObjectRequest getReq = GetObjectRequest.builder().bucket(bucket).key(key).build();
        var presignReq = GetObjectPresignRequest.builder()
            .signatureDuration(expiresIn)
            .getObjectRequest(getReq)
            .build();
        return presigner.presignGetObject(presignReq).url().toString();
    }

    public byte[] download(String key) throws IOException {
        try {
            var response = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
            return response.readAllBytes();
        } catch (S3Exception e) {
            throw new IOException("Failed to download from S3: " + e.getMessage());
        }
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "video.mp4";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String getExtension(String contentType) {
        if (contentType == null) return ".mp4";
        return switch (contentType) {
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            default -> ".mp4";
        };
    }

    public String getBucket() {
        return bucket;
    }
}
