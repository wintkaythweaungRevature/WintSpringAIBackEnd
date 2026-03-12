package com.example.service;

import com.example.entity.EmailVerificationToken;
import com.example.entity.User;
import com.example.repository.EmailVerificationTokenRepository;
import com.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final JavaMailSender mailSender;
    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Value("${app.base-url:https://wintaibot.com}")
    private String baseUrl;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    private static final int TOKEN_BYTES = 32;
    private static final int EXPIRY_HOURS = 24;

    public EmailVerificationService(JavaMailSender mailSender,
                                    EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository) {
        this.mailSender = mailSender;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void sendVerificationEmail(User user) {
        String token = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(EXPIRY_HOURS);

        EmailVerificationToken verificationToken = new EmailVerificationToken(token, user, expiresAt);
        tokenRepository.save(verificationToken);

        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String verificationLink = base + "/verify-email.html?token=" + token;

        if (!mailEnabled) {
            log.info("Mail disabled: verification link for {} -> {}", user.getEmail(), verificationLink);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Verify your email - WintAI");
        message.setText("Hello " + (user.getFirstName() != null ? user.getFirstName() : "there") + ",\n\n"
                + "Please click the link below to verify your email address:\n\n"
                + verificationLink + "\n\n"
                + "This link will expire in 24 hours.\n\n"
                + "If you didn't create an account, you can ignore this email.\n\n"
                + "Best regards,\nWintAI Team");
        mailSender.send(message);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Email is already verified");
        }
        tokenRepository.findByUser(user).forEach(tokenRepository::delete);
        sendVerificationEmail(user);
    }

    public boolean verifyEmail(String token) {
        return tokenRepository.findByToken(token)
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(t -> {
                    User user = t.getUser();
                    user.setEmailVerified(true);
                    userRepository.save(user);
                    tokenRepository.delete(t);
                    return true;
                })
                .orElse(false);
    }

    private String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
