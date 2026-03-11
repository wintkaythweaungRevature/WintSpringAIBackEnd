package com.example.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.Repo.SubscriptionRepository;
import com.example.Repo.UserRepository;
import com.example.User.Subscription;
import com.example.User.Subscription.PlanType;
import com.example.User.User;

import jakarta.annotation.PostConstruct;

@Service
public class StripeService {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserService userService;

    @Value("${stripe.price.basic:price_basic_monthly}")
    private String basicPriceId;

    @Value("${stripe.price.pro:price_pro_monthly}")
    private String proPriceId;

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String priceId = switch (planType.toUpperCase()) {
            case "BASIC" -> basicPriceId;
            case "PRO" -> proPriceId;
            default -> throw new IllegalArgumentException("Invalid plan: " + planType);
        };

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
                        .putMetadata("planType", planType)
                        .build())
                .putMetadata("userId", userId.toString())
                .putMetadata("planType", planType);

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
        String planType = stripeSub.getMetadata() != null ? stripeSub.getMetadata().getOrDefault("planType", "BASIC") : "BASIC";
        sub.setPlanType(PlanType.valueOf(planType));
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
