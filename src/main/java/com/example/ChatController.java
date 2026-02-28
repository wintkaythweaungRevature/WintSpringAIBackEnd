

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;
import java.io.IOException;

// PDFBox
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

    public ChatController(OpenAiChatModel chatModel, ImageModel imageModel) {
        this.chatModel = chatModel;
        this.imageModel = imageModel;
    }

    @GetMapping("/test")
    public String test() {
        return "Backend is alive!";
    }

    @GetMapping("/generate-image")
    public Map<String, String> generateImage(@RequestParam(value = "prompt") String prompt) {
        ImageResponse response = imageModel.call(new ImagePrompt(prompt));
        String imageUrl = response.getResult().getOutput().getUrl();
        return Collections.singletonMap("url", imageUrl);
    }

    // ✅ FIXED: Return type changed to Map<String, String> to match the body
    @PostMapping("/analyze-pdf")
    public ResponseEntity<Map<String, String>> analyzePdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "Analyze this document.") String userPrompt
    ) throws IOException {

        // Step 1: Extract text
        String pdfText = readPdf(file);

        // Step 2: AI Prompt
        String aiPrompt = """
                You are a data extraction assistant. Analyze the PDF content and return ONLY a JSON object:
                {
                  "summary": "summary here",
                  "table_headers": ["H1", "H2"],
                  "table_rows": [["R1C1", "R1C2"]],
                  "insights": ["insight"]
                }
                PDF Content:
                %s
                """.formatted(pdfText);

        String aiResponse = chatModel.call(aiPrompt);

        // ✅ Step 3: Return Map so React sees data.analysis
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "analysis", aiResponse,
                    "rawText", pdfText
                ));
    }

    private String readPdf(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    @PostMapping("/chatdoc")
    public ResponseEntity<Map<String, String>> chatWithDoc(@RequestBody Map<String, String> request) {
        String documentText = request.get("documentText");
        String userQuestion = request.get("question");

        String fullPrompt = """
                Use this document to answer:
                %s
                Question: %s
                """.formatted(documentText, userQuestion);

        String aiResponse = chatModel.call(fullPrompt); 

        return ResponseEntity.ok(Map.of("answer", aiResponse));
    }
}