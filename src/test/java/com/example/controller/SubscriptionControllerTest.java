package com.example.controller;

import com.example.service.StripeService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = SubscriptionController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class SubscriptionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean StripeService stripeService;
    @MockBean UserService userService;

    // ─── Unauthenticated access (null principal → controller returns 401) ────

    @Test
    void getStatus_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/subscription/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void checkout_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/subscription/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("plan", "MEMBER"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void cancel_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/subscription/cancel"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void reactivate_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/subscription/reactivate"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void invoices_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/subscription/invoices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void portal_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/subscription/portal"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void verifySession_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/subscription/verify-session")
                        .param("session_id", "cs_test_fake"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }
}
