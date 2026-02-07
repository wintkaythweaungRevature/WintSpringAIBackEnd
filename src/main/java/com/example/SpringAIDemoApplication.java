package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.openai.OpenAiChatModel;

@SpringBootApplication
@RestController // 👈 ဒီမှာတင် Controller လုပ်လိုက်မယ်
public class SpringAIDemoApplication {

    private final OpenAiChatModel chatModel;

    public SpringAIDemoApplication(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringAIDemoApplication.class, args);
    }

    @GetMapping("/")
    public String home() {
        return "I AM ALIVE AND RUNNING!";
    }

    @GetMapping("/ai/chat")
    public String completion(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        return chatModel.call(message);
    }
}