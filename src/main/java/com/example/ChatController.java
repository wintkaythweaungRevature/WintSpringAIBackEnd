package com.example;

// ⚠️ ဒီ Import တွေက မရှိမဖြစ် လိုအပ်ပါတယ်
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final OpenAiChatModel chatModel;

    // Spring Boot က chatModel ကို အလိုအလျောက် ထည့်ပေးပါလိမ့်မယ် (Dependency Injection)
    public ChatController(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    // 🌐 API Endpoint: http://localhost:8080/ai/chat?message=hello
    @GetMapping("/ai/chat")
    public String completion(@RequestParam(value = "message", defaultValue = "Hello, tell me a joke") String message) {
        return chatModel.call(message);
    }
    
    // 🏠 Home Page: http://localhost:8080/
    @GetMapping("/")
    public String home() {
        return "Spring AI Backend is Running Successfully!";
    }
}