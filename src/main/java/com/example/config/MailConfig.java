package com.example.config;

import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;

/**
 * When app.mail.enabled=false (default), provides a no-op mail sender so the app starts without SMTP.
 * When MAIL_ENABLED=true, MailSenderAutoConfiguration provides the real JavaMailSender.
 */
@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
    public JavaMailSender noOpMailSender() {
        return new JavaMailSender() {
            @Override
            public void send(SimpleMailMessage simpleMessage) throws MailException { /* no-op */ }
            @Override
            public void send(SimpleMailMessage... simpleMessages) throws MailException { /* no-op */ }
            @Override
            public MimeMessage createMimeMessage() { return null; }
            @Override
            public MimeMessage createMimeMessage(InputStream contentStream) throws MailException { return null; }
            @Override
            public void send(MimeMessage mimeMessage) throws MailException { /* no-op */ }
            @Override
            public void send(MimeMessage... mimeMessages) throws MailException { /* no-op */ }
            @Override
            public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException { /* no-op */ }
            @Override
            public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException { /* no-op */ }
        };
    }
}
