package com.example;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailGeneratorService {

    private final ChatClient chatClient;

    @Value("${spring.ai.openai.api-url}") // ဒီနေရာမှာ Docker run တုန်းက error တက်ခဲ့တာပါ
    private String apiUrl;

    public EmailGeneratorService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String generateEmailReply(String emailContent, String tone) {
        String prompt = "Generate a " + tone + " reply to the following email: " + emailContent;
        return chatClient.call(prompt);
    }
}