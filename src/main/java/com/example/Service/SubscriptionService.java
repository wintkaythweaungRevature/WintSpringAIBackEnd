package com.example.Service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.Repo.SubscriptionRepository;
import com.example.User.Subscription;
import com.example.User.Subscription.PlanType;
import com.example.User.User;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserService userService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, UserService userService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userService = userService;
    }

    public boolean canUseAiTools(Long userId) {
        User user = userService.findById(userId);
        return !"FREE".equals(user.getMembershipType()) || hasActiveFreeSubscription(user);
    }

    public boolean hasActiveFreeSubscription(User user) {
        return subscriptionRepository.findTopByUserAndStatusOrderByCurrentPeriodEndDesc(user, "active")
                .map(Subscription::getPlanType)
                .map(p -> p == PlanType.FREE)
                .orElse(true);
    }

    public Optional<Subscription> getActiveSubscription(Long userId) {
        User user = userService.findById(userId);
        return subscriptionRepository.findTopByUserAndStatusOrderByCurrentPeriodEndDesc(user, "active");
    }

    public PlanType getPlanType(Long userId) {
        User user = userService.findById(userId);
        return subscriptionRepository.findTopByUserAndStatusOrderByCurrentPeriodEndDesc(user, "active")
                .map(Subscription::getPlanType)
                .orElse(PlanType.FREE);
    }
}
