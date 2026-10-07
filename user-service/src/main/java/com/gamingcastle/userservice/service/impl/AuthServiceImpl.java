package com.gamingcastle.userservice.service.impl;

import com.gamingcastle.userservice.dto.response.AuthResponse;
import com.gamingcastle.userservice.dto.request.LoginRequest;
import com.gamingcastle.userservice.dto.request.PhoneLoginRequest;
import com.gamingcastle.userservice.dto.request.RegisterRequest;
import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.exception.AccountLockedException;
import com.gamingcastle.userservice.exception.EmailAlreadyExistsException;
import com.gamingcastle.userservice.exception.InvalidCredentialsException;
import com.gamingcastle.userservice.exception.PhoneAlreadyExistsException;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.service.AuthService;
import com.gamingcastle.userservice.util.JwtUtil;
import com.gamingcastle.userservice.client.NotificationClient;
import com.gamingcastle.userservice.exception.AccountDeactivatedByAdminException;
import com.gamingcastle.userservice.exception.AccountDeactivatedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * FR-01–FR-06: registration, login, and account-lockout logic.
 * Kept separate from the controller so it stays unit-testable without
 * spinning up the web layer (see the course's unit test standards, §6.3.1).
 */
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final NotificationClient notificationClient;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, NotificationClient notificationClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.notificationClient = notificationClient;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String email = request.email().toLowerCase().trim();

        log.info("Registration attempt for email: {}", email);

        if (userRepository.existsByEmail(email)) {log.warn("Registration failed - email already exists: {}", email);
            throw new EmailAlreadyExistsException();
        }

        String phoneNumber = request.phoneNumber() != null ? request.phoneNumber().trim() : null;
        if (phoneNumber != null && !phoneNumber.isBlank() && userRepository.existsByPhoneNumber(phoneNumber)) {
            log.warn("Registration failed - phone number already exists: {}", phoneNumber);
            throw new PhoneAlreadyExistsException();
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phoneNumber(request.phoneNumber())
                .role(Role.CUSTOMER)
                .build();

        user = userRepository.save(user);

        log.info("User registered successfully - email: {}, role: {}",user.getEmail(),user.getRole());

        String token = jwtUtil.generateToken(user);

        log.debug("JWT token generated successfully for newly registered user: {}",user.getEmail());

        boolean notificationSent = true;

        try {
            notificationClient.sendWelcomeEmail(user.getEmail(), user.getFullName());
            log.info("Welcome notification sent successfully to {}",user.getEmail());
        } catch (Exception e) {
            notificationSent = false;
            log.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage(),e);
        }

        return AuthResponse.of(token,user.getId().toString(),user.getEmail(),user.getRole().name(),notificationSent);
    }

    @Override
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public AuthResponse login(LoginRequest request) {

        String email = request.email().toLowerCase().trim();
        log.info("Login attempt for email: {}", email);

        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found for email: {}", email);
                    return new InvalidCredentialsException();
                });

        return authenticate(user, request.password());
    }

    @Override
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public AuthResponse loginByPhone(PhoneLoginRequest request) {

        String phoneNumber = request.phoneNumber().trim();
        log.info("Login attempt for phone number: {}", phoneNumber);

        User user = userRepository.findByPhoneNumberForUpdate(phoneNumber)
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found for phone number: {}", phoneNumber);
                    return new InvalidCredentialsException();
                });

        return authenticate(user, request.password());
    }

    /**
     * FR-03/FR-06: shared credential-check + lockout logic used by both the
     * email login (FR-01/FR-02 flow) and the phone-number login (FR-03).
     */
    private AuthResponse authenticate(User user, String rawPassword) {

        if (user.getLockedUntil() != null && !user.isLocked()) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }

        if (user.isLocked()) {
            log.warn("Login rejected - account is locked for user: {}", user.getEmail());
            throw new AccountLockedException(user.getLockedUntil());   // remaining time anuppum
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            registerFailedAttempt(user);

            // 5th thappa attempt la udane "locked, 15 minutes" nu solla
            if (user.isLocked()) {
                log.warn("Account locked due to repeated failed login attempts: {}", user.getEmail());
                throw new AccountLockedException(user.getLockedUntil());
            }
            throw new InvalidCredentialsException();
        }

        // Password correct, aana account switch-off
        if (!user.isEnabled()) {
            if (user.isSelfDeactivated()) {
                log.warn("Login rejected - account deactivated by the user, verification required: {}", user.getEmail());
                throw new AccountDeactivatedException();
            }
            log.warn("Login rejected - account deactivated by an admin: {}", user.getEmail());
            throw new AccountDeactivatedByAdminException();
        }

        // Successful login resets the failed-attempt counter
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        log.info("Login successful for user: {} with role: {}", user.getEmail(), user.getRole());

        String token = jwtUtil.generateToken(user);

        log.debug("JWT token generated successfully for user: {}", user.getEmail());

        return AuthResponse.of(token, user.getId().toString(), user.getEmail(), user.getRole().name());
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES));
        }
        userRepository.save(user);
    }
}
