package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.request.ConfirmReactivationRequest;
import com.gamingcastle.userservice.dto.request.ReactivateAccountRequest;
import com.gamingcastle.userservice.dto.response.AuthResponse;
import com.gamingcastle.userservice.entity.DeactivatedBy;
import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.entity.VerificationPurpose;
import com.gamingcastle.userservice.exception.InvalidResetCodeException;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.service.impl.AccountReactivationServiceImpl;
import com.gamingcastle.userservice.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountReactivationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private JwtUtil jwtUtil;

    private AccountReactivationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AccountReactivationServiceImpl(userRepository, verificationCodeService, jwtUtil);
    }

    private User userDeactivatedBy(DeactivatedBy by) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("player@example.com")
                .phoneNumber("0771234567")
                .passwordHash("hash")
                .role(Role.CUSTOMER)
                .build();
        user.deactivate(by);
        return user;
    }

    // --- request ---

    @Test
    void requestReactivation_shouldIssueCode_givenSelfDeactivatedUser() {
        User user = userDeactivatedBy(DeactivatedBy.SELF);
        when(verificationCodeService.findUserByIdentifier("player@example.com")).thenReturn(Optional.of(user));

        service.requestReactivation(new ReactivateAccountRequest("player@example.com"));

        verify(verificationCodeService).issueCode(user, "player@example.com", VerificationPurpose.ACCOUNT_REACTIVATION);
    }

    @Test
    void requestReactivation_shouldDoNothing_givenAdminDeactivatedUser() {
        User user = userDeactivatedBy(DeactivatedBy.ADMIN);
        when(verificationCodeService.findUserByIdentifier("player@example.com")).thenReturn(Optional.of(user));

        service.requestReactivation(new ReactivateAccountRequest("player@example.com"));

        verify(verificationCodeService, never()).issueCode(any(), any(), any());
    }

    @Test
    void requestReactivation_shouldDoNothing_givenActiveUser() {
        User user = User.builder().id(UUID.randomUUID()).email("player@example.com").role(Role.CUSTOMER).build();
        when(verificationCodeService.findUserByIdentifier("player@example.com")).thenReturn(Optional.of(user));

        service.requestReactivation(new ReactivateAccountRequest("player@example.com"));

        verify(verificationCodeService, never()).issueCode(any(), any(), any());
    }

    @Test
    void requestReactivation_shouldDoNothing_givenUnknownIdentifier() {
        when(verificationCodeService.findUserByIdentifier("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReactivation(new ReactivateAccountRequest("nobody@example.com"));

        verify(verificationCodeService, never()).issueCode(any(), any(), any());
    }

    // --- confirm ---

    @Test
    void confirmReactivation_shouldActivateAndLogin_givenValidCode() {
        User user = userDeactivatedBy(DeactivatedBy.SELF);
        user.setFailedLoginAttempts(2);
        user.setLockedUntil(Instant.now().plusSeconds(300));
        when(verificationCodeService.findUserByIdentifier("player@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("mock-jwt-token");

        AuthResponse response = service.confirmReactivation(
                new ConfirmReactivationRequest("player@example.com", "123456"));

        verify(verificationCodeService).verifyAndConsume(user, "123456", VerificationPurpose.ACCOUNT_REACTIVATION);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getDeactivatedBy()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
        verify(userRepository).save(user);
    }

    @Test
    void confirmReactivation_shouldStayDeactivated_givenInvalidCode() {
        User user = userDeactivatedBy(DeactivatedBy.SELF);
        when(verificationCodeService.findUserByIdentifier("player@example.com")).thenReturn(Optional.of(user));
        doThrow(new InvalidResetCodeException())
                .when(verificationCodeService).verifyAndConsume(user, "000000", VerificationPurpose.ACCOUNT_REACTIVATION);

        assertThatThrownBy(() -> service.confirmReactivation(
                new ConfirmReactivationRequest("player@example.com", "000000")))
                .isInstanceOf(InvalidResetCodeException.class);
        assertThat(user.isEnabled()).isFalse();
        verify(userRepository, never()).save(any());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void confirmReactivation_shouldReject_givenAdminDeactivatedUser() {
        User user = userDeactivatedBy(DeactivatedBy.ADMIN);
        when(verificationCodeService.findUserByIdentifier("player@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.confirmReactivation(
                new ConfirmReactivationRequest("player@example.com", "123456")))
                .isInstanceOf(InvalidResetCodeException.class);
        assertThat(user.isEnabled()).isFalse();
        verify(verificationCodeService, never()).verifyAndConsume(any(), any(), any());
    }

    @Test
    void confirmReactivation_shouldReject_givenUnknownIdentifier() {
        when(verificationCodeService.findUserByIdentifier("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmReactivation(
                new ConfirmReactivationRequest("nobody@example.com", "123456")))
                .isInstanceOf(InvalidResetCodeException.class);
    }
}
