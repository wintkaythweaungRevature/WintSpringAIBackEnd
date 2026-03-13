package com.example.Controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.entity.Subscription;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    /** Returns subscription status for the current user: active, plan, period end, cancelAtPeriodEnd. */
    @GetMapping("/status")
    public ResponseEntity<?> getSubscriptionStatus(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            userService.hasActivePaidAccess(user);
            user = userService.findById(user.getId());
            Optional<Subscription> memberSub = userService.getActiveMemberSubscription(user);
            Map<String, Object> status = new HashMap<>();
            status.put("active", userService.hasActivePaidAccess(user));
            status.put("plan", user.getMembershipType() != null ? user.getMembershipType() : "FREE");
            status.put("subscriptionStatus", null);
            status.put("subscriptionPeriodEnd", null);
            status.put("cancelAtPeriodEnd", false);
            if (memberSub.isPresent()) {
                Subscription sub = memberSub.get();
                status.put("subscriptionStatus", sub.getStatus() != null ? sub.getStatus() : "active");
                if (sub.getCurrentPeriodEnd() != null) {
                    status.put("subscriptionPeriodEnd", sub.getCurrentPeriodEnd().toLocalDate().toString());
                }
                status.put("cancelAtPeriodEnd", sub.isCancelAtPeriodEnd());
            }
            status.put("canManageInvoices", user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank());
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).build();
        }
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

    /** Cancel subscription at period end. User keeps access until then; then webhook sets them to FREE. */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelSubscription(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            stripeService.cancelSubscriptionAtPeriodEnd(user.getId());
            return ResponseEntity.ok(Map.of("message", "Subscription will cancel at the end of the current period. You can use member features until then."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Cancel subscription failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Could not cancel subscription. Try the billing portal or contact support."));
        }
    }

    /** Reactivate a subscription that was set to cancel at period end (undo cancellation). */
    @PostMapping("/reactivate")
    public ResponseEntity<?> reactivateSubscription(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            stripeService.reactivateSubscription(user.getId());
            return ResponseEntity.ok(Map.of("message", "Subscription reactivated. Your membership will continue after the current period."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Reactivate subscription failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Could not reactivate subscription. Try the billing portal or contact support."));
        }
    }

    /** Verify a Stripe checkout session and upgrade user if payment completed. */
    @GetMapping("/verify-session")
    public ResponseEntity<?> verifySession(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestParam("session_id") String sessionId) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            boolean upgraded = stripeService.verifyCheckoutSession(user.getId(), sessionId);
            return ResponseEntity.ok(Map.of("upgraded", upgraded));
        } catch (Exception e) {
            log.error("Session verification failed", e);
            return ResponseEntity.ok(Map.of("upgraded", false));
        }
    }

    /** Returns the last 10 invoices for the current user from Stripe. */
    @GetMapping("/invoices")
    public ResponseEntity<?> getInvoices(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            var invoices = stripeService.getInvoices(user.getId());
            return ResponseEntity.ok(invoices);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch invoices", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Could not retrieve invoices."));
        }
    }

    /** Returns a one-time URL to Stripe Customer Portal (invoices, update payment, cancel). */
    @GetMapping("/portal")
    public ResponseEntity<?> createPortalSession(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            User user = userService.findByEmail(userDetails.getUsername());
            Map<String, String> session = stripeService.createCustomerPortalSession(user.getId());
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Portal session failed: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "Could not open billing portal.";
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }
}
