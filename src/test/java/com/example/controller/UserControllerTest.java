package com.example.controller;

import com.example.Controller.UserController;
import com.example.entity.User;
import com.example.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = UserController.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean UserService userService;
    @MockBean com.example.service.JwtService jwtService;
    @MockBean com.example.service.UserDetailsServiceImpl userDetailsService;

    @Test
    void getProfile_returnsUserProfile() throws Exception {
        User user = new User("user@example.com", "pass", "Test", "User");
        user.setId(1L);
        user.setMembershipType("FREE");

        when(userService.findByEmail("user@example.com")).thenReturn(user);

        mockMvc.perform(get("/api/user/me")
                .with(SecurityMockMvcRequestPostProcessors.user("user@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.membershipType").value("FREE"));
    }

    @Test
    void getProfile_returnsMembershipType() throws Exception {
        User user = new User("member@example.com", "pass", "Member", "User");
        user.setId(2L);
        user.setMembershipType("MEMBER");

        when(userService.findByEmail("member@example.com")).thenReturn(user);

        mockMvc.perform(get("/api/user/me")
                .with(SecurityMockMvcRequestPostProcessors.user("member@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipType").value("MEMBER"));
    }

    @Test
    void getProfile_callsUserServiceWithPrincipalEmail() throws Exception {
        User user = new User("user@example.com", "pass", "Test", "User");
        user.setId(1L);
        user.setMembershipType("FREE");

        when(userService.findByEmail("user@example.com")).thenReturn(user);

        mockMvc.perform(get("/api/user/me")
                .with(SecurityMockMvcRequestPostProcessors.user("user@example.com")))
                .andExpect(status().isOk());

        verify(userService).findByEmail("user@example.com");
    }

    @Test
    void getProfile_doesNotExposePassword() throws Exception {
        User user = new User("user@example.com", "secret-hashed-pass", "Test", "User");
        user.setId(1L);
        user.setMembershipType("FREE");

        when(userService.findByEmail("user@example.com")).thenReturn(user);

        mockMvc.perform(get("/api/user/me")
                .with(SecurityMockMvcRequestPostProcessors.user("user@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
