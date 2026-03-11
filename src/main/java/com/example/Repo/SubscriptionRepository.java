package com.example.Repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.User.Subscription;
import com.example.User.Subscription.PlanType;
import com.example.User.User;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserOrderByCreatedAtDesc(User user);
    Optional<Subscription> findByUserAndStatus(User user, String status);
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    Optional<Subscription> findTopByUserAndStatusOrderByCurrentPeriodEndDesc(User user, String status);
}
