package com.example;

import org.springframework.ai.chat.model.ChatModel; // generic interface ကို သုံးတာ ပိုကောင်းပါတယ်
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
// @CrossOrigin ကို SecurityConfig မှာ Pattern နဲ့ ပေးထားရင် ဒီမှာ ထပ်ရေးစရာ မလိုတော့ပါဘူး
// ဒါပေမဲ့ သေချာအောင် ထားချင်ရင်လည်း ထားနိုင်ပါတယ်
public class ChatController {

    private final ChatModel chatModel;
    private final ImageModel imageModel;

    public ChatController(ChatModel chatModel, ImageModel imageModel) {
        this.chatModel = chatModel;
        this.imageModel = imageModel;
    }

    @GetMapping("/test")
    public String test() {
        return "Backend is alive!";
    }

    // စာသားအတွက် (Ask AI) ✅ JSON format ပြောင်းထားပေးပါတယ်
  @GetMapping("/ask-ai")
public ResponseEntity<?> askAi(@RequestParam String prompt) {
    try {
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Prompt is missing"));
        }

        String answer = chatModel.call(prompt);
        return ResponseEntity.ok(Map.of("answer", answer));

    } catch (Exception e) {
        e.printStackTrace(); // 🔥 server log မှာ error မြင်ရမယ်
        return ResponseEntity.status(500)
            .body(Map.of("error", "AI service failed"));
    }
}

    // ပုံအတွက် (Image Generator) ✅
    @GetMapping("/generate-image")
    public Map<String, String> generateImage(@RequestParam(value = "prompt") String prompt) {
        ImageResponse response = imageModel.call(new ImagePrompt(prompt));
        String imageUrl = response.getResult().getOutput().getUrl();
        return Collections.singletonMap("url", imageUrl);
    }
}