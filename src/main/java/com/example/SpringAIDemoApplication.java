package com.example;

import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.*;
import org.springframework.beans.factory.annotation.Value; // ဒါလေး ထည့်ပေးပါ
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringAIDemoApplication {

    // @Value ကို သုံးမှသာ AWS ထဲက Key ကို Java က နားလည်မှာ ဖြစ်ပါတယ်
    @Value("${SPRING_AI_OPENAI_API_KEY}")
    private String apiKey;

    public static void main(String[] args) {
        System.setProperty("server.port", "8080");
        SpringApplication.run(SpringAIDemoApplication.class, args);
    }

    @Bean
    public OpenAiChatModel openAiChatModel() {
        // API_KEY အစား apiKey variable ကို သုံးပါ
        return new OpenAiChatModel(new OpenAiApi(apiKey));
    }

    @Bean
    public OpenAiImageModel openAiImageModel() {
        return new OpenAiImageModel(new OpenAiImageApi(apiKey));
    }

    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel() {
        return new OpenAiEmbeddingModel(new OpenAiApi(apiKey));
    }

    @Bean
    public OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel() {
        return new OpenAiAudioTranscriptionModel(new OpenAiAudioApi(apiKey));
    }
    
    @Bean
    public OpenAiAudioSpeechModel openAiAudioSpeechModel() {
        return new OpenAiAudioSpeechModel(new OpenAiAudioApi(apiKey));
    }
}