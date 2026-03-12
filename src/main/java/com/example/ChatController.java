package com.example;

import com.example.entity.User;
import com.example.service.UserService;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;      // Added
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;
import java.io.ByteArrayOutputStream; // Added
import java.io.File;
import java.io.IOException;

// Apache POI & PDFBox
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.catalina.connector.Response;
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

    private ResponseEntity<?> requireEmailVerified(UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userService.findByEmail(userDetails.getUsername());
        if (!user.isEmailVerified()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Please verify your email to use Ask AI. Check your inbox."));
        }
        return null;
    }

    private ResponseEntity<?> requirePaidSubscription(UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userService.findByEmail(userDetails.getUsername());
        String plan = user.getMembershipType() != null ? user.getMembershipType() : "FREE";
        if ("FREE".equalsIgnoreCase(plan)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "This feature requires a member subscription ($5.99/month). Please upgrade to continue."));
        }
        return null;
    }

    @GetMapping("/test")
    public String test() {
        return "Backend is alive and CORS is configured!";
    }

    @GetMapping("/ask-ai")
    public ResponseEntity<?> askAi(@AuthenticationPrincipal UserDetails userDetails,
                                   @RequestParam(value = "prompt") String prompt) {
        ResponseEntity<?> err = requireEmailVerified(userDetails);
        if (err != null) return err;
        return ResponseEntity.ok(chatModel.call(prompt));
    }

    @GetMapping("/generate-image")
    public ResponseEntity<?> generateImage(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestParam(value = "prompt") String prompt) {
        ResponseEntity<?> err = requirePaidSubscription(userDetails);
        if (err != null) return err;
        ImageResponse response = imageModel.call(new ImagePrompt(prompt));
        String imageUrl = response.getResult().getOutput().getUrl();
        return ResponseEntity.ok(Collections.singletonMap("url", imageUrl));
    }

    @PostMapping("/analyze-pdf")
    public ResponseEntity<?> analyzePdf(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "Analyze this document and extract all important information.") String userPrompt
    ) throws IOException {
        ResponseEntity<?> err = requirePaidSubscription(userDetails);
        if (err != null) return err;

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
    @PostMapping("/transcribe")
    public ResponseEntity<?> transcribeAudio(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) throws IOException {
        ResponseEntity<?> err = requirePaidSubscription(userDetails);
        if (err != null) return err;
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

    @PostMapping("/reply")
    public ResponseEntity<?> generateReply(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestBody Map<String, String> payload) {
        ResponseEntity<?> err = requirePaidSubscription(userDetails);
        if (err != null) return err;
        String emailContent = payload.get("emailContent");
        String tone = payload.get("tone");
        return ResponseEntity.ok(emailGeneratorService.generateEmailReply(emailContent, tone));
    }
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
@ResponseBody
public org.springframework.core.io.Resource getSitemap() {
    return new org.springframework.core.io.ClassPathResource("static/sitemap.xml");
}
     
     
}