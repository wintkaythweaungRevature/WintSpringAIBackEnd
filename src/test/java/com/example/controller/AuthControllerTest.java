package com.example.controller;

import com.example.Controller.AuthController;
import com.example.dto.AuthResponse;
import com.example.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean com.example.service.JwtService jwtService;

    // ─── POST /api/auth/register ─────────────────────────────────────────────

    @Test
    void register_withValidBody_returns200() throws Exception {
        AuthResponse fake = new AuthResponse("tok", "user@example.com", "FREE", 1L);
        when(userService.register(any())).thenReturn(fake);

        Map<String, String> body = Map.of(
                "email", "user@example.com",
                "password", "pass123",
                "firstName", "Test",
                "lastName", "User"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.membershipType").value("FREE"));
    }

    @Test
    void register_withMissingEmail_returns400() throws Exception {
        Map<String, String> body = Map.of("password", "pass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void register_withMissingPassword_returns400() throws Exception {
        Map<String, String> body = Map.of("email", "user@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void register_whenEmailAlreadyExists_returns400() throws Exception {
        when(userService.register(any()))
                .thenThrow(new IllegalArgumentException("Email already registered"));

        Map<String, String> body = Map.of("email", "dup@example.com", "password", "pass");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    // ─── POST /api/auth/login ────────────────────────────────────────────────

    @Test
    void login_withValidCredentials_returns200() throws Exception {
        AuthResponse fake = new AuthResponse("login-tok", "user@example.com", "FREE", 1L);
        when(userService.login(any())).thenReturn(fake);

        Map<String, String> body = Map.of("email", "user@example.com", "password", "pass123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("login-tok"));
    }

    @Test
    void login_withMissingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void login_withInvalidCredentials_returns400() throws Exception {
        when(userService.login(any()))
                .thenThrow(new IllegalArgumentException("Invalid email or password"));

        Map<String, String> body = Map.of("email", "user@example.com", "password", "wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    // ─── GET /api/auth/me (no auth → null principal → 401 from controller) ──

    @Test
    void me_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /api/auth/reactivate ───────────────────────────────────────────

    @Test
    void reactivate_withValidBody_returns200() throws Exception {
        AuthResponse fake = new AuthResponse("re-tok", "user@example.com", "FREE", 1L);
        when(userService.reactivateAccount("user@example.com", "pass123")).thenReturn(fake);

        Map<String, String> body = Map.of("email", "user@example.com", "password", "pass123");

        mockMvc.perform(post("/api/auth/reactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("re-tok"));
    }

    @Test
    void reactivate_withMissingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/reactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ─── POST /api/auth/deactivate (no auth → 401 from controller) ──────────

    @Test
    void deactivate_withoutAuth_returns401() throws Exception {
        Map<String, String> body = Map.of("password", "pass123");

        mockMvc.perform(post("/api/auth/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }
}
