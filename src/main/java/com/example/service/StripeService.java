package com.example.service;

import com.example.entity.Subscription;
import com.example.entity.Subscription.PlanType;
import com.example.entity.User;
import com.example.repository.SubscriptionRepository;
import com.example.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Service
public class StripeService {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Value("${stripe.price.member:price_1T9wNI64XP2DAi8NUBtSQg02}")
    private String memberPriceId;

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserService userService;

    public StripeService(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
                         UserService userService) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userService = userService;
    }

    @PostConstruct
    public void init() {
        com.stripe.Stripe.apiKey = stripeApiKey;
    }

    public Map<String, String> createCheckoutSession(Long userId, String planType) throws Exception {
        if (stripeApiKey == null || stripeApiKey.isBlank() || stripeApiKey.contains("your_stripe_key") || stripeApiKey.contains("sk_test_your")) {
            throw new IllegalArgumentException("Stripe is not configured. Set STRIPE_SECRET_KEY in production.");
        }
        if (memberPriceId == null || memberPriceId.isBlank() || !memberPriceId.startsWith("price_")) {
            throw new IllegalArgumentException("Invalid Stripe price. Set STRIPE_PRICE_MEMBER (e.g. price_1T9wNI64XP2DAi8NUBtSQg02) in production.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String priceId = memberPriceId;

        var paramsBuilder = com.stripe.param.checkout.SessionCreateParams.builder()
                .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl("https://wintaibot.com/subscription/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl("https://wintaibot.com/subscription/cancel")
                .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .setSubscriptionData(com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
                        .putMetadata("userId", userId.toString())
                        .putMetadata("planType", "MEMBER")
                        .build())
                .putMetadata("userId", userId.toString())
                .putMetadata("planType", "MEMBER");

        if (user.getStripeCustomerId() != null) {
            paramsBuilder.setCustomer(user.getStripeCustomerId());
        } else {
            paramsBuilder.setCustomerEmail(user.getEmail());
        }

        var session = com.stripe.model.checkout.Session.create(paramsBuilder.build());

        Map<String, String> result = new HashMap<>();
        result.put("sessionId", session.getId());
        result.put("url", session.getUrl());
        return result;
    }

    /** Cancels the user's subscription at period end. They keep access until then; webhook will set FREE when it ends. */
    public void cancelSubscriptionAtPeriodEnd(Long userId) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        var memberSub = subscriptionRepository.findTopByUserAndStatusOrderByCurrentPeriodEndDesc(user, "active")
                .filter(s -> s.getPlanType() == PlanType.MEMBER && s.getStripeSubscriptionId() != null);
        if (memberSub.isEmpty()) {
            throw new IllegalArgumentException("No active subscription to cancel");
        }
        Subscription sub = memberSub.get();
        String stripeSubId = sub.getStripeSubscriptionId();
        var params = com.stripe.param.SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build();
        com.stripe.model.Subscription.retrieve(stripeSubId).update(params);
        sub.setCancelAtPeriodEnd(true);
        subscriptionRepository.save(sub);
    }

    /** Reactivates a subscription that was set to cancel at period end (undo cancellation). */
    public void reactivateSubscription(Long userId) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        var memberSub = subscriptionRepository.findTopByUserAndStatusOrderByCurrentPeriodEndDesc(user, "active")
                .filter(s -> s.getPlanType() == PlanType.MEMBER && s.getStripeSubscriptionId() != null && s.isCancelAtPeriodEnd());
        if (memberSub.isEmpty()) {
            throw new IllegalArgumentException("No cancelled subscription to reactivate");
        }
        Subscription sub = memberSub.get();
        String stripeSubId = sub.getStripeSubscriptionId();
        var params = com.stripe.param.SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(false)
                .build();
        com.stripe.model.Subscription.retrieve(stripeSubId).update(params);
        sub.setCancelAtPeriodEnd(false);
        subscriptionRepository.save(sub);
    }

    /** Verifies a Stripe checkout session and upgrades user if the payment completed. Returns true if user was upgraded. */
    public boolean verifyCheckoutSession(Long userId, String sessionId) throws Exception {
        var session = com.stripe.model.checkout.Session.retrieve(sessionId);
        if (!"complete".equals(session.getStatus()) && !"paid".equals(session.getPaymentStatus())) {
            return false;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (session.getCustomer() != null && (user.getStripeCustomerId() == null || user.getStripeCustomerId().isBlank())) {
            userService.setStripeCustomerId(userId, session.getCustomer());
            user = userRepository.findById(userId).orElse(user);
        }
        if (!"MEMBER".equals(user.getMembershipType())) {
            userService.updateMembership(userId, "MEMBER");
            return true;
        }
        return false;
    }

    public Map<String, String> createCustomerPortalSession(Long userId) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getStripeCustomerId() == null) {
            throw new IllegalArgumentException("No subscription found");
        }

        var params = com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(user.getStripeCustomerId())
                .setReturnUrl("https://wintaibot.com/subscription")
                .build();

        var session = com.stripe.model.billingportal.Session.create(params);

        Map<String, String> result = new HashMap<>();
        result.put("url", session.getUrl());
        return result;
    }

    public void handleWebhook(String payload, String sigHeader) throws Exception {
        var event = com.stripe.net.Webhook.constructEvent(payload, sigHeader, webhookSecret);

        switch (event.getType()) {
            case "customer.subscription.created":
            case "customer.subscription.updated":
                handleSubscriptionUpdate(event);
                break;
            case "customer.subscription.deleted":
                handleSubscriptionDeleted(event);
                break;
            case "checkout.session.completed":
                handleCheckoutCompleted(event);
                break;
            default:
                break;
        }
    }

    private com.stripe.model.Subscription getSubscriptionFromEvent(com.stripe.model.Event event) throws com.stripe.exception.StripeException {
        var deserializer = event.getDataObjectDeserializer();
        var obj = deserializer.getObject().orElseGet(() -> {
            try {
                return deserializer.deserializeUnsafe();
            } catch (com.stripe.exception.EventDataObjectDeserializationException e) {
                throw new RuntimeException(e);
            }
        });
        return (com.stripe.model.Subscription) obj;
    }

    private com.stripe.model.checkout.Session getSessionFromEvent(com.stripe.model.Event event) throws com.stripe.exception.StripeException {
        var deserializer = event.getDataObjectDeserializer();
        var obj = deserializer.getObject().orElseGet(() -> {
            try {
                return deserializer.deserializeUnsafe();
            } catch (com.stripe.exception.EventDataObjectDeserializationException e) {
                throw new RuntimeException(e);
            }
        });
        return (com.stripe.model.checkout.Session) obj;
    }

    private void handleSubscriptionUpdate(com.stripe.model.Event event) throws com.stripe.exception.StripeException {
        var stripeSub = getSubscriptionFromEvent(event);

        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId()).ifPresentOrElse(
                sub -> {
                    sub.setStatus(stripeSub.getStatus());
                    sub.setCurrentPeriodStart(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()).atZone(ZoneId.systemDefault()).toLocalDateTime());
                    sub.setCurrentPeriodEnd(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()).atZone(ZoneId.systemDefault()).toLocalDateTime());
                    sub.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd()));
                    subscriptionRepository.save(sub);
                    userService.updateMembership(sub.getUser().getId(), sub.getPlanType().name());
                },
                () -> createNewSubscription(stripeSub)
        );
    }

    private void createNewSubscription(com.stripe.model.Subscription stripeSub) {
        var meta = stripeSub.getMetadata();
        String userId = meta != null ? meta.get("userId") : null;
        if (userId == null && stripeSub.getCustomer() != null) {
            try {
                var cust = com.stripe.model.Customer.retrieve(stripeSub.getCustomer());
                var user = userRepository.findByEmail(cust.getEmail()).orElse(null);
                if (user != null) {
                    saveSubscription(user, stripeSub);
                }
            } catch (com.stripe.exception.StripeException ignored) {
            }
        } else if (userId != null) {
            userRepository.findById(Long.parseLong(userId)).ifPresent(user -> saveSubscription(user, stripeSub));
        }
    }

    private void saveSubscription(User user, com.stripe.model.Subscription stripeSub) {
        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setStripeSubscriptionId(stripeSub.getId());
        sub.setStatus(stripeSub.getStatus());
        sub.setCurrentPeriodStart(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        sub.setCurrentPeriodEnd(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()).atZone(ZoneId.systemDefault()).toLocalDateTime());
        String planType = stripeSub.getMetadata() != null ? stripeSub.getMetadata().getOrDefault("planType", "MEMBER") : "MEMBER";
        sub.setPlanType("MEMBER".equalsIgnoreCase(planType) ? PlanType.MEMBER : PlanType.MEMBER);
        subscriptionRepository.save(sub);
        userService.updateMembership(user.getId(), sub.getPlanType().name());
    }

    private void handleSubscriptionDeleted(com.stripe.model.Event event) throws com.stripe.exception.StripeException {
        var stripeSub = getSubscriptionFromEvent(event);

        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId()).ifPresent(sub -> {
            sub.setStatus("canceled");
            subscriptionRepository.save(sub);
            userService.updateMembership(sub.getUser().getId(), "FREE");
        });
    }

    private void handleCheckoutCompleted(com.stripe.model.Event event) throws com.stripe.exception.StripeException {
        var session = getSessionFromEvent(event);

        var meta = session.getMetadata();
        if (meta != null) {
            String userIdStr = meta.get("userId");
            if (userIdStr != null && session.getCustomer() != null) {
                userService.setStripeCustomerId(Long.parseLong(userIdStr), session.getCustomer());
            }
        }
    }
}
