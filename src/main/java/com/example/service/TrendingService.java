package com.example.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Provides viral trends, news, and content ideas for users.
 * Supports both legacy format (getTrendingInfo) and frontend format (getTrendsForUploadStep).
 */
@Service
public class TrendingService {

    private final ChatModel chatModel;

    public TrendingService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Returns { trends: [...], news: [...] } for the Upload step "Viral trends & news" card.
     * trends: 4-5 bullet items (hashtags, formats, best times).
     * news: 2-3 short platform/news items (algorithm or product updates).
     */
    public Map<String, Object> getTrendsForUploadStep() {
        String prompt = """
            You are a social media trends analyst. Provide current viral trends and platform news for creators.
            Return a JSON object with exactly these keys (valid JSON only, no markdown):
            {
              "trends": [
                "hashtag or topic trending now (e.g. #AITools trending...)",
                "format insight (e.g. Short-form vertical video engagement up 40%...)",
                "Best posting time: [day] [time]",
                "one more trend",
                "one more trend"
              ],
              "news": [
                "Instagram Reels: [algorithm or product update]",
                "YouTube Shorts: [update]",
                "TikTok: [update]"
              ]
            }
            trends: 4-5 actionable bullet items. news: 2-3 short platform-specific updates.
            Be specific and current. Use real platform names.
            """;
        try {
            String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getContent();
            return parseTrendsJson(response);
        } catch (Exception e) {
            return getDefaultTrendsAndNews();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTrendsJson(String json) {
        try {
            String clean = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(clean, Map.class);
            if (parsed.containsKey("trends") && parsed.containsKey("news")) {
                return parsed;
            }
        } catch (Exception ignored) { }
        return getDefaultTrendsAndNews();
    }

    private Map<String, Object> getDefaultTrendsAndNews() {
        return Map.of(
            "trends", List.of(
                "#AITools trending across TikTok and Reels",
                "Short-form vertical video engagement up 40% this quarter",
                "Best posting time: Tuesday 7 PM",
                "Behind-the-scenes and tutorial formats performing well",
                "Original audio prioritized over reused sounds"
            ),
            "news", List.of(
                "Instagram Reels: algorithm now prioritizes original audio.",
                "YouTube Shorts: 60s clips with strong retention favored.",
                "TikTok: new Series feature for multi-part content."
            )
        );
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
