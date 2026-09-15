package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.AuthResponse;
import com.gamingcastle.userservice.dto.LoginRequest;
import com.gamingcastle.userservice.dto.RegisterRequest;
import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.exception.AuthException;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

            throw new AuthException(HttpStatus.CONFLICT,"EMAIL_TAKEN","An account with this email already exists");
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
                    return new AuthException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Invalid email or password");
                });

        if (user.isLocked()) {
            log.warn("Login rejected - account is locked for email: {}", email);
            throw new AuthException(HttpStatus.LOCKED,"ACCOUNT_LOCKED","Account is temporarily locked due to repeated failed login attempts");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {

            log.warn("Login failed - invalid password for email: {}", email);
            registerFailedAttempt(user);
            log.info("Failed login attempts for {}: {}",email,user.getFailedLoginAttempts());

            if (user.isLocked()) {
                log.warn("Account locked due to repeated failed login attempts: {}", email);
            }

            throw new AuthException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Invalid email or password");
        }

        // Successful login resets the failed-attempt counter
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        log.info("Login successful for user: {} with role: {}", email, user.getRole());

        String token = jwtUtil.generateToken(user);

        log.debug("JWT token generated successfully for user: {}", email);

        return AuthResponse.of(token,user.getId().toString(),user.getEmail(),user.getRole().name());
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
