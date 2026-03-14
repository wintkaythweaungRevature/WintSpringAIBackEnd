package com.example.controller;

import com.example.Controller.StripeWebhookController;
import com.example.service.StripeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = StripeWebhookController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class StripeWebhookControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean StripeService stripeService;
    @MockBean com.example.service.JwtService jwtService;
    @MockBean com.example.service.UserDetailsServiceImpl userDetailsService;

    @Test
    void handleWebhook_validPayload_returns200OK() throws Exception {
        doNothing().when(stripeService).handleWebhook(anyString(), anyString());

        mockMvc.perform(post("/api/webhook/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .content("{\"type\":\"checkout.session.completed\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void handleWebhook_invalidSignature_returns400() throws Exception {
        doThrow(new RuntimeException("Webhook signature verification failed"))
                .when(stripeService).handleWebhook(anyString(), anyString());

        mockMvc.perform(post("/api/webhook/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "invalid-sig")
                        .content("{\"type\":\"checkout.session.completed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Webhook signature verification failed"));
    }

    @Test
    void handleWebhook_callsStripeServiceWithPayloadAndHeader() throws Exception {
        String payload = "{\"type\":\"invoice.paid\"}";
        String sigHeader = "t=456,v1=xyz";

        doNothing().when(stripeService).handleWebhook(anyString(), anyString());

        mockMvc.perform(post("/api/webhook/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", sigHeader)
                        .content(payload))
                .andExpect(status().isOk());

        verify(stripeService).handleWebhook(payload, sigHeader);
    }

    @Test
    void handleWebhook_serviceThrowsGenericException_returns400WithMessage() throws Exception {
        doThrow(new Exception("Unexpected error"))
                .when(stripeService).handleWebhook(anyString(), anyString());

        mockMvc.perform(post("/api/webhook/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=abc")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Unexpected error"));
    }
}
