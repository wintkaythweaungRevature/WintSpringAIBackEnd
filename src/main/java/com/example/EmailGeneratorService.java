package com.example;

import com.fasterxml.jackson.databind.JsonNode; // ✅ Add this import
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

@Service
public class EmailGeneratorService {

    @Value("${spring.ai.openai.api-url}")
    private String geminiApiUrl;

    @Value("${spring.ai.openai.api-key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String generateEmail(EmailRequest emailRequest) {
        String prompt = buildPrompt(emailRequest);

        // Gemini JSON Format
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String urlWithKey = geminiApiUrl + "?key=" + geminiApiKey;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            // ✅ Map အစား JsonNode နဲ့ အဖြေယူတာက ပိုစိတ်ချရပါတယ်
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(urlWithKey, entity, JsonNode.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return extractTextFromNode(response.getBody());
            } else {
                return "Error: API returned status " + response.getStatusCode();
            }
        } catch (Exception e) {
            return "Error calling AI: " + e.getMessage();
        }
    }

    private String buildPrompt(EmailRequest emailRequest) {
        return "Write a professional email reply. Tone: " + 
               (emailRequest.getTone() != null ? emailRequest.getTone() : "formal") + 
               ".\nContent: " + emailRequest.getEmailContent();
    }

    private String extractTextFromNode(JsonNode root) {
        try {
            // ✅ Gemini structure: candidates[0].content.parts[0].text
            return root.path("candidates")
                       .get(0)
                       .path("content")
                       .path("parts")
                       .get(0)
                       .path("text")
                       .asText();
        } catch (Exception e) {
            return "Failed to parse AI response. Raw: " + root.toString();
        }
    }
}