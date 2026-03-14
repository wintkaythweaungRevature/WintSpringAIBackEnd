package com.example.controller;

import com.example.Controller.AdminController;
import com.example.entity.User;
import com.example.service.StripeService;
import com.example.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AdminController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean StripeService stripeService;
    @MockBean com.example.service.JwtService jwtService;
    @MockBean com.example.service.UserDetailsServiceImpl userDetailsService;

    // ─── GET /api/admin/users ─────────────────────────────────────────────────

    @Test
    void listUsers_withoutAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Admin access required"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listUsers_withNonAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Admin access required"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listUsers_withAdminRole_returns200WithUserList() throws Exception {
        User u = new User("user@example.com", "pass", "Test", "User");
        u.setId(1L);
        u.setMembershipType("FREE");
        u.setRole("ROLE_USER");

        when(userService.getAllUsers()).thenReturn(List.of(u));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("user@example.com"))
                .andExpect(jsonPath("$[0].membershipType").value("FREE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listUsers_returnsEmptyList_whenNoUsers() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── POST /api/admin/users/{userId}/activate ──────────────────────────────

    @Test
    void activateUser_withoutAuth_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/activate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateUser_withAdmin_returns200() throws Exception {
        doNothing().when(userService).adminActivateUser(1L);

        mockMvc.perform(post("/api/admin/users/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "User account activated. Membership set to FREE — user must re-subscribe."))
                .andExpect(jsonPath("$.membershipType").value("FREE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateUser_userNotFound_returns400() throws Exception {
        doThrow(new IllegalArgumentException("User not found"))
                .when(userService).adminActivateUser(999L);

        mockMvc.perform(post("/api/admin/users/999/activate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    // ─── POST /api/admin/users/{userId}/membership ────────────────────────────

    @Test
    void setMembership_withoutAuth_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/membership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("membershipType", "MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setMembership_toMember_returns200() throws Exception {
        doNothing().when(userService).updateMembership(1L, "MEMBER");

        mockMvc.perform(post("/api/admin/users/1/membership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("membershipType", "MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipType").value("MEMBER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setMembership_toFree_returns200() throws Exception {
        doNothing().when(userService).updateMembership(1L, "FREE");

        mockMvc.perform(post("/api/admin/users/1/membership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("membershipType", "FREE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipType").value("FREE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setMembership_withInvalidType_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/membership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("membershipType", "PREMIUM"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("membershipType must be MEMBER or FREE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setMembership_withMissingType_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/membership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ─── POST /api/admin/users/{userId}/deactivate ────────────────────────────

    @Test
    void deactivateUser_withoutAuth_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/deactivate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateUser_freeMember_returns200() throws Exception {
        User user = new User("user@example.com", "pass", "Test", "User");
        user.setId(1L);
        user.setMembershipType("FREE");

        when(userService.findById(1L)).thenReturn(user);
        doNothing().when(userService).updateMembership(1L, "FREE");
        doNothing().when(userService).adminDeactivateUser(1L);

        mockMvc.perform(post("/api/admin/users/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User account deactivated and subscription cancelled."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateUser_memberWithStripe_cancelSubscriptionCalled() throws Exception {
        User user = new User("member@example.com", "pass", "Test", "User");
        user.setId(2L);
        user.setMembershipType("MEMBER");
        user.setStripeCustomerId("cus_test123");

        when(userService.findById(2L)).thenReturn(user);
        doNothing().when(stripeService).cancelSubscriptionAtPeriodEnd(2L);
        doNothing().when(userService).updateMembership(2L, "FREE");
        doNothing().when(userService).adminDeactivateUser(2L);

        mockMvc.perform(post("/api/admin/users/2/deactivate"))
                .andExpect(status().isOk());

        verify(stripeService).cancelSubscriptionAtPeriodEnd(2L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateUser_userNotFound_returns400() throws Exception {
        when(userService.findById(999L))
                .thenThrow(new IllegalArgumentException("User not found"));

        mockMvc.perform(post("/api/admin/users/999/deactivate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User not found"));
    }
}
