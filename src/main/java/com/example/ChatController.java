package com.example;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

// အဓိက ပြင်ဆင်ထားသော အပိုင်း (CORS ခွင့်ပြုချက်)
@CrossOrigin(origins = {
    "http://localhost:3000",                // Local development အတွက်
    "https://wintkaythweaung.com",          // Live domain အတွက်
    "https://www.wintkaythweaung.com",
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

    // Backend နိုး၊ မနိုး စစ်ဆေးရန် (Browser မှာ api.wintkaythweaung.com/api/ai/test လို့ ရိုက်ကြည့်ပါ)
    @GetMapping("/test")
    public String test() {
        return "Backend is alive and CORS is configured!";
    }

    // စာသားအတွက် (Ask AI)
    @GetMapping("/ask-ai")
    public String askAi(@RequestParam(value = "prompt") String prompt) {
        return chatModel.call(prompt);
    }

    // ပုံအတွက် (Image Generator)
    @GetMapping("/generate-image")
    public Map<String, String> generateImage(@RequestParam(value = "prompt") String prompt) {
        ImageResponse response = imageModel.call(new ImagePrompt(prompt));
        String imageUrl = response.getResult().getOutput().getUrl();
        return Collections.singletonMap("url", imageUrl);
    }
}