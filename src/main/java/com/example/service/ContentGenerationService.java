package com.example.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * GPT-based content generation: captions, hashtags, descriptions per platform.
 */
@Service
public class ContentGenerationService {

    private final ChatModel chatModel;

    public ContentGenerationService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Map<String, String> generateForPlatform(String platform, String transcript, String filename) {
        String prompt = buildPrompt(platform, transcript, filename);
        String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getContent();
        return parseJsonResponse(response, platform);
    }

    public Map<String, String> generateForAllPlatforms(String transcript, String filename) {
        List<String> platforms = List.of(
            "YouTube", "YouTube Shorts", "Instagram Post", "Instagram Reel", "TikTok",
            "LinkedIn", "Facebook", "X (Twitter)", "Threads", "Pinterest"
        );
        Map<String, String> result = new java.util.HashMap<>();
        for (String p : platforms) {
            try {
                Map<String, String> gen = generateForPlatform(p, transcript, filename);
                result.put(p + "_caption", gen.getOrDefault("caption", ""));
                result.put(p + "_hashtags", gen.getOrDefault("hashtags", ""));
                result.put(p + "_description", gen.getOrDefault("description", ""));
            } catch (Exception e) {
                result.put(p + "_caption", "Check out my latest video! " + filename);
                result.put(p + "_hashtags", "#video #content #" + p.replaceAll("\\s+", ""));
                result.put(p + "_description", "");
            }
        }
        return result;
    }

    private String buildPrompt(String platform, String transcript, String filename) {
        return """
            You are a social media expert. Based on this video transcript, write an optimized post for %s.

            Transcript (first 1000 chars): %s

            Return ONLY valid JSON (no markdown, no code block):
            {"caption":"...","hashtags":"#tag1 #tag2 #tag3 #tag4 #tag5","description":"..."}

            Rules for %s:
            - YouTube: engaging description up to 500 chars, caption = title
            - YouTube Shorts: punchy caption under 100 chars
            - Instagram Post: casual, emoji-rich, up to 300 chars
            - Instagram Reel: trendy, short, include fyp
            - TikTok: trendy, short, include fyp hashtag
            - LinkedIn: professional, insight-driven, up to 400 chars
            - Facebook: friendly, engaging, up to 300 chars
            - X (Twitter): punchy, under 250 chars
            - Threads: conversational, under 300 chars
            - Pinterest: descriptive, inspiring, SEO keywords
            """.formatted(platform, transcript.substring(0, Math.min(transcript.length(), 1000)), platform);
    }

    private Map<String, String> parseJsonResponse(String json, String platform) {
        Map<String, String> out = new java.util.HashMap<>();
        out.put("caption", extractJsonField(json, "caption"));
        out.put("hashtags", extractJsonField(json, "hashtags"));
        out.put("description", extractJsonField(json, "description"));
        return out;
    }

    private String extractJsonField(String json, String field) {
        try {
            int start = json.indexOf("\"" + field + "\"");
            if (start < 0) return "";
            start = json.indexOf(":", start) + 1;
            char q = json.charAt(start);
            if (q != '"' && q != '\'') return "";
            int end = json.indexOf(q, start + 1);
            while (end > 0 && json.charAt(end - 1) == '\\') end = json.indexOf(q, end + 1);
            return end > start ? json.substring(start + 1, end).replace("\\\"", "\"") : "";
        } catch (Exception e) {
            return "";
        }
    }
}
