package com.example.controller;

import com.example.entity.User;
import com.example.service.StripeService;
import com.example.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final StripeService stripeService;
    private final UserService userService;

    public SubscriptionController(StripeService stripeService, UserService userService) {
        this.stripeService = stripeService;
        this.userService = userService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(@AuthenticationPrincipal UserDetails userDetails,
                                          @RequestBody Map<String, String> body) {
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            String plan = body.getOrDefault("plan", "MEMBER");
            Map<String, String> session = stripeService.createCheckoutSession(user.getId(), plan);
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/portal")
    public ResponseEntity<?> createPortalSession(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            Map<String, String> session = stripeService.createCustomerPortalSession(user.getId());
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
