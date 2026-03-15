package com.example.service;

import com.example.entity.SocialToken;
import com.example.entity.User;
import com.example.repository.SocialTokenRepository;
import com.example.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SocialAuthService {

    private final SocialTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${social.redirect.base-url:https://api.wintaibot.com}")
    private String redirectBaseUrl;

    // YouTube / Google
    @Value("${social.youtube.client-id:}") private String youtubeClientId;
    @Value("${social.youtube.client-secret:}") private String youtubeClientSecret;

    // Instagram / Facebook
    @Value("${social.instagram.app-id:}") private String instagramAppId;
    @Value("${social.instagram.app-secret:}") private String instagramAppSecret;

    // TikTok
    @Value("${social.tiktok.client-key:}") private String tiktokClientKey;
    @Value("${social.tiktok.client-secret:}") private String tiktokClientSecret;

    // LinkedIn
    @Value("${social.linkedin.client-id:}") private String linkedinClientId;
    @Value("${social.linkedin.client-secret:}") private String linkedinClientSecret;

    // X (Twitter)
    @Value("${social.x.client-id:}") private String xClientId;
    @Value("${social.x.client-secret:}") private String xClientSecret;

    public SocialAuthService(SocialTokenRepository tokenRepository, UserRepository userRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    /**
     * Generate the OAuth authorization URL for a given platform.
     * Frontend redirects the user to this URL.
     */
    public String getAuthorizationUrl(String platform, String userEmail) {
        String callbackUrl = redirectBaseUrl + "/api/social/callback/" + platform;
        String state = userEmail; // simple state; use JWT/CSRF token in production

        return switch (platform.toLowerCase()) {
            case "youtube" -> "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + youtubeClientId +
                "&redirect_uri=" + callbackUrl +
                "&response_type=code" +
                "&scope=https://www.googleapis.com/auth/youtube.upload+https://www.googleapis.com/auth/youtube.readonly" +
                "&access_type=offline&prompt=consent" +
                "&state=" + state;

            case "instagram" -> "https://api.instagram.com/oauth/authorize" +
                "?client_id=" + instagramAppId +
                "&redirect_uri=" + callbackUrl +
                "&scope=user_profile,user_media" +
                "&response_type=code" +
                "&state=" + state;

            case "facebook" -> "https://www.facebook.com/v18.0/dialog/oauth" +
                "?client_id=" + instagramAppId +
                "&redirect_uri=" + callbackUrl +
                "&scope=pages_manage_posts,pages_read_engagement,publish_video" +
                "&state=" + state;

            case "tiktok" -> "https://www.tiktok.com/v2/auth/authorize/" +
                "?client_key=" + tiktokClientKey +
                "&redirect_uri=" + callbackUrl +
                "&scope=user.info.basic,video.publish,video.upload" +
                "&response_type=code" +
                "&state=" + state;

            case "linkedin" -> "https://www.linkedin.com/oauth/v2/authorization" +
                "?client_id=" + linkedinClientId +
                "&redirect_uri=" + callbackUrl +
                "&scope=w_member_social+r_liteprofile+r_emailaddress" +
                "&response_type=code" +
                "&state=" + state;

            case "x" -> "https://twitter.com/i/oauth2/authorize" +
                "?client_id=" + xClientId +
                "&redirect_uri=" + callbackUrl +
                "&scope=tweet.write+tweet.read+users.read+offline.access" +
                "&response_type=code" +
                "&code_challenge=challenge&code_challenge_method=plain" +
                "&state=" + state;

            default -> throw new IllegalArgumentException("Unsupported platform: " + platform);
        };
    }

    /**
     * Exchange authorization code for access token and store it.
     */
    @Transactional
    public SocialToken handleCallback(String platform, String code, String userEmail) {
        String callbackUrl = redirectBaseUrl + "/api/social/callback/" + platform;
        Map<String, String> tokens = exchangeCodeForTokens(platform, code, callbackUrl);

        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

        SocialToken token = tokenRepository
            .findByUserIdAndPlatform(user.getId(), platform)
            .orElse(new SocialToken());

        token.setUser(user);
        token.setPlatform(platform);
        token.setAccessToken(tokens.get("access_token"));
        token.setRefreshToken(tokens.getOrDefault("refresh_token", null));
        token.setConnectedAt(LocalDateTime.now());

        String expiresIn = tokens.get("expires_in");
        if (expiresIn != null) {
            token.setExpiresAt(LocalDateTime.now().plusSeconds(Long.parseLong(expiresIn)));
        }

        return tokenRepository.save(token);
    }

    /**
     * Get list of connected platform IDs for a user.
     */
    public List<String> getConnectedPlatforms(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return tokenRepository.findByUserId(user.getId())
            .stream().map(SocialToken::getPlatform).collect(Collectors.toList());
    }

    /**
     * Get a stored access token for a user+platform.
     */
    public String getAccessToken(String userEmail, String platform) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return tokenRepository.findByUserIdAndPlatform(user.getId(), platform)
            .map(SocialToken::getAccessToken)
            .orElseThrow(() -> new RuntimeException("Not connected to " + platform));
    }

    /**
     * Disconnect (remove stored tokens) for a platform.
     */
    @Transactional
    public void disconnect(String userEmail, String platform) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));
        tokenRepository.deleteByUserIdAndPlatform(user.getId(), platform);
    }

    /* ── Internal: exchange code for tokens ────────────────────── */
    @SuppressWarnings("unchecked")
    private Map<String, String> exchangeCodeForTokens(String platform, String code, String redirectUri) {
        String tokenUrl;
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        switch (platform.toLowerCase()) {
            case "youtube" -> {
                tokenUrl = "https://oauth2.googleapis.com/token";
                params.add("client_id", youtubeClientId);
                params.add("client_secret", youtubeClientSecret);
            }
            case "instagram" -> {
                tokenUrl = "https://api.instagram.com/oauth/access_token";
                params.add("client_id", instagramAppId);
                params.add("client_secret", instagramAppSecret);
            }
            case "facebook" -> {
                tokenUrl = "https://graph.facebook.com/v18.0/oauth/access_token";
                params.add("client_id", instagramAppId);
                params.add("client_secret", instagramAppSecret);
            }
            case "tiktok" -> {
                tokenUrl = "https://open.tiktokapis.com/v2/oauth/token/";
                params.add("client_key", tiktokClientKey);
                params.add("client_secret", tiktokClientSecret);
            }
            case "linkedin" -> {
                tokenUrl = "https://www.linkedin.com/oauth/v2/accessToken";
                params.add("client_id", linkedinClientId);
                params.add("client_secret", linkedinClientSecret);
            }
            case "x" -> {
                tokenUrl = "https://api.twitter.com/2/oauth2/token";
                params.add("client_id", xClientId);
                params.add("code_verifier", "challenge");
            }
            default -> throw new IllegalArgumentException("Unsupported platform: " + platform);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        Map<String, String> result = new HashMap<>();
        if (response.getBody() != null) {
            response.getBody().forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        }
        return result;
    }
}
