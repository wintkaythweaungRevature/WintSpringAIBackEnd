package com.example;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailGeneratorService {

    private final ChatClient chatClient;

    // API URL ကို ဒီမှာ မသုံးဘဲ application.properties ကနေ Spring AI က အလိုအလျောက် ယူသွားပါလိမ့်မယ်
    @Value("${spring.ai.openai.api-url}")
    private String apiUrl;

    // ChatClient.Builder ကို သုံးပြီး Constructor မှာ Bean ဆောက်ပေးပါ
    public EmailGeneratorService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String generateEmailReply(String emailContent, String tone) {
        // ChatClient ရဲ့ Fluent API (prompt, user, call) ကို သုံးတာ ပိုကောင်းပါတယ်
        return chatClient.prompt()
                .user("Generate a " + tone + " reply to the following email: " + emailContent)
                .call()
                .content();
    }
}