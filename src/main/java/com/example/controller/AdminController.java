package com.example.controller;

import com.example.entity.User;
import com.example.service.StripeService;
import com.example.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private final UserService userService;
    private final StripeService stripeService;

    public AdminController(UserService userService, StripeService stripeService) {
        this.userService = userService;
        this.stripeService = stripeService;
    }

    private boolean isAdmin(UserDetails userDetails) {
        if (userDetails == null) return false;
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /** List all users (admin only). */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@AuthenticationPrincipal UserDetails userDetails) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        List<User> users = userService.getAllUsers();
        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", u.getId());
            m.put("email", u.getEmail());
            m.put("firstName", u.getFirstName());
            m.put("lastName", u.getLastName());
            m.put("membershipType", u.getMembershipType());
            m.put("role", u.getRole());
            m.put("active", u.isActive());
            m.put("stripeCustomerId", u.getStripeCustomerId());
            m.put("createdAt", u.getCreatedAt());
            m.put("deactivatedAt", u.getDeactivatedAt());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Activate a user account (admin only).
     * Account is restored as FREE — user must re-subscribe to regain MEMBER access.
     */
    @PostMapping("/users/{userId}/activate")
    public ResponseEntity<?> activateUser(@PathVariable Long userId,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        try {
            userService.adminActivateUser(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "User account activated. Membership set to FREE — user must re-subscribe.",
                    "membershipType", "FREE"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Deactivate a user account (admin only).
     * Cancels any active Stripe subscription and sets membership to FREE.
     */
    @PostMapping("/users/{userId}/deactivate")
    public ResponseEntity<?> deactivateUser(@PathVariable Long userId,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        try {
            // Cancel Stripe subscription if active
            User user = userService.findById(userId);
            if (user.getStripeCustomerId() != null && "MEMBER".equals(user.getMembershipType())) {
                try {
                    stripeService.cancelSubscriptionAtPeriodEnd(userId);
                } catch (Exception e) {
                    log.warn("Could not cancel Stripe subscription for user {}: {}", userId, e.getMessage());
                }
            }
            // Set membership FREE then deactivate
            userService.updateMembership(userId, "FREE");
            userService.adminDeactivateUser(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "User account deactivated and subscription cancelled."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
