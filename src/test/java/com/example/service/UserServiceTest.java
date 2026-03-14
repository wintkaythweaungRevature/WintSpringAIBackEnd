package com.example.service;

import com.example.dto.AuthRequest;
import com.example.dto.AuthResponse;
import com.example.dto.RegisterRequest;
import com.example.entity.Subscription;
import com.example.entity.User;
import com.example.repository.SubscriptionRepository;
import com.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepo;
    @Mock SubscriptionRepository subscriptionRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    @InjectMocks UserService userService;

    private RegisterRequest registerRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("Password123");
        registerRequest.setFirstName("Test");
        registerRequest.setLastName("User");

        savedUser = new User("test@example.com", "encoded-pass", "Test", "User");
        savedUser.setId(1L);
        savedUser.setMembershipType("FREE");
    }

    // ─── register ────────────────────────────────────────────────────────────

    @Test
    void register_success_returnsTokenAndEmail() {
        when(userRepo.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded-pass");
        when(userRepo.save(any(User.class))).thenReturn(savedUser);
        when(subscriptionRepo.save(any(Subscription.class))).thenReturn(new Subscription());
        when(jwtService.generateToken("test@example.com", 1L)).thenReturn("fake-token");

        AuthResponse response = userService.register(registerRequest);

        assertThat(response.getToken()).isEqualTo("fake-token");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getMembershipType()).isEqualTo("FREE");
    }

    @Test
    void register_throwsWhenEmailAlreadyExists() {
        when(userRepo.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already registered");
    }

    @Test
    void register_throwsWhenPasswordTooShort() {
        registerRequest.setPassword("Short1");

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must be at least 8 characters long");
    }

    @Test
    void register_throwsWhenPasswordMissingUppercase() {
        registerRequest.setPassword("password123");

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must contain at least one uppercase letter");
    }

    @Test
    void register_throwsWhenPasswordMissingDigit() {
        registerRequest.setPassword("PasswordOnly");

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must contain at least one digit");
    }

    @Test
    void register_savesUserWithEncodedPassword() {
        when(userRepo.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed-pw");
        when(userRepo.save(any(User.class))).thenReturn(savedUser);
        when(subscriptionRepo.save(any())).thenReturn(new Subscription());
        when(jwtService.generateToken(anyString(), anyLong())).thenReturn("tok");

        userService.register(registerRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed-pw");
    }

    // ─── login ────────────────────────────────────────────────────────────────

    @Test
    void login_successReturnsToken() {
        AuthRequest req = new AuthRequest();
        req.setEmail("test@example.com");
        req.setPassword("password123");

        savedUser.setActive(true);
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "encoded-pass")).thenReturn(true);
        when(userRepo.findById(1L)).thenReturn(Optional.of(savedUser));
        when(subscriptionRepo.findTopByUserAndStatusOrderByCurrentPeriodEndDesc(savedUser, "active"))
                .thenReturn(Optional.empty());
        when(jwtService.generateToken("test@example.com", 1L)).thenReturn("login-token");

        AuthResponse response = userService.login(req);
        assertThat(response.getToken()).isEqualTo("login-token");
    }

    @Test
    void login_throwsForUnknownEmail() {
        AuthRequest req = new AuthRequest();
        req.setEmail("nobody@example.com");
        req.setPassword("pass");

        when(userRepo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_throwsForWrongPassword() {
        AuthRequest req = new AuthRequest();
        req.setEmail("test@example.com");
        req.setPassword("wrong");

        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("wrong", "encoded-pass")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_throwsForDeactivatedAccount() {
        AuthRequest req = new AuthRequest();
        req.setEmail("test@example.com");
        req.setPassword("password123");

        savedUser.setActive(false);
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "encoded-pass")).thenReturn(true);

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deactivated");
    }

    // ─── deactivateAccount ───────────────────────────────────────────────────

    @Test
    void deactivateAccount_setsUserInactive() {
        savedUser.setActive(true);
        when(userRepo.findById(1L)).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "encoded-pass")).thenReturn(true);

        userService.deactivateAccount(1L, "password123");

        assertThat(savedUser.isActive()).isFalse();
        assertThat(savedUser.getDeactivatedAt()).isNotNull();
        verify(userRepo).save(savedUser);
    }

    @Test
    void deactivateAccount_throwsForWrongPassword() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("wrong", "encoded-pass")).thenReturn(false);

        assertThatThrownBy(() -> userService.deactivateAccount(1L, "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid password");
    }

    // ─── reactivateAccount ───────────────────────────────────────────────────

    @Test
    void reactivateAccount_reactivatesDeactivatedUser() {
        savedUser.setActive(false);
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "encoded-pass")).thenReturn(true);
        when(userRepo.save(any(User.class))).thenReturn(savedUser);
        when(userRepo.findById(1L)).thenReturn(Optional.of(savedUser));
        when(subscriptionRepo.findTopByUserAndStatusOrderByCurrentPeriodEndDesc(savedUser, "active"))
                .thenReturn(Optional.empty());
        when(subscriptionRepo.save(any())).thenReturn(new Subscription());
        when(jwtService.generateToken("test@example.com", 1L)).thenReturn("reactivated-token");

        AuthResponse resp = userService.reactivateAccount("test@example.com", "password123");
        assertThat(resp.getToken()).isEqualTo("reactivated-token");
        assertThat(savedUser.isActive()).isTrue();
    }

    @Test
    void reactivateAccount_throwsIfAlreadyActive() {
        savedUser.setActive(true);
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "encoded-pass")).thenReturn(true);

        assertThatThrownBy(() -> userService.reactivateAccount("test@example.com", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already active");
    }

    // ─── findByEmail / findById ───────────────────────────────────────────────

    @Test
    void findByEmail_throwsWhenNotFound() {
        when(userRepo.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByEmail("missing@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }

    @Test
    void findById_throwsWhenNotFound() {
        when(userRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }
}
