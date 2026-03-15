package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Handles: (1) Whisper transcription, (2) SRT generation, (3) FFmpeg resize/clip per platform.
 */
@Service
public class VideoProcessingService {

    private static final Logger log = LoggerFactory.getLogger(VideoProcessingService.class);

    private final OpenAiAudioTranscriptionModel transcriptionModel;

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    public VideoProcessingService(OpenAiAudioTranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    public String transcribe(File videoFile) {
        OpenAiAudioTranscriptionOptions opts = OpenAiAudioTranscriptionOptions.builder()
            .withResponseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
            .withLanguage("en")
            .withTemperature(0f)
            .build();
        AudioTranscriptionResponse resp = transcriptionModel.call(
            new AudioTranscriptionPrompt(new FileSystemResource(videoFile), opts));
        return resp.getResult().getOutput();
    }

    public String generateSrt(String transcript, int wordsPerSegment) {
        String[] words = transcript.split("\\s+");
        StringBuilder srt = new StringBuilder();
        int segment = 1;
        for (int i = 0; i < words.length; i += wordsPerSegment) {
            int end = Math.min(i + wordsPerSegment, words.length);
            String text = String.join(" ", java.util.Arrays.copyOfRange(words, i, end));
            int startSec = (i * 2) / wordsPerSegment;
            int endSec = (end * 2) / wordsPerSegment;
            srt.append(segment++).append("\n");
            srt.append(formatSrtTime(startSec)).append(" --> ").append(formatSrtTime(endSec)).append("\n");
            srt.append(text).append("\n\n");
        }
        return srt.toString();
    }

    private String formatSrtTime(int seconds) {
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return "%02d:%02d:%02d,000".formatted(h, m, s);
    }

    /**
     * Resize/clip video per platform specs.
     * Returns path to output file.
     */
    public File processForPlatform(File inputFile, String platform, Integer maxDurationSec) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("video-process");
        String ext = getExtension(inputFile.getName());
        File output = tempDir.resolve(platform + ext).toFile();

        int width, height;
        switch (platform.toLowerCase()) {
            case "youtube", "facebook", "linkedin", "x" -> { width = 1920; height = 1080; }
            case "youtube_shorts", "instagram_reel", "tiktok" -> { width = 1080; height = 1920; }
            case "instagram_post" -> { width = 1080; height = 1080; }
            case "pinterest" -> { width = 1000; height = 1500; }
            default -> { width = 1920; height = 1080; }
        }

        int duration = maxDurationSec != null ? maxDurationSec : 9999;
        String filter = "scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2".formatted(width, height, width, height);
        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath, "-y", "-i", inputFile.getAbsolutePath(),
            "-t", String.valueOf(duration),
            "-vf", filter,
            "-c:v", "libx264", "-preset", "fast", "-crf", "23",
            "-c:a", "aac", "-b:a", "128k",
            output.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean ok = p.waitFor(5, TimeUnit.MINUTES);
        if (!ok || !output.exists()) {
            throw new IOException("FFmpeg failed for platform " + platform);
        }
        return output;
    }

    public int getDurationSeconds(File videoFile) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath, "-i", videoFile.getAbsolutePath(), "-f", "null", "-"
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String err = new String(p.getInputStream().readAllBytes());
        p.waitFor(30, TimeUnit.SECONDS);
        // Parse "Duration: 00:01:23.45" from stderr
        int idx = err.indexOf("Duration:");
        if (idx < 0) return 0;
        String dur = err.substring(idx + 10, idx + 21);
        String[] parts = dur.split(":");
        if (parts.length < 3) return 0;
        int h = Integer.parseInt(parts[0].trim());
        int m = Integer.parseInt(parts[1].trim());
        double s = Double.parseDouble(parts[2].trim().replace(",", "."));
        return (int) (h * 3600 + m * 60 + s);
    }

    private String getExtension(String name) {
        if (name == null) return ".mp4";
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i) : ".mp4";
    }
}
