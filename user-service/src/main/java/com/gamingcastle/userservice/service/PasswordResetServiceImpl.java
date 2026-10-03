package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.request.ForgotPasswordRequest;
import com.gamingcastle.userservice.dto.request.ResetPasswordRequest;
import com.gamingcastle.userservice.entity.PasswordResetChannel;
import com.gamingcastle.userservice.entity.PasswordResetToken;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.exception.InvalidResetCodeException;
import com.gamingcastle.userservice.repository.PasswordResetTokenRepository;
import com.gamingcastle.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * FR-04/FR-05: password reset via email verification or phone verification.
 * Which channel is used is decided purely by the shape of {@code identifier}
 * ("@" in it -&gt; email, otherwise phone) so both FRs share one flow.
 */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final int CODE_EXPIRY_MINUTES = 15;
    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public PasswordResetServiceImpl(UserRepository userRepository,
                                    PasswordResetTokenRepository tokenRepository,
                                    PasswordEncoder passwordEncoder,
                                    NotificationService notificationService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        String identifier = request.identifier().trim();
        boolean byEmail = isEmail(identifier);

        Optional<User> userOpt = byEmail
                ? userRepository.findByEmail(identifier.toLowerCase())
                : userRepository.findByPhoneNumber(identifier);

        // Same behaviour whether or not the account exists, so this endpoint
        // can't be used to enumerate registered emails/phone numbers.
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown identifier");
            return;
        }

        User user = userOpt.get();
        String code = generateCode();
        PasswordResetChannel channel = byEmail ? PasswordResetChannel.EMAIL : PasswordResetChannel.PHONE;

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .channel(channel)
                .expiresAt(Instant.now().plus(CODE_EXPIRY_MINUTES, ChronoUnit.MINUTES))
                .used(false)
                .build();
        tokenRepository.save(token);

        log.info("Password reset code issued for user: {} via {}", user.getId(), channel);

        if (channel == PasswordResetChannel.EMAIL) {
            notificationService.sendPasswordResetEmail(user.getEmail(), code);
        } else {
            notificationService.sendPasswordResetSms(user.getPhoneNumber(), code);
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String identifier = request.identifier().trim();
        boolean byEmail = isEmail(identifier);

        User user = (byEmail
                ? userRepository.findByEmail(identifier.toLowerCase())
                : userRepository.findByPhoneNumber(identifier))
                .orElseThrow(InvalidResetCodeException::new);

        PasswordResetToken token = tokenRepository
                .findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(InvalidResetCodeException::new);

        if (token.isExpired()) {
            log.warn("Password reset rejected - code expired for user: {}", user.getId());
            throw new InvalidResetCodeException("Verification code has expired");
        }

        if (!passwordEncoder.matches(request.code(), token.getCodeHash())) {
            log.warn("Password reset rejected - code mismatch for user: {}", user.getId());
            throw new InvalidResetCodeException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // A successful, verified reset is as strong a proof of ownership as a
        // correct password, so it also clears any FR-06 lockout.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        log.info("Password reset successful for user: {}", user.getId());
    }

    private boolean isEmail(String identifier) {
        return identifier.contains("@");
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
