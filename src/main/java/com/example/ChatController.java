package com.example;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;@CrossOrigin(origins = {
    "https://wintkaythweaung.com",          // www မပါဘဲ တစ်ခု
    "https://www.wintkaythweaung.com",      // www ပါတာ တစ်ခု
    "https://springaifrontend.ms-wintkaythweaung-eb9.workers.dev" // Cloudflare Workers အတွက်
}, allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
@RequestMapping("/api/ai")
@RestController
public class ChatController {

    private final OpenAiChatModel chatModel;
    private final ImageModel imageModel; // ၁။ ImageModel ကို ထပ်ထည့်ပါ

    public ChatController(OpenAiChatModel chatModel, ImageModel imageModel) {
        this.chatModel = chatModel;
        this.imageModel = imageModel; // ၂။ Dependency Injection လုပ်ပါ
    }
    @GetMapping("/test")
public String test() {
    return "Backend is alive!";
}
    // စာသားအတွက် (Ask AI)
    @GetMapping("/ask-ai")
    public String askAi(@RequestParam(value = "prompt") String prompt) {
        return chatModel.call(prompt);
    }

    // ပုံအတွက် (Image Generator) ✅
    @GetMapping("/generate-image")
    public Map<String, String> generateImage(@RequestParam(value = "prompt") String prompt) {
        ImageResponse response = imageModel.call(new ImagePrompt(prompt));
        String imageUrl = response.getResult().getOutput().getUrl();
        
        // Frontend မှာ <img> tag နဲ့ သုံးလို့ရအောင် URL ကို JSON ပုံစံနဲ့ ပြန်ပေးပါ
        return Collections.singletonMap("url", imageUrl);
    }
}