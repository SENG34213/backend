package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.request.ForgotPasswordRequest;
import com.gamingcastle.userservice.dto.request.ResetPasswordRequest;
import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.entity.VerificationPurpose;
import com.gamingcastle.userservice.exception.InvalidResetCodeException;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.service.impl.PasswordResetServiceImpl;
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
 * Covers FR-04 (email verification) and FR-05 (phone verification).
 * Code generation / email-vs-SMS routing / expiry / hash matching now live in
 * VerificationCodeService and are tested in VerificationCodeServiceTest -
 * here we only check that the reset flow uses it correctly.
 */
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private VerificationCodeService verificationCodeService;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        passwordResetService = new PasswordResetServiceImpl(
                userRepository, passwordEncoder, verificationCodeService);
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

    // --- FR-04 / FR-05: request ---

    @Test
    void requestReset_shouldIssueResetCode_givenEmailIdentifier() {
        User user = sampleUser();
        when(verificationCodeService.findUserByIdentifier("player@example.com")).thenReturn(Optional.of(user));

        passwordResetService.requestReset(new ForgotPasswordRequest("player@example.com"));
        verify(verificationCodeService).issueCode(user, "player@example.com", VerificationPurpose.PASSWORD_RESET);
    }

    @Test
    void requestReset_shouldIssueResetCode_givenPhoneIdentifier() {
        User user = sampleUser();
        when(verificationCodeService.findUserByIdentifier("0771234567")).thenReturn(Optional.of(user));

        passwordResetService.requestReset(new ForgotPasswordRequest("0771234567"));
        verify(verificationCodeService).issueCode(user, "0771234567", VerificationPurpose.PASSWORD_RESET);
    }

    @Test
    void requestReset_shouldDoNothing_givenUnknownIdentifier() {
        when(verificationCodeService.findUserByIdentifier("nobody@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestReset(new ForgotPasswordRequest("nobody@example.com"));
        verify(verificationCodeService, never()).issueCode(any(), any(), any());
    }

    // --- reset confirmation, shared by FR-04/FR-05 ---

    @Test
    void resetPassword_shouldUpdatePassword_givenValidCode() {
        User user = sampleUser();
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(Instant.now().plusSeconds(300));
        when(verificationCodeService.findUserByIdentifier("player@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-hash");

        passwordResetService.resetPassword(new ResetPasswordRequest("player@example.com", "123456", "newpassword1"));

        verify(verificationCodeService).verifyAndConsume(user, "123456", VerificationPurpose.PASSWORD_RESET);
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_shouldReject_givenInvalidCode() {
        User user = sampleUser();
        when(verificationCodeService.findUserByIdentifier("player@example.com")).thenReturn(Optional.of(user));
        doThrow(new InvalidResetCodeException())
                .when(verificationCodeService).verifyAndConsume(user, "000000", VerificationPurpose.PASSWORD_RESET);

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                new ResetPasswordRequest("player@example.com", "000000", "newpassword1")))
                .isInstanceOf(InvalidResetCodeException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_shouldReject_givenUnknownIdentifier() {
        when(verificationCodeService.findUserByIdentifier("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                new ResetPasswordRequest("nobody@example.com", "123456", "newpassword1")))
                .isInstanceOf(InvalidResetCodeException.class);
        verify(userRepository, never()).save(any());
    }
}