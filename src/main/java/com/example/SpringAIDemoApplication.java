package com.example;

import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringAIDemoApplication {

    private static final String API_KEY = "OPENAI_API_KEY"; // သင့်ရဲ့ OpenAI API Key ကို ဒီမှာ ထည့်ပါ

    public static void main(String[] args) {
    	 System.setProperty("server.port", "8081");
        SpringApplication.run(SpringAIDemoApplication.class, args);
       
    }

    @Bean
    public OpenAiChatModel openAiChatModel() {
        return new OpenAiChatModel(new OpenAiApi(API_KEY));
    }

    @Bean
    public OpenAiImageModel openAiImageModel() {
        return new OpenAiImageModel(new OpenAiImageApi(API_KEY));
    }

    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel() {
        return new OpenAiEmbeddingModel(new OpenAiApi(API_KEY));
    }

    // အခု Error တက်နေတဲ့ Transcription (Audio) အတွက်
    @Bean
    public OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel() {
        return new OpenAiAudioTranscriptionModel(new OpenAiAudioApi(API_KEY));
    }
    
    // နောက်ထပ်တက်လာနိုင်တဲ့ Speech Model အတွက်ပါ ကြိုဆောက်ထားပေးပါမယ်
    @Bean
    public OpenAiAudioSpeechModel openAiAudioSpeechModel() {
        return new OpenAiAudioSpeechModel(new OpenAiAudioApi(API_KEY));
    }
}