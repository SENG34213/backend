package com.gamingcastle.userservice.service.impl;

import com.gamingcastle.userservice.dto.request.ConfirmReactivationRequest;
import com.gamingcastle.userservice.dto.request.ReactivateAccountRequest;
import com.gamingcastle.userservice.dto.response.AuthResponse;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.entity.VerificationPurpose;
import com.gamingcastle.userservice.exception.InvalidResetCodeException;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.service.AccountReactivationService;
import com.gamingcastle.userservice.service.VerificationCodeService;
import com.gamingcastle.userservice.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AccountReactivationServiceImpl implements AccountReactivationService {

    private static final Logger log = LoggerFactory.getLogger(AccountReactivationServiceImpl.class);

    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodeService;
    private final JwtUtil jwtUtil;

    public AccountReactivationServiceImpl(UserRepository userRepository,
                                          VerificationCodeService verificationCodeService,
                                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.verificationCodeService = verificationCodeService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional
    public void requestReactivation(ReactivateAccountRequest request) {
        String identifier = request.identifier().trim();

        Optional<User> userOpt = verificationCodeService.findUserByIdentifier(identifier);

        if (userOpt.isEmpty() || !userOpt.get().isSelfDeactivated()) {
            log.info("Reactivation code requested for an identifier that is not eligible");
            return;
        }

        verificationCodeService.issueCode(userOpt.get(), identifier, VerificationPurpose.ACCOUNT_REACTIVATION);
    }

    @Override
    @Transactional(noRollbackFor = InvalidResetCodeException.class)
    public AuthResponse confirmReactivation(ConfirmReactivationRequest request) {
        String identifier = request.identifier().trim();

        User user = verificationCodeService.findUserByIdentifier(identifier)
                .orElseThrow(InvalidResetCodeException::new);

        if (!user.isSelfDeactivated()) {
            throw new InvalidResetCodeException();
        }

        verificationCodeService.verifyAndConsume(user, request.code(), VerificationPurpose.ACCOUNT_REACTIVATION);

        user.activate();
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        log.info("Account reactivated by the user after verification: userId={}", user.getId());

        String token = jwtUtil.generateToken(user);
        return AuthResponse.of(token, user.getId().toString(), user.getEmail(), user.getRole().name());
    }
}