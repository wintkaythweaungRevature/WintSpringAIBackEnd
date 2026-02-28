

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity; // Added
import org.springframework.http.HttpHeaders;    // Added
import org.springframework.http.MediaType;      // Added
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;
import java.io.ByteArrayOutputStream; // Added
import java.io.IOException;

// Apache POI & PDFBox
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

    @PostMapping("/convert-pdf-to-excel")
    public ResponseEntity<byte[]> convertToExcel(@RequestParam("file") MultipartFile file) throws IOException {
        // 1. Extract text from PDF
        String pdfContent = readPdf(file); 
        
        // 2. Create Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Spending Analysis");
        
        String[] lines = pdfContent.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue(lines[i]);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=spending.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(out.toByteArray());
    }

    // ✅ အသစ်ထည့်လိုက်သော Method (Missing readPdf logic)
    private String readPdf(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}