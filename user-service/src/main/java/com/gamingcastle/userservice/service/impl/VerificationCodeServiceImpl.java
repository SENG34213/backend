package com.gamingcastle.userservice.service.impl;

import com.gamingcastle.userservice.client.NotificationClient;
import com.gamingcastle.userservice.entity.PasswordResetChannel;
import com.gamingcastle.userservice.entity.PasswordResetToken;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.entity.VerificationPurpose;
import com.gamingcastle.userservice.exception.InvalidResetCodeException;
import com.gamingcastle.userservice.repository.PasswordResetTokenRepository;
import com.gamingcastle.userservice.repository.UserRepository;

import com.gamingcastle.userservice.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final int CODE_EXPIRY_MINUTES = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationClient notificationClient;

    public VerificationCodeServiceImpl(UserRepository userRepository,
                                       PasswordResetTokenRepository tokenRepository,
                                       PasswordEncoder passwordEncoder,
                                       NotificationClient notificationClient) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationClient = notificationClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findUserByIdentifier(String identifier) {
        String trimmed = identifier.trim();
        return isEmail(trimmed)
                ? userRepository.findByEmail(trimmed.toLowerCase())
                : userRepository.findByPhoneNumber(trimmed);
    }

    @Override
    @Transactional
    public void issueCode(User user, String identifier, VerificationPurpose purpose) {
        PasswordResetChannel channel = isEmail(identifier.trim())
                ? PasswordResetChannel.EMAIL
                : PasswordResetChannel.PHONE;
        String code = generateCode();

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .channel(channel)
                .purpose(purpose)
                .expiresAt(Instant.now().plus(CODE_EXPIRY_MINUTES, ChronoUnit.MINUTES))
                .used(false)
                .build();
        tokenRepository.save(token);

        log.info("Verification code ({}) issued for user: {} via {}", purpose, user.getId(), channel);

        if (channel == PasswordResetChannel.EMAIL) {
            notificationClient.sendPasswordResetEmail(user.getEmail(), code);
        } else {
            notificationClient.sendPasswordResetSms(user.getPhoneNumber(), code);
        }
    }

    @Override
    @Transactional
    public void verifyAndConsume(User user, String code, VerificationPurpose purpose) {
        PasswordResetToken token = tokenRepository
                .findFirstByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(user.getId(), purpose)
                .orElseThrow(InvalidResetCodeException::new);

        if (token.isExpired()) {
            log.warn("Verification ({}) rejected - code expired for user: {}", purpose, user.getId());
            throw new InvalidResetCodeException("Verification code has expired");
        }

        if (!passwordEncoder.matches(code, token.getCodeHash())) {
            log.warn("Verification ({}) rejected - code mismatch for user: {}", purpose, user.getId());
            throw new InvalidResetCodeException();
        }

        token.setUsed(true);
        tokenRepository.save(token);
    }

    private boolean isEmail(String identifier) {
        return identifier.contains("@");
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
