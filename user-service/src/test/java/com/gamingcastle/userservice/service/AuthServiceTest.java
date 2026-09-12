package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.AuthResponse;
import com.gamingcastle.userservice.dto.LoginRequest;
import com.gamingcastle.userservice.dto.RegisterRequest;
import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.exception.AuthException;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Follows the course's AAA / Given-When-Then unit test standard (§6.3.1).
 * Covers AC1 (happy path registration/login) and the account-lockout edge
 * case (FR-06), matching the Test Requirements pattern from the issue template.
 */
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    private AuthService authService; // depend on the interface, same as AuthController does

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtUtil);
    }

    @Test
    void register_shouldCreateCustomerAccount_givenNewEmail() {
        // Arrange
        RegisterRequest request = new RegisterRequest("player@example.com", "password123", "Test Player", "0771234567");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtUtil.generateToken(any(User.class))).thenReturn("mock-jwt-token");

        // Act
        AuthResponse response = authService.register(request);

        // Assert
        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER.name());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldThrowConflict_givenEmailAlreadyExists() {
        // Arrange
        RegisterRequest request = new RegisterRequest("existing@example.com", "password123", "Someone", null);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already exists");
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_shouldReturnToken_givenValidCredentials() {
        // Arrange
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("player@example.com")
                .passwordHash("hashed-password")
                .role(Role.CUSTOMER)
                .failedLoginAttempts(0)
                .build();
        LoginRequest request = new LoginRequest("player@example.com", "password123");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generateToken(user)).thenReturn("mock-jwt-token");

        // Act
        AuthResponse response = authService.login(request);

        // Assert
        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void login_shouldLockAccount_afterFiveFailedAttempts() {
        // Arrange
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("player@example.com")
                .passwordHash("hashed-password")
                .role(Role.CUSTOMER)
                .failedLoginAttempts(4) // one more failure will trip the lock
                .build();
        LoginRequest request = new LoginRequest("player@example.com", "wrong-password");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(AuthException.class);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.isLocked()).isTrue();
    }

    @Test
    void login_shouldRejectLockedAccount_evenWithCorrectPassword() {
        // Arrange
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("player@example.com")
                .passwordHash("hashed-password")
                .role(Role.CUSTOMER)
                .lockedUntil(Instant.now().plusSeconds(600))
                .build();
        LoginRequest request = new LoginRequest("player@example.com", "password123");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("locked");
        verify(passwordEncoder, never()).matches(any(), any());
    }
}
