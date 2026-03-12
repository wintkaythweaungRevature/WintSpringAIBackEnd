package com.example.service;

import com.example.dto.AuthRequest;
import com.example.dto.AuthResponse;
import com.example.dto.RegisterRequest;
import com.example.entity.Subscription;
import com.example.entity.Subscription.PlanType;
import com.example.entity.User;
import com.example.repository.SubscriptionRepository;
import com.example.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepo, SubscriptionRepository subscriptionRepo,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepo = userRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User(
                req.getEmail(),
                passwordEncoder.encode(req.getPassword()),
                req.getFirstName(),
                req.getLastName()
        );
        user.setMembershipType("FREE");
        user = userRepo.save(user);

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setPlanType(PlanType.FREE);
        sub.setStatus("active");
        sub.setCurrentPeriodStart(java.time.LocalDateTime.now());
        sub.setCurrentPeriodEnd(java.time.LocalDateTime.now().plusYears(100));
        subscriptionRepo.save(sub);

        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return new AuthResponse(token, user.getEmail(), user.getMembershipType(), user.getId());
    }

    public AuthResponse login(AuthRequest req) {
        User user = userRepo.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (user.getPassword() == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        ensureUserHasRoleAndSubscription(user);
        user = userRepo.findById(user.getId()).orElse(user);
        String membershipType = user.getMembershipType() != null ? user.getMembershipType() : "FREE";
        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return new AuthResponse(token, user.getEmail(), membershipType, user.getId());
    }

    public User findById(Long id) {
        return userRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public User findByEmail(String email) {
        return userRepo.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public void updateMembership(Long userId, String membershipType) {
        User user = findById(userId);
        user.setMembershipType(membershipType);
        userRepo.save(user);
    }

    public void setStripeCustomerId(Long userId, String stripeCustomerId) {
        User user = findById(userId);
        user.setStripeCustomerId(stripeCustomerId);
        userRepo.save(user);
    }

    /** Returns the user's active paid subscription (MEMBER), if any. */
    public Optional<Subscription> getActiveMemberSubscription(User user) {
        return subscriptionRepo.findTopByUserAndStatusOrderByCurrentPeriodEndDesc(user, "active")
                .filter(sub -> sub.getPlanType() == PlanType.MEMBER);
    }

    /**
     * True only if the user has an active paid (MEMBER) subscription and the current period has not ended.
     * If the period end date has passed, this syncs the user to FREE and returns false so member-only features are blocked.
     */
    public boolean hasActivePaidAccess(User user) {
        Optional<Subscription> opt = getActiveMemberSubscription(user);
        if (opt.isEmpty()) {
            return false;
        }
        Subscription sub = opt.get();
        LocalDateTime periodEnd = sub.getCurrentPeriodEnd();
        if (periodEnd == null) {
            return true;
        }
        if (periodEnd.isBefore(LocalDateTime.now())) {
            syncExpiredSubscription(user, sub);
            return false;
        }
        return true;
    }

    /** Marks subscription as expired and sets user membership to FREE when period end has passed. */
    @Transactional
    public void syncExpiredSubscription(User user, Subscription sub) {
        sub.setStatus("expired");
        subscriptionRepo.save(sub);
        updateMembership(user.getId(), "FREE");
    }

    /** Ensures user has a role and at least one active subscription (for legacy/old members). */
    @Transactional
    public void ensureUserHasRoleAndSubscription(User user) {
        boolean changed = false;
        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("ROLE_USER");
            changed = true;
        }
        if (user.getMembershipType() == null || user.getMembershipType().isBlank()) {
            user.setMembershipType("FREE");
            changed = true;
        }
        if (changed) {
            userRepo.save(user);
        }
        boolean hasActive = subscriptionRepo.findTopByUserAndStatusOrderByCurrentPeriodEndDesc(user, "active").isPresent();
        if (!hasActive) {
            Subscription sub = new Subscription();
            sub.setUser(user);
            sub.setPlanType(PlanType.FREE);
            sub.setStatus("active");
            sub.setCurrentPeriodStart(java.time.LocalDateTime.now());
            sub.setCurrentPeriodEnd(java.time.LocalDateTime.now().plusYears(100));
            subscriptionRepo.save(sub);
            if (user.getMembershipType() == null || user.getMembershipType().isBlank()) {
                user.setMembershipType("FREE");
                userRepo.save(user);
            }
        }
    }
}
