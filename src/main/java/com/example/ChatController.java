package com.example;

import org.springframework.ai.openai.OpenAiChatModel; // သို့မဟုတ် သင်သုံးသော Model
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "https://www.wintkaythweaung.com")
@RequestMapping("/api/ai")
@RestController
public class ChatController {

    // ၁။ ဒီစာကြောင်းကို ထည့်ပေးရန် လိုအပ်သည် (Variable Declaration)
    private final OpenAiChatModel chatModel;

    // ၂။ ဒီ Constructor ကို ထည့်ပေးရန် လိုအပ်သည် (Dependency Injection)
    public ChatController(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping("/ask-ai")
    public String askAi(@RequestParam(value = "prompt") String prompt) {
        // အခုဆိုလျှင် chatModel ကို ဒီနေရာမှာ သုံးလို့ရသွားပါပြီ
        return chatModel.call(prompt);
    }
}