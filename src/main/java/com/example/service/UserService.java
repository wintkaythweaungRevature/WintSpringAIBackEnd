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
        validatePassword(req.getPassword());
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
        if (!user.isActive()) {
            throw new IllegalArgumentException("Account is deactivated. Please reactivate your account to continue.");
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

    /** Owner account: full access to all features. */
    private static final String OWNER_EMAIL = "wint@gmail.com";

    /**
     * True only if the user has an active paid (MEMBER) subscription and the current period has not ended.
     * If the period end date has passed, this syncs the user to FREE and returns false so member-only features are blocked.
     * Owner (wint@gmail.com) always has full access.
     */
    public boolean hasActivePaidAccess(User user) {
        if (user != null && OWNER_EMAIL.equalsIgnoreCase(user.getEmail())) {
            return true;
        }
        // Check subscription record first
        Optional<Subscription> opt = getActiveMemberSubscription(user);
        if (opt.isPresent()) {
            Subscription sub = opt.get();
            LocalDateTime periodEnd = sub.getCurrentPeriodEnd();
            if (periodEnd == null) return true;
            if (periodEnd.isBefore(LocalDateTime.now())) {
                syncExpiredSubscription(user, sub);
                return false;
            }
            return true;
        }
        // Fallback: trust the membershipType field (set by admin or webhook)
        return "MEMBER".equals(user.getMembershipType());
    }

    /** Marks subscription as expired and sets user membership to FREE when period end has passed. */
    @Transactional
    public void syncExpiredSubscription(User user, Subscription sub) {
        sub.setStatus("expired");
        subscriptionRepo.save(sub);
        updateMembership(user.getId(), "FREE");
    }

    /** Deactivates the user account after verifying password. */
    @Transactional
    public void deactivateAccount(Long userId, String password) {
        User user = findById(userId);
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid password");
        }
        user.setActive(false);
        user.setDeactivatedAt(LocalDateTime.now());
        userRepo.save(user);
    }

    /** Reactivates a previously deactivated account after verifying credentials. */
    @Transactional
    public AuthResponse reactivateAccount(String email, String password) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        if (user.isActive()) {
            throw new IllegalArgumentException("Account is already active. Please login normally.");
        }
        user.setActive(true);
        user.setDeactivatedAt(null);
        userRepo.save(user);
        ensureUserHasRoleAndSubscription(user);
        user = userRepo.findById(user.getId()).orElse(user);
        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return new AuthResponse(token, user.getEmail(), user.getMembershipType() != null ? user.getMembershipType() : "FREE", user.getId());
    }

    /** Admin: list all users with basic info. */
    public java.util.List<User> getAllUsers() {
        return userRepo.findAll();
    }

    /** Admin: activate a user account by id. */
    @Transactional
    public void adminActivateUser(Long userId) {
        User user = findById(userId);
        user.setActive(true);
        user.setDeactivatedAt(null);
        userRepo.save(user);
    }

    /** Admin: deactivate a user account by id. */
    @Transactional
    public void adminDeactivateUser(Long userId) {
        User user = findById(userId);
        user.setActive(false);
        user.setDeactivatedAt(java.time.LocalDateTime.now());
        userRepo.save(user);
    }

    /**
     * Validates password strength. Requires:
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     */
    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must contain at least one digit");
        }
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
