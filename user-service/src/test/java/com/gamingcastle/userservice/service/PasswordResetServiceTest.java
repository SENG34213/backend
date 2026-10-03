package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.request.ForgotPasswordRequest;
import com.gamingcastle.userservice.dto.request.ResetPasswordRequest;
import com.gamingcastle.userservice.entity.PasswordResetChannel;
import com.gamingcastle.userservice.entity.PasswordResetToken;
import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.exception.InvalidResetCodeException;
import com.gamingcastle.userservice.repository.PasswordResetTokenRepository;
import com.gamingcastle.userservice.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Follows the course's AAA / Given-When-Then unit test standard (§6.3.1).
 * Covers FR-04 (email verification) and FR-05 (phone verification).
 */
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationService notificationService;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        passwordResetService = new PasswordResetServiceImpl(
                userRepository, tokenRepository, passwordEncoder, notificationService);
    }

    private User sampleUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("player@example.com")
                .phoneNumber("0771234567")
                .passwordHash("old-hash")
                .role(Role.CUSTOMER)
                .build();
    }

    // --- FR-04: request via email ---

    @Test
    void requestReset_shouldEmailCode_givenEmailIdentifier() {
        // Arrange
        User user = sampleUser();
        ForgotPasswordRequest request = new ForgotPasswordRequest("player@example.com");
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");

        // Act
        passwordResetService.requestReset(request);

        // Assert
        verify(notificationService).sendPasswordResetEmail(eq(user.getEmail()), any());
        verify(notificationService, never()).sendPasswordResetSms(any(), any());
        verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    // --- FR-05: request via phone ---

    @Test
    void requestReset_shouldTextCode_givenPhoneIdentifier() {
        // Arrange
        User user = sampleUser();
        ForgotPasswordRequest request = new ForgotPasswordRequest("0771234567");
        when(userRepository.findByPhoneNumber("0771234567")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");

        // Act
        passwordResetService.requestReset(request);

        // Assert
        verify(notificationService).sendPasswordResetSms(eq(user.getPhoneNumber()), any());
        verify(notificationService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void requestReset_shouldDoNothing_givenUnknownIdentifier() {
        // Arrange
        ForgotPasswordRequest request = new ForgotPasswordRequest("nobody@example.com");
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        // Act
        passwordResetService.requestReset(request);

        // Assert — no code generated, no notification sent, no enumeration signal
        verifyNoInteractions(notificationService);
        verify(tokenRepository, never()).save(any());
    }

    // --- reset confirmation, shared by FR-04/FR-05 ---

    @Test
    void resetPassword_shouldUpdatePassword_givenValidCode() {
        // Arrange
        User user = sampleUser();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash("hashed-code")
                .channel(PasswordResetChannel.EMAIL)
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .build();
        ResetPasswordRequest request = new ResetPasswordRequest("player@example.com", "123456", "newpassword1");

        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-hash");

        // Act
        passwordResetService.resetPassword(request);

        // Assert
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_shouldReject_givenExpiredCode() {
        // Arrange
        User user = sampleUser();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash("hashed-code")
                .channel(PasswordResetChannel.PHONE)
                .expiresAt(Instant.now().minusSeconds(60)) // already expired
                .used(false)
                .build();
        ResetPasswordRequest request = new ResetPasswordRequest("0771234567", "123456", "newpassword1");

        when(userRepository.findByPhoneNumber("0771234567")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(token));

        // Act & Assert
        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(InvalidResetCodeException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_shouldReject_givenWrongCode() {
        // Arrange
        User user = sampleUser();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash("hashed-code")
                .channel(PasswordResetChannel.EMAIL)
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .build();
        ResetPasswordRequest request = new ResetPasswordRequest("player@example.com", "000000", "newpassword1");

        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(InvalidResetCodeException.class);
        verify(userRepository, never()).save(any());
    }
}
