package com.example.repository;

import com.example.entity.Subscription;
import com.example.entity.User;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserOrderByCreatedAtDesc(User user);
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    Optional<Subscription> findTopByUserAndStatusOrderByCurrentPeriodEndDesc(User user, String status);
}
