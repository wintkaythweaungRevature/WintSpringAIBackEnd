package com.example.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET =
            "test-jwt-secret-key-must-be-at-least-32-chars-long-for-hmac";
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", EXPIRATION_MS);
    }

    @Test
    void generateToken_returnsNonBlankToken() {
        String token = jwtService.generateToken("user@example.com", 42L);
        assertThat(token).isNotBlank();
    }

    @Test
    void getEmailFromToken_returnsCorrectEmail() {
        String token = jwtService.generateToken("user@example.com", 42L);
        assertThat(jwtService.getEmailFromToken(token)).isEqualTo("user@example.com");
    }

    @Test
    void getUserIdFromToken_returnsCorrectId() {
        String token = jwtService.generateToken("user@example.com", 99L);
        assertThat(jwtService.getUserIdFromToken(token)).isEqualTo(99L);
    }

    @Test
    void validateToken_returnsTrueForValidToken() {
        String token = jwtService.generateToken("valid@example.com", 1L);
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalseForGarbage() {
        assertThat(jwtService.validateToken("not.a.real.token")).isFalse();
    }

    @Test
    void validateToken_returnsFalseForEmptyString() {
        assertThat(jwtService.validateToken("")).isFalse();
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() {
        // Generate token with -1ms expiration (already expired)
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1L);
        String expiredToken = jwtService.generateToken("exp@example.com", 5L);
        assertThat(jwtService.validateToken(expiredToken)).isFalse();
    }

    @Test
    void generateToken_differentUsersProduceDifferentTokens() {
        String token1 = jwtService.generateToken("a@example.com", 1L);
        String token2 = jwtService.generateToken("b@example.com", 2L);
        assertThat(token1).isNotEqualTo(token2);
    }
}
