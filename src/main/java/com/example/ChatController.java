

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

  @PostMapping("/analyze-pdf")
    public ResponseEntity<Map<String, String>> analyzePdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "Analyze this document.") String userPrompt
    ) throws IOException {

        String pdfText = readPdf(file);

        String aiPrompt = "Analyze the following content and provide a detailed report:\n" + pdfText;

        String aiResponse = chatModel.call(aiPrompt);

        // ✅ This MUST return a Map so the Frontend can read data.analysis
        return ResponseEntity.ok(Map.of(
            "analysis", aiResponse,
            "rawText", pdfText
        ));
    }
    
private String readPdf(MultipartFile file) throws IOException {
    try (PDDocument document = PDDocument.load(file.getInputStream())) {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setEndPage(3); // Only process first 3 pages to prevent 502 timeout
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