package com.gamingcastle.userservice.service;

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
import com.gamingcastle.userservice.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
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

        return AuthResponse.of(token,user.getId().toString(),user.getEmail(),user.getRole().name());
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {

        String email = request.email().toLowerCase().trim();
        log.info("Login attempt for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found for email: {}", email);
                    return new InvalidCredentialsException();
                });

        return authenticate(user, request.password());
    }

    @Override
    @Transactional
    public AuthResponse loginByPhone(PhoneLoginRequest request) {

        String phoneNumber = request.phoneNumber().trim();
        log.info("Login attempt for phone number: {}", phoneNumber);

        User user = userRepository.findByPhoneNumber(phoneNumber)
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

        if (user.isLocked()) {
            log.warn("Login rejected - account is locked for user: {}", user.getEmail());
            throw new AccountLockedException();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {

            log.warn("Login failed - invalid password for user: {}", user.getEmail());
            registerFailedAttempt(user);
            log.info("Failed login attempts for {}: {}", user.getEmail(), user.getFailedLoginAttempts());

            if (user.isLocked()) {
                log.warn("Account locked due to repeated failed login attempts: {}", user.getEmail());
            }

            throw new InvalidCredentialsException();
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
