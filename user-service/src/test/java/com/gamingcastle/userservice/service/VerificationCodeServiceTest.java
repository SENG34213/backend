package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.client.NotificationClient;
import com.gamingcastle.userservice.entity.PasswordResetChannel;
import com.gamingcastle.userservice.entity.PasswordResetToken;
import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.entity.VerificationPurpose;
import com.gamingcastle.userservice.exception.InvalidResetCodeException;
import com.gamingcastle.userservice.repository.PasswordResetTokenRepository;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.service.impl.VerificationCodeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/** The shared code logic used by both password reset and account reactivation. */
class VerificationCodeServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationClient notificationClient;

    private VerificationCodeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new VerificationCodeServiceImpl(userRepository, tokenRepository, passwordEncoder, notificationClient);
    }

    private User sampleUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("player@example.com")
                .phoneNumber("0771234567")
                .passwordHash("hash")
                .role(Role.CUSTOMER)
                .build();
    }

    private PasswordResetToken token(User user, VerificationPurpose purpose, Instant expiresAt) {
        return PasswordResetToken.builder()
                .user(user)
                .codeHash("hashed-code")
                .channel(PasswordResetChannel.EMAIL)
                .purpose(purpose)
                .expiresAt(expiresAt)
                .used(false)
                .build();
    }

    // --- findUserByIdentifier ---

    @Test
    void findUserByIdentifier_shouldLookUpByEmail_whenIdentifierContainsAt() {
        User user = sampleUser();
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(user));

        assertThat(service.findUserByIdentifier("  Player@Example.com ")).contains(user);
        verify(userRepository, never()).findByPhoneNumber(any());
    }

    @Test
    void findUserByIdentifier_shouldLookUpByPhone_whenNoAt() {
        User user = sampleUser();
        when(userRepository.findByPhoneNumber("0771234567")).thenReturn(Optional.of(user));

        assertThat(service.findUserByIdentifier("0771234567")).contains(user);
        verify(userRepository, never()).findByEmail(any());
    }

    // --- issueCode ---

    @Test
    void issueCode_shouldEmailAccountReactivationCode_givenEmailIdentifierAndReactivationPurpose() {
        User user = sampleUser();
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");

        service.issueCode(user, "player@example.com", VerificationPurpose.ACCOUNT_REACTIVATION);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getPurpose()).isEqualTo(VerificationPurpose.ACCOUNT_REACTIVATION);
        assertThat(captor.getValue().getChannel()).isEqualTo(PasswordResetChannel.EMAIL);
        assertThat(captor.getValue().getCodeHash()).isEqualTo("hashed-code");
        verify(notificationClient).sendAccountReactivationEmail(eq(user.getEmail()), any());
        verify(notificationClient, never()).sendPasswordResetEmail(any(), any());
        verify(notificationClient, never()).sendPasswordResetSms(any(), any());
    }

    @Test
    void issueCode_shouldEmailPasswordResetCode_givenEmailIdentifierAndPasswordResetPurpose() {
        User user = sampleUser();
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");

        service.issueCode(user, "player@example.com", VerificationPurpose.PASSWORD_RESET);

        verify(notificationClient).sendPasswordResetEmail(eq(user.getEmail()), any());
        verify(notificationClient, never()).sendAccountReactivationEmail(any(), any());
    }

    @Test
    void issueCode_shouldTextPasswordResetCode_givenPhoneIdentifierAndPasswordResetPurpose() {
        User user = sampleUser();
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");

        service.issueCode(user, "0771234567", VerificationPurpose.PASSWORD_RESET);

        verify(notificationClient).sendPasswordResetSms(eq(user.getPhoneNumber()), any());
        verify(notificationClient, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void issueCode_shouldTextAccountReactivationCode_givenPhoneIdentifierAndReactivationPurpose() {
        User user = sampleUser();
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");

        service.issueCode(user, "0771234567", VerificationPurpose.ACCOUNT_REACTIVATION);

        verify(notificationClient).sendAccountReactivationSms(eq(user.getPhoneNumber()), any());
        verify(notificationClient, never()).sendPasswordResetSms(any(), any());
    }

    // --- verifyAndConsume ---

    @Test
    void verifyAndConsume_shouldMarkCodeUsed_givenValidCode() {
        User user = sampleUser();
        PasswordResetToken token = token(user, VerificationPurpose.PASSWORD_RESET, Instant.now().plusSeconds(600));
        when(tokenRepository.findFirstByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                user.getId(), VerificationPurpose.PASSWORD_RESET)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);

        service.verifyAndConsume(user, "123456", VerificationPurpose.PASSWORD_RESET);

        assertThat(token.isUsed()).isTrue();
        verify(tokenRepository).save(token);
    }

    @Test
    void verifyAndConsume_shouldReject_givenExpiredCode() {
        User user = sampleUser();
        PasswordResetToken token = token(user, VerificationPurpose.PASSWORD_RESET, Instant.now().minusSeconds(60));
        when(tokenRepository.findFirstByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                user.getId(), VerificationPurpose.PASSWORD_RESET)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyAndConsume(user, "123456", VerificationPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidResetCodeException.class);
        assertThat(token.isUsed()).isFalse();
    }

    @Test
    void verifyAndConsume_shouldReject_givenWrongCode() {
        User user = sampleUser();
        PasswordResetToken token = token(user, VerificationPurpose.PASSWORD_RESET, Instant.now().plusSeconds(600));
        when(tokenRepository.findFirstByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                user.getId(), VerificationPurpose.PASSWORD_RESET)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyAndConsume(user, "000000", VerificationPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidResetCodeException.class);
        assertThat(token.isUsed()).isFalse();
    }

    @Test
    void verifyAndConsume_shouldReject_whenNoCodeExistsForThatPurpose() {
        // e.g. the user only has a PASSWORD_RESET code and tries to use it for reactivation
        User user = sampleUser();
        when(tokenRepository.findFirstByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                user.getId(), VerificationPurpose.ACCOUNT_REACTIVATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndConsume(user, "123456", VerificationPurpose.ACCOUNT_REACTIVATION))
                .isInstanceOf(InvalidResetCodeException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }
}