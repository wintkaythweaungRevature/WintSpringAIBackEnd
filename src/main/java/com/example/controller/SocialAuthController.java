package com.example.Controller;

import com.example.service.SocialAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = {
    "http://localhost:3000",
    "https://wintaibot.com",
    "https://www.wintaibot.com",
    "https://api.wintaibot.com"
}, allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST,
    RequestMethod.DELETE, RequestMethod.OPTIONS})
@RestController
@RequestMapping("/api/social")
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    @Value("${social.frontend-url:https://www.wintaibot.com}")
    private String frontendUrl;

    public SocialAuthController(SocialAuthService socialAuthService) {
        this.socialAuthService = socialAuthService;
    }

    /**
     * Returns the OAuth URL for the user to open in a popup/redirect.
     * GET /api/social/connect/youtube
     */
    @GetMapping("/connect/{platform}")
    public ResponseEntity<Map<String, String>> getConnectUrl(
            @PathVariable String platform,
            @AuthenticationPrincipal UserDetails userDetails) {
        String url = socialAuthService.getAuthorizationUrl(platform, userDetails.getUsername());
        return ResponseEntity.ok(Map.of("url", url, "platform", platform));
    }

    /**
     * OAuth callback — platform redirects here after user grants permission.
     * GET /api/social/callback/youtube?code=...&state=...
     * Redirects back to frontend with success/error status.
     */
    @GetMapping("/callback/{platform}")
    public void handleCallback(
            @PathVariable String platform,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletResponse response) throws IOException {

        if (error != null || code == null) {
            response.sendRedirect(frontendUrl + "?social_connect=error&platform=" + platform);
            return;
        }

        try {
            // state contains the user email (set during getAuthorizationUrl)
            socialAuthService.handleCallback(platform, code, state);
            response.sendRedirect(frontendUrl + "?social_connect=success&platform=" + platform);
        } catch (Exception e) {
            response.sendRedirect(frontendUrl + "?social_connect=error&platform=" + platform
                + "&msg=" + e.getMessage());
        }
    }

    /**
     * Get list of connected platforms for current user.
     * GET /api/social/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<String> connected = socialAuthService.getConnectedPlatforms(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("connected", connected));
    }

    /**
     * Disconnect a platform.
     * DELETE /api/social/disconnect/youtube
     */
    @DeleteMapping("/disconnect/{platform}")
    public ResponseEntity<Map<String, String>> disconnect(
            @PathVariable String platform,
            @AuthenticationPrincipal UserDetails userDetails) {
        socialAuthService.disconnect(userDetails.getUsername(), platform);
        return ResponseEntity.ok(Map.of("message", "Disconnected from " + platform));
    }
}
