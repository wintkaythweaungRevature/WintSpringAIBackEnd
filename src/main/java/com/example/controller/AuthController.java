package com.example.controller;

import com.example.dto.AuthRequest;
import com.example.dto.AuthResponse;
import com.example.dto.RegisterRequest;
import com.example.entity.Subscription;
import com.example.entity.User;
import com.example.service.UserService;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody(required = false) RegisterRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Email and password are required"));
        }
        try {
            AuthResponse response = userService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) AuthRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
        }
        try {
            AuthResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AuthMeResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            userService.hasActivePaidAccess(user);
            user = userService.findById(user.getId());
            String membershipType = user.getMembershipType() != null ? user.getMembershipType() : "FREE";
            String subscriptionStatus = null;
            String subscriptionPeriodEnd = null;
            Boolean cancelAtPeriodEnd = null;
            Optional<Subscription> memberSub = userService.getActiveMemberSubscription(user);
            if (memberSub.isPresent()) {
                Subscription sub = memberSub.get();
                subscriptionStatus = sub.getStatus() != null ? sub.getStatus() : "active";
                if (sub.getCurrentPeriodEnd() != null) {
                    subscriptionPeriodEnd = sub.getCurrentPeriodEnd().format(DateTimeFormatter.ISO_LOCAL_DATE);
                }
                cancelAtPeriodEnd = sub.isCancelAtPeriodEnd();
            }
            String role = (user.getRole() != null && !user.getRole().isBlank()) ? user.getRole() : "ROLE_USER";
            return ResponseEntity.ok(new AuthMeResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    membershipType,
                    role,
                    subscriptionStatus,
                    subscriptionPeriodEnd,
                    cancelAtPeriodEnd
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    /** Deactivate logged-in user's account. Requires password confirmation. */
    @PostMapping("/deactivate")
    public ResponseEntity<?> deactivateAccount(@AuthenticationPrincipal UserDetails userDetails,
                                               @RequestBody(required = false) Map<String, String> body) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        String password = body != null ? body.get("password") : null;
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password is required to deactivate your account"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            userService.deactivateAccount(user.getId(), password);
            return ResponseEntity.ok(Map.of("message", "Account deactivated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Reactivate a previously deactivated account. Public endpoint (no auth required). */
    @PostMapping("/reactivate")
    public ResponseEntity<?> reactivateAccount(@RequestBody(required = false) Map<String, String> body) {
        if (body == null || body.get("email") == null || body.get("password") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
        }
        try {
            AuthResponse response = userService.reactivateAccount(body.get("email"), body.get("password"));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record AuthMeResponse(
            Long id, String email, String firstName, String lastName, String membershipType, String role,
            String subscriptionStatus, String subscriptionPeriodEnd, Boolean cancelAtPeriodEnd
    ) {}
}
