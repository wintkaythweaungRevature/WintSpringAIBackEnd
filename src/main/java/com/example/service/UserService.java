package com.example.service;

import com.example.dto.AuthRequest;
import com.example.dto.AuthResponse;
import com.example.dto.RegisterRequest;
import com.example.entity.Subscription;
import com.example.entity.Subscription.PlanType;
import com.example.entity.User;
import com.example.repository.SubscriptionRepository;
import com.example.repository.UserRepository;
import com.example.service.EmailVerificationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;

    public UserService(UserRepository userRepo, SubscriptionRepository subscriptionRepo,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       EmailVerificationService emailVerificationService) {
        this.userRepo = userRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
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

        emailVerificationService.sendVerificationEmail(user);

        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return new AuthResponse(token, user.getEmail(), user.getMembershipType(), user.getId(), user.isEmailVerified());
    }

    public AuthResponse login(AuthRequest req) {
        User user = userRepo.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        ensureUserHasActiveSubscription(user);
        String membershipType = user.getMembershipType() != null ? user.getMembershipType() : "FREE";
        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return new AuthResponse(token, user.getEmail(), membershipType, user.getId(), user.isEmailVerified());
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

    /** Ensures user has an active FREE subscription (for legacy users created before subscriptions). */
    private void ensureUserHasActiveSubscription(User user) {
        boolean hasActive = subscriptionRepo.findTopByUserAndStatusOrderByCurrentPeriodEndDesc(user, "active").isPresent();
        if (!hasActive) {
            Subscription sub = new Subscription();
            sub.setUser(user);
            sub.setPlanType(PlanType.FREE);
            sub.setStatus("active");
            sub.setCurrentPeriodStart(java.time.LocalDateTime.now());
            sub.setCurrentPeriodEnd(java.time.LocalDateTime.now().plusYears(100));
            subscriptionRepo.save(sub);
            if (user.getMembershipType() == null) {
                user.setMembershipType("FREE");
                userRepo.save(user);
            }
        }
    }
}
