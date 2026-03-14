package com.example.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock JwtService jwtService;
    @Mock UserDetailsServiceImpl userDetailsService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;

    @InjectMocks
    JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_noAuthHeader_passesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_nonBearerHeader_passesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_invalidToken_passesWithoutSettingAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token.here");
        when(jwtService.validateToken("invalid.token.here")).thenReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_validToken_setsSecurityContextAuthentication() throws Exception {
        UserDetails userDetails = new User(
                "user@example.com", "pass",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        when(request.getHeader("Authorization")).thenReturn("Bearer valid.token.here");
        when(jwtService.validateToken("valid.token.here")).thenReturn(true);
        when(jwtService.getEmailFromToken("valid.token.here")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("user@example.com");
    }

    @Test
    void doFilterInternal_validTokenButUserNotFound_passesWithoutSettingAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.token.here");
        when(jwtService.validateToken("valid.token.here")).thenReturn(true);
        when(jwtService.getEmailFromToken("valid.token.here")).thenReturn("deleted@example.com");
        when(userDetailsService.loadUserByUsername("deleted@example.com"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_validAdminToken_setsAdminAuthority() throws Exception {
        UserDetails adminDetails = new User(
                "admin@example.com", "pass",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        when(request.getHeader("Authorization")).thenReturn("Bearer admin.token.here");
        when(jwtService.validateToken("admin.token.here")).thenReturn(true);
        when(jwtService.getEmailFromToken("admin.token.here")).thenReturn("admin@example.com");
        when(userDetailsService.loadUserByUsername("admin@example.com")).thenReturn(adminDetails);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
