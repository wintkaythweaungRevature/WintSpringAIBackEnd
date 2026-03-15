package com.example.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Provides viral trends, news, and content ideas for users.
 */
@Service
public class TrendingService {

    private final ChatModel chatModel;

    public TrendingService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Map<String, Object> getTrendingInfo() {
        String prompt = """
            You are a social media trends analyst. Provide current viral trends and content ideas for creators.
            Return a JSON object with these keys (use valid JSON only, no markdown):
            {
              "trendingTopics": ["topic1", "topic2", "topic3", "topic4", "topic5"],
              "trendingHashtags": ["#hashtag1", "#hashtag2", "#hashtag3", "#hashtag4", "#hashtag5"],
              "viralFormats": ["short-form vertical video", "behind-the-scenes", "tutorial", "trending audio", "challenge"],
              "bestPostingTimes": {"youtube": "Tue 2-4pm", "instagram": "Wed 11am", "tiktok": "Thu 7-9pm", "linkedin": "Tue-Thu 8-10am"},
              "contentIdeas": ["idea1", "idea2", "idea3"],
              "newsSummary": "Brief 1-2 sentence summary of what's trending in social/content this week"
            }
            Base this on current trends. Be specific and actionable.
            """;
        String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getContent();
        return parseTrendingJson(response);
    }

    private Map<String, Object> parseTrendingJson(String json) {
        try {
            // Simple extraction - GPT may wrap in markdown
            String clean = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(clean, Map.class);
        } catch (Exception e) {
            return Map.of(
                "trendingTopics", List.of("AI tools", "Productivity", "Short-form video", "Tutorials", "Behind-the-scenes"),
                "trendingHashtags", List.of("#viral", "#fyp", "#trending", "#contentcreator", "#explore"),
                "viralFormats", List.of("Short vertical video", "Tutorial", "Trending audio"),
                "bestPostingTimes", Map.of("youtube", "Tue 2-4pm", "instagram", "Wed 11am", "tiktok", "Thu 7-9pm"),
                "contentIdeas", List.of("Create content around trending topics", "Use trending sounds", "Share tips and tutorials"),
                "newsSummary", "Check social platforms for latest viral trends."
            );
        }
    }
}
