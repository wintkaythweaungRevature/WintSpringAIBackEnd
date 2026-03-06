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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity; // Added
import org.springframework.security.access.method.P;
import org.springframework.http.HttpHeaders;    // Added
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;      // Added
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
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
     // ✅ FLASHCARD ENDPOINT - AI generates interview flashcards
    @PostMapping("/generate-flashcards")
    public ResponseEntity<String> generateFlashcards(@RequestBody Map<String, String> payload) {
        String jdText = payload.get("jd");
        String cvText = payload.get("cv");

        if (jdText == null || cvText == null) {
            return ResponseEntity.badRequest().body("{\"error\": \"Missing JD or CV content\"}");
        }

        // AI ကို Flashcard ထုတ်ခိုင်းမည့် Prompt
        String flashcardPrompt = """
                You are an expert interview coach. Based on the following Job Description and Candidate CV, 
                generate 5-6 high-quality interview flashcards. 
                Each flashcard must have a 'question' and a 'answer' (focused on key talking points).
                
                Return ONLY a JSON array of objects with this exact structure:
                [
                  { "q": "Question here", "a": "Answer/Key points here" },
                  { "q": "Question here", "a": "Answer/Key points here" }
                ]

                Rules:
                - Focus on matching the candidate's skills to the job requirements.
                - Keep answers concise and easy to memorize.
                - Return ONLY the JSON array. No conversational text.

                JOB DESCRIPTION:
                %s

                CANDIDATE CV:
                %s
                """.formatted(jdText, cvText);

        try {
            String aiResponse = chatModel.call(flashcardPrompt);
            
            // Clean up the response if AI includes markdown (like ```json ... ```)
            String cleanedResponse = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cleanedResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"AI generation failed: " + e.getMessage() + "\"}");
        }
    }

    // ✅ NEW: PDF Export for Flashcards
    @PostMapping("/flashcards-pdf")
    public ResponseEntity<byte[]> exportFlashcardsPdf(@RequestBody List<Map<String, String>> flashcards) throws IOException {
        try (PDDocument document = new PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);

            try (org.apache.pdfbox.pdmodel.PDPageContentStream contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 16);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText("Interview Preparation Flashcards");
                contentStream.newLineAtOffset(0, -30);
                contentStream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);

                for (Map<String, String> card : flashcards) {
                    contentStream.showText("Q: " + card.get("q"));
                    contentStream.newLineAtOffset(0, -20);
                    contentStream.showText("A: " + card.get("a"));
                    contentStream.newLineAtOffset(0, -30);
                }
                contentStream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "Interview_Flashcards.pdf");

            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
        }
    }
}