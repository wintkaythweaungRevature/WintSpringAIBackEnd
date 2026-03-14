package com.example;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;      // Added
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.multipart.MultipartFile;

import com.example.entity.User;
import com.example.service.UserService;

import java.util.Collections;
import java.util.Map;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@CrossOrigin(origins = {
    "http://localhost:3000",
    "http://localhost:3001",
    "https://wintkaythweaung.com",
    "https://www.wintkaythweaung.com",
    "https://api.wintaibot.com",
    "https://www.wintaibot.com",
    "https://wintaibot.com",
    "https://main.dk6jk3fcod2l.amplifyapp.com",
    "https://springai.pages.dev"
}, allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
@RequestMapping("/api/ai")
@RestController
public class ChatController {

    private final OpenAiChatModel chatModel;
    private final ImageModel imageModel;
    private final OpenAiAudioTranscriptionModel transcriptionModel;
    private final EmailGeneratorService emailGeneratorService;
    private final UserService userService;

    public ChatController(OpenAiChatModel chatModel, ImageModel imageModel,
        OpenAiAudioTranscriptionModel transcriptionModel, EmailGeneratorService emailGeneratorService,
        UserService userService) {
        this.chatModel = chatModel;
        this.imageModel = imageModel;
        this.transcriptionModel = transcriptionModel;
        this.emailGeneratorService = emailGeneratorService;
        this.userService = userService;
    }

    private ResponseEntity<?> requirePaidSubscription(UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            if (!userService.hasActivePaidAccess(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "requiresSubscription", true,
                            "message", "To use this feature, please subscribe to a plan. Visit the subscription page to upgrade and unlock all features."));
            }
            return null;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Subscription check failed: " + e.getMessage()));
        }
    }

    @GetMapping("/test")
    public String test() {
        return "Backend is alive and CORS is configured!";
    }

    @GetMapping(value = "/ask-ai", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> askAi(@AuthenticationPrincipal UserDetails userDetails,
                                        @RequestParam(value = "prompt") String prompt) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }
        return ResponseEntity.ok(chatModel.call(prompt));
    }

    @GetMapping("/generate-image")
    public ResponseEntity<?> generateImage(@AuthenticationPrincipal UserDetails userDetails,
                                            @RequestParam(value = "prompt") String prompt) {
        ResponseEntity<?> denied = requirePaidSubscription(userDetails);
        if (denied != null) return denied;
        ImageResponse response = imageModel.call(new ImagePrompt(prompt));
        String imageUrl = response.getResult().getOutput().getUrl();
        return ResponseEntity.ok(Collections.singletonMap("url", imageUrl));
    }

    // ✅ NEW endpoint - AI analyzes PDF and returns structured JSON
    @PostMapping("/analyze-pdf")
    public ResponseEntity<?> analyzePdf(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "Analyze this document and extract all important information.") String userPrompt
    ) throws IOException {
        ResponseEntity<?> denied = requirePaidSubscription(userDetails);
        if (denied != null) return denied;

        // Step 1: Extract text from PDF using PDFBox
        String pdfText = readPdf(file);

        // Step 2: Send extracted text to AI with structured prompt
        String aiPrompt = """
                You are a data extraction assistant. Analyze the following PDF content and return ONLY a JSON object with this exact structure, no extra text:
                {
                  "summary": "2-3 sentence summary of the document",
                  "table_headers": ["Column1", "Column2", "Column3"],
                  "table_rows": [["value1", "value2", "value3"]],
                  "insights": ["Key insight 1", "Key insight 2"]
                }

                Rules:
                - Extract ALL meaningful structured data into table_headers and table_rows
                - If no clear table exists, use ["Field", "Value"] as headers and key info as rows
                - All values must be strings
                - Return ONLY the JSON, nothing else

                User focus: %s

                PDF Content:
                %s
                """.formatted(userPrompt, pdfText);

        String aiResponse = chatModel.call(aiPrompt);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(aiResponse);
    }

    // ✅ Shared helper method used by both endpoints
    private String readPdf(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
    // Audio transcription endpoint
    @PostMapping("/transcribe")
    public ResponseEntity<?> transcribeAudio(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) throws IOException {
        ResponseEntity<?> denied = requirePaidSubscription(userDetails);
        if (denied != null) return denied;
        File tempfile = File.createTempFile("audio", "wav");
              file.transferTo(tempfile);
              OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
              .withResponseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
              .withLanguage("en")
              .withTemperature(0f)
              .build();
              var audiofile = new FileSystemResource(tempfile);
              AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audiofile,transcriptionOptions);
              AudioTranscriptionResponse response = transcriptionModel.call(transcriptionRequest);
              tempfile.delete();

              return new ResponseEntity<>(response.getResult().getOutput(), HttpStatus.OK);
        }

    /**
     * Interview Prep Kit: resume + job description → match score, gaps analysis,
     * 30 interview questions (10 Technical, 10 Behavioral, 10 Role-specific), 20 flashcards.
     * Requires MEMBER subscription. Expects multipart: file (PDF), jd (string).
     */
    @PostMapping(value = "/prepare-interview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> prepareInterview(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam("jd") String jobDescription) throws IOException {
        // Check member subscription
        ResponseEntity<?> denied = requirePaidSubscription(userDetails);
        if (denied != null) return denied;
        if (file.isEmpty() || jobDescription == null || jobDescription.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "PDF file and job description (jd) are required"));
        }
        String resumeText = readPdf(file);
        if (resumeText == null || resumeText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not extract text from the uploaded PDF"));
        }

        String aiPrompt = """
            You are an expert Technical Recruiter. Analyze the JD and Resume provided.

            JD: %s

            Resume: %s

            TASK:
            1. Compare keywords and experience to give a MATCH_PERCENTAGE (0-100).
            2. Generate exactly 30 Interview Questions (10 Technical, 10 Behavioral, 10 Role-specific).
            3. Generate exactly 20 Flashcards for key technical terms and concepts.

            RETURN ONLY VALID JSON:
            {
              "match_percentage": 85,
              "analysis": "10-line summary of keyword comparison and gaps.",
              "questions": [{"q": "...", "type": "Technical|Behavioral|Role-specific", "guidance": "...", "tips": "..."}],
              "flashcards": [{"front": "...", "back": "..."}]
            }
            """.formatted(jobDescription, resumeText);

        try {
            String aiResponse = chatModel.call(aiPrompt);

            // Extract JSON object robustly: find first '{' and last '}'
            int start = aiResponse.indexOf('{');
            int end = aiResponse.lastIndexOf('}');
            if (start == -1 || end == -1 || end <= start) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "AI did not return a JSON object. Please try again."));
            }
            String cleanedJson = aiResponse.substring(start, end + 1);

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> result = mapper.readValue(cleanedJson, Map.class);
            return ResponseEntity.ok().body(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "AI response was not valid JSON. Please try again."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "AI service error: " + e.getMessage()));
        }
    }

    @PostMapping("/reply")
    public ResponseEntity<?> generateReply(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestBody Map<String, String> payload) {
        ResponseEntity<?> denied = requirePaidSubscription(userDetails);
        if (denied != null) return denied;
        String emailContent = payload.get("emailContent");
        String tone = payload.get("tone");
        return ResponseEntity.ok(emailGeneratorService.generateEmailReply(emailContent, tone));
    }
}