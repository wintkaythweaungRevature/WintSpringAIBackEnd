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
import org.springframework.http.ResponseEntity; // Added
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;      // Added
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;d
import java.io.File;
import java.io.IOException;

// Apache POI & PDFBox
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.catalina.connector.Response;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@CrossOrigin(origins = {
    "http://localhost:3000",    "http://localhost:3001",
    "https://wintkaythweaung.com",
    "https://www.wintkaythweaung.com",
    "https://api.wintaibot.com",
    "https://www.wintaibot.com",
    "https://wintaibot.com",
    "https://main.dk6jk3fcod2l.amplifyapp.com",
    "https://springai.pages.dev"
}, allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
@RequestMapping
@RestController
public class ChatController {

    private final OpenAiChatModel chatModel;
    private final ImageModel imageModel;
    private final OpenAiAudioTranscriptionModel transcriptionModel;
    private final EmailGeneratorService emailGeneratorService;

    public ChatController(OpenAiChatModel chatModel, ImageModel imageModel ,
        OpenAiAudioTranscriptionModel transcriptionModel, EmailGeneratorService emailGeneratorService) {
        this.chatModel = chatModel;
        this.imageModel = imageModel;
        this.transcriptionModel = transcriptionModel;
        this.emailGeneratorService = emailGeneratorService;
    
    }

    @GetMapping("/test")
    public String test() {
        return "Backend is alive and CORS is configured!";
    }

    @GetMapping("/ask-ai")
    public String askAi(@RequestParam(value = "prompt") String prompt) {
        return chatModel.call(prompt);
    }

    @GetMapping("/generate-image")
    public Map<String, String> generateImage(@RequestParam(value = "prompt") String prompt) {
        ImageResponse response = imageModel.call(new ImagePrompt(prompt));
        String imageUrl = response.getResult().getOutput().getUrl();
        return Collections.singletonMap("url", imageUrl);
    }

    // ✅ NEW endpoint - AI analyzes PDF and returns structured JSON
    @PostMapping("/analyze-pdf")
    public ResponseEntity<String> analyzePdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "Analyze this document and extract all important information.") String userPrompt
    ) throws IOException {

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
           public ResponseEntity<String> transcribeAudio
        (@RequestParam("file") MultipartFile file) throws IOException {
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
    public String generateReply(@RequestBody Map<String, String> payload) {
        String emailContent = payload.get("emailContent");
        String tone = payload.get("tone");
        return emailGeneratorService.generateEmailReply(emailContent, tone);
    }
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
@ResponseBody
public org.springframework.core.io.Resource getSitemap() {
    return new org.springframework.core.io.ClassPathResource("static/sitemap.xml");
}
     @PostMapping("/prepare-interview")
public ResponseEntity<String> prepareInterview(
        @RequestParam("file") MultipartFile file,
        @RequestParam("jd") String jobDescription
) throws IOException {

    // 1. Extract Resume Text
    String resumeText = readPdf(file);

    // 2. Build the Comprehensive Prompt
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
          "questions": [{"q": "...", "type": "...", "guidance": "...", "tips": "..."}],
          "flashcards": [{"front": "...", "back": "..."}]
        }
        """.formatted(jobDescription, resumeText);

    // 3. Call AI and Clean Response
    String aiResponse = chatModel.call(aiPrompt);
    String cleanedJson = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();

    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(cleanedJson);
}


}