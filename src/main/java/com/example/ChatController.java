package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.ComponentScan;
@RestController
@ComponentScan(basePackages = "com.example")
public class ChatController {

    private final OpenAiChatModel chatModel;

    public ChatController(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping("/ai/chat")
    public String completion(@RequestParam(value = "message", defaultValue = "Hello, tell me a joke") String message) {
        // Version အသစ်တွေမှာ call(message) တိုက်ရိုက်မရရင် အောက်ကအတိုင်း ရေးရပါတယ်
        try {
            return chatModel.call(message);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    @GetMapping("/")
    public String home() {
        return "Spring AI Backend is Running Successfully!";
    }
}