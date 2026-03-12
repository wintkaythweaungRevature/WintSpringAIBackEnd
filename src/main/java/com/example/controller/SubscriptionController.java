package com.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);
    private final StripeService stripeService;
    private final UserService userService;

    public SubscriptionController(StripeService stripeService, UserService userService) {
        this.stripeService = stripeService;
        this.userService = userService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(@AuthenticationPrincipal UserDetails userDetails,
                                          @RequestBody(required = false) Map<String, String> body) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            String plan = (body != null ? body.get("plan") : null);
            if (plan == null || plan.isBlank()) plan = "MEMBER";
            Map<String, String> session = stripeService.createCheckoutSession(user.getId(), plan);
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Checkout failed", e);
            String msg = e.getMessage() != null ? e.getMessage() : "Checkout failed. Please try again.";
            if (msg.contains("Invalid API Key") || msg.contains("No such price")) {
                msg = "Payment setup error. Please contact support.";
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
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
