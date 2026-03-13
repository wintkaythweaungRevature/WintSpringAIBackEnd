package com.example.Config;

import com.example.entity.Subscription;
import com.example.entity.Subscription.PlanType;
import com.example.entity.User;
import com.example.repository.SubscriptionRepository;
import com.example.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminDataLoader(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail("wint").isEmpty()) {
            User admin = new User("wint", passwordEncoder.encode("wint"), "Admin", "User");
            admin.setRole("ROLE_ADMIN");
            admin.setMembershipType("MEMBER");
            admin = userRepository.save(admin);

            Subscription sub = new Subscription();
            sub.setUser(admin);
            sub.setPlanType(PlanType.MEMBER);
            sub.setStatus("active");
            sub.setCurrentPeriodStart(java.time.LocalDateTime.now());
            sub.setCurrentPeriodEnd(java.time.LocalDateTime.now().plusYears(100));
            subscriptionRepository.save(sub);
        }
    }
}
