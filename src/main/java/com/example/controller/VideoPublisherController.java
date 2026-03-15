package com.example.Controller;

import com.example.service.SocialAuthService;
import com.example.service.VideoPublishService;
import com.example.service.UserService;
import com.example.entity.User;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;

@CrossOrigin(origins = {
    "http://localhost:3000",
    "https://wintaibot.com",
    "https://www.wintaibot.com",
    "https://api.wintaibot.com"
}, allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
@RestController
@RequestMapping("/api/video")
public class VideoPublisherController {

    private final OpenAiChatModel chatModel;
    private final OpenAiAudioTranscriptionModel transcriptionModel;
    private final SocialAuthService socialAuthService;
    private final VideoPublishService videoPublishService;
    private final UserService userService;

    public VideoPublisherController(OpenAiChatModel chatModel,
                                    OpenAiAudioTranscriptionModel transcriptionModel,
                                    SocialAuthService socialAuthService,
                                    VideoPublishService videoPublishService,
                                    UserService userService) {
        this.chatModel = chatModel;
        this.transcriptionModel = transcriptionModel;
        this.socialAuthService = socialAuthService;
        this.videoPublishService = videoPublishService;
        this.userService = userService;
    }

    /**
     * Step 1: Process video — transcribe audio, generate captions + hashtags per platform.
     * POST /api/video/process
     * multipart: file (video), platforms (comma-separated)
     */
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> processVideo(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam("platforms") String platformsCsv) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }

        User user = userService.findByEmail(userDetails.getUsername());
        if (!userService.hasActivePaidAccess(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("requiresSubscription", true, "message", "Member subscription required"));
        }

        try {
            // 1. Transcribe audio from video using Whisper
            File tempFile = File.createTempFile("video", getExtension(file.getOriginalFilename()));
            file.transferTo(tempFile);

            OpenAiAudioTranscriptionOptions opts = OpenAiAudioTranscriptionOptions.builder()
                .withResponseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                .withLanguage("en")
                .withTemperature(0f)
                .build();

            AudioTranscriptionResponse transcriptionResponse =
                transcriptionModel.call(new AudioTranscriptionPrompt(new FileSystemResource(tempFile), opts));
            String transcript = transcriptionResponse.getResult().getOutput();
            tempFile.delete();

            // 2. Generate platform-specific captions + hashtags using GPT
            List<String> platforms = Arrays.asList(platformsCsv.split(","));
            Map<String, Object> variants = generateVariants(transcript, platforms, file.getOriginalFilename());

            return ResponseEntity.ok(Map.of(
                "transcript", transcript,
                "variants", variants
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Processing failed: " + e.getMessage()));
        }
    }

    /**
     * Step 2: Publish an approved variant to a specific platform.
     * POST /api/video/publish/{platform}
     * multipart: file (video), caption, hashtags
     */
    @PostMapping(value = "/publish/{platform}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> publishVideo(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String platform,
            @RequestParam("file") MultipartFile file,
            @RequestParam("caption") String caption,
            @RequestParam(value = "hashtags", defaultValue = "") String hashtags) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }

        try {
            String accessToken = socialAuthService.getAccessToken(userDetails.getUsername(), platform);
            String postId = videoPublishService.publish(platform, accessToken, file, caption, hashtags);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "platform", platform,
                "postId", postId,
                "message", "Successfully published to " + platform
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Publish failed for " + platform + ": " + e.getMessage()));
        }
    }

    /* ── Generate captions + hashtags via GPT ──────────────────── */
    private Map<String, Object> generateVariants(String transcript, List<String> platforms, String filename) {
        Map<String, Object> variants = new LinkedHashMap<>();

        for (String platform : platforms) {
            String prompt = buildCaptionPrompt(platform.trim(), transcript, filename);
            try {
                String aiResponse = chatModel.call(prompt);
                // Parse GPT response: expect JSON {caption:"...", hashtags:"..."}
                String caption = extractField(aiResponse, "caption");
                String hashtags = extractField(aiResponse, "hashtags");
                variants.put(platform.trim(), Map.of(
                    "caption", caption,
                    "hashtags", hashtags,
                    "clipNote", getClipNote(platform.trim()),
                    "status", "draft"
                ));
            } catch (Exception e) {
                variants.put(platform.trim(), Map.of(
                    "caption", "Check out my latest video! " + filename,
                    "hashtags", "#video #content #" + platform.trim(),
                    "clipNote", getClipNote(platform.trim()),
                    "status", "draft"
                ));
            }
        }
        return variants;
    }

    private String buildCaptionPrompt(String platform, String transcript, String filename) {
        return """
            You are a social media expert. Based on this video transcript, write an optimized post for %s.

            Transcript: %s

            Return ONLY valid JSON (no markdown):
            {"caption": "platform-specific caption here", "hashtags": "#tag1 #tag2 #tag3 #tag4 #tag5"}

            Rules for %s:
            - YouTube: engaging description up to 500 chars with emojis and CTA
            - Instagram: casual, emoji-rich, up to 300 chars
            - TikTok: trendy, short, include fyp hashtag
            - LinkedIn: professional tone, insight-driven, up to 400 chars
            - Facebook: friendly and engaging, up to 300 chars
            - X: punchy, under 250 chars
            - Threads: conversational, under 300 chars
            - Pinterest: descriptive, inspiring
            """.formatted(platform, transcript.substring(0, Math.min(transcript.length(), 800)), platform);
    }

    private String extractField(String json, String field) {
        try {
            int start = json.indexOf("\"" + field + "\"") + field.length() + 4;
            char quote = json.charAt(start);
            int end = json.indexOf(quote, start + 1);
            return json.substring(start + 1, end);
        } catch (Exception e) {
            return "";
        }
    }

    private String getClipNote(String platform) {
        return switch (platform.toLowerCase()) {
            case "youtube"   -> "Full horizontal video · 16:9";
            case "instagram" -> "60s Reel · 9:16 vertical";
            case "tiktok"    -> "15–60s · 9:16 vertical";
            case "linkedin"  -> "Up to 10 min · professional tone";
            case "facebook"  -> "Full video · 16:9";
            case "x"         -> "Up to 2:20 · punchy caption";
            case "threads"   -> "Text + thumbnail";
            case "pinterest" -> "Thumbnail · 2:3 ratio";
            default          -> "";
        };
    }

    private String getExtension(String filename) {
        if (filename == null) return ".mp4";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".mp4";
    }
}
