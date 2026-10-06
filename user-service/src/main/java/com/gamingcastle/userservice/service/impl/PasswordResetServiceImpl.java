package com.gamingcastle.userservice.service.impl;

import com.gamingcastle.userservice.dto.request.ForgotPasswordRequest;
import com.gamingcastle.userservice.dto.request.ResetPasswordRequest;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.entity.VerificationPurpose;
import com.gamingcastle.userservice.exception.InvalidResetCodeException;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.service.PasswordResetService;
import com.gamingcastle.userservice.service.VerificationCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;

    public PasswordResetServiceImpl(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    VerificationCodeService verificationCodeService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeService = verificationCodeService;
    }

    @Override
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        String identifier = request.identifier().trim();

        Optional<User> userOpt = verificationCodeService.findUserByIdentifier(identifier);

        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown identifier");
            return;
        }

        verificationCodeService.issueCode(userOpt.get(), identifier, VerificationPurpose.PASSWORD_RESET);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String identifier = request.identifier().trim();

        User user = verificationCodeService.findUserByIdentifier(identifier)
                .orElseThrow(InvalidResetCodeException::new);

        verificationCodeService.verifyAndConsume(user, request.code(), VerificationPurpose.PASSWORD_RESET);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        log.info("Password reset successful for user: {}", user.getId());
    }
}