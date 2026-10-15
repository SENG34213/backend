package com.gamingcastle.userservice.controller;

import com.gamingcastle.userservice.client.NotificationClient;
import com.gamingcastle.userservice.dto.request.*;
import com.gamingcastle.userservice.dto.response.AuthResponse;
import com.gamingcastle.userservice.dto.response.ErrorResponse;
import com.gamingcastle.userservice.entity.*;
import com.gamingcastle.userservice.exception.ErrorCodes;
import com.gamingcastle.userservice.repository.PasswordResetTokenRepository;
import com.gamingcastle.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the public authentication and account-recovery endpoints:
 * - POST /api/auth/register
 * - POST /api/auth/login
 * - POST /api/auth/login/phone
 * - POST /api/auth/password/forgot
 * - POST /api/auth/password/reset
 * - POST /api/auth/reactivate/request
 * - POST /api/auth/reactivate/confirm
 *
 * Runs against the real Spring Boot application context with the dedicated test database.
 * External notification service calls are stubbed via @MockBean NotificationClient.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserServiceAuthIntegrationTest {

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL = "/api/auth/login";
    private static final String PHONE_LOGIN_URL = "/api/auth/login/phone";
    private static final String FORGOT_PASSWORD_URL = "/api/auth/password/forgot";
    private static final String RESET_PASSWORD_URL = "/api/auth/password/reset";
    private static final String REACTIVATE_REQUEST_URL = "/api/auth/reactivate/request";
    private static final String REACTIVATE_CONFIRM_URL = "/api/auth/reactivate/confirm";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private NotificationClient notificationClient;

    @BeforeEach
    void cleanDatabase() {
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(notificationClient).sendWelcomeEmail(anyString(), anyString());
        doNothing().when(notificationClient).sendPasswordResetEmail(anyString(), anyString());
        doNothing().when(notificationClient).sendPasswordResetSms(anyString(), anyString());
        doNothing().when(notificationClient).sendAccountReactivationEmail(anyString(), anyString());
        doNothing().when(notificationClient).sendAccountReactivationSms(anyString(), anyString());
    }

    // =========================================================================
    // POST /api/auth/register
    // =========================================================================

    @Test
    @DisplayName("POST /api/auth/register - successful registration persists user and returns 201 with JWT")
    void register_withValidData_shouldPersistUserAndReturn201WithJwt() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "player_" + uniqueSuffix + "@example.com";
        String rawPassword = "StrongPassword123!";
        String fullName = "Test Player " + uniqueSuffix;
        String phone = "+9477" + uniqueSuffix.replaceAll("[^0-9]", "1").substring(0, 7);

        RegisterRequest request = new RegisterRequest(email, rawPassword, fullName, phone);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                REGISTER_URL,
                request,
                AuthResponse.class
        );

        // Verify HTTP status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify Response Body
        AuthResponse authResponse = response.getBody();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.accessToken()).isNotBlank();
        assertThat(authResponse.tokenType()).isEqualTo("Bearer");
        assertThat(authResponse.email()).isEqualTo(email.toLowerCase());
        assertThat(authResponse.role()).isEqualTo("CUSTOMER");
        assertThat(authResponse.userId()).isNotBlank();
        assertThat(authResponse.notificationSent()).isTrue();

        // Verify Database Persistence
        Optional<User> savedUserOpt = userRepository.findByEmail(email.toLowerCase());
        assertThat(savedUserOpt).isPresent();

        User savedUser = savedUserOpt.get();
        assertThat(savedUser.getId().toString()).isEqualTo(authResponse.userId());
        assertThat(savedUser.getFullName()).isEqualTo(fullName);
        assertThat(savedUser.getPhoneNumber()).isEqualTo(phone);
        assertThat(savedUser.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(savedUser.isEnabled()).isTrue();
        assertThat(savedUser.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(savedUser.getLockedUntil()).isNull();
        assertThat(passwordEncoder.matches(rawPassword, savedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("POST /api/auth/register - duplicate email returns 409 Conflict with EMAIL_TAKEN")
    void register_withDuplicateEmail_shouldReturn409Conflict() {
        String email = "existing_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        createAndSaveUser(email, "+94770000001", "Existing User", "Password123!");

        RegisterRequest request = new RegisterRequest(
                email.toUpperCase(), // Test case-insensitive duplicate email detection
                "AnotherPassword123!",
                "Duplicate Email Applicant",
                "+94779999999"
        );

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                REGISTER_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.EMAIL_TAKEN);
        assertThat(errorResponse.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(errorResponse.path()).isEqualTo(REGISTER_URL);
    }

    @Test
    @DisplayName("POST /api/auth/register - duplicate phone returns 409 Conflict with PHONE_TAKEN")
    void register_withDuplicatePhone_shouldReturn409Conflict() {
        String phone = "+9477" + UUID.randomUUID().toString().replaceAll("[^0-9]", "2").substring(0, 7);
        createAndSaveUser("phone_owner_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                phone, "Phone Owner", "Password123!");

        RegisterRequest request = new RegisterRequest(
                "new_applicant_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "NewPassword123!",
                "Duplicate Phone Applicant",
                phone
        );

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                REGISTER_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.PHONE_TAKEN);
        assertThat(errorResponse.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(errorResponse.path()).isEqualTo(REGISTER_URL);
    }

    @Test
    @DisplayName("POST /api/auth/register - validation failure returns 400 Bad Request with field errors")
    void register_withInvalidData_shouldReturn400BadRequest() {
        // Invalid email format, password < 8 characters, blank full name
        RegisterRequest request = new RegisterRequest("not-a-valid-email", "short", "   ", null);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                REGISTER_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThat(errorResponse.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(errorResponse.fieldErrors()).isNotEmpty();
        assertThat(errorResponse.fieldErrors()).containsKey("email");
        assertThat(errorResponse.fieldErrors()).containsKey("password");
        assertThat(errorResponse.fieldErrors()).containsKey("fullName");
    }

    // =========================================================================
    // POST /api/auth/login
    // =========================================================================

    @Test
    @DisplayName("POST /api/auth/login - successful login returns 200 OK with valid JWT and user info")
    void login_withValidCredentials_shouldReturn200WithJwt() {
        String email = "login_success_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "CorrectPassword123!";
        User savedUser = createAndSaveUser(email, null, "Active Player", password);

        LoginRequest request = new LoginRequest(email, password);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                LOGIN_URL,
                request,
                AuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse authResponse = response.getBody();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.accessToken()).isNotBlank();
        assertThat(authResponse.tokenType()).isEqualTo("Bearer");
        assertThat(authResponse.userId()).isEqualTo(savedUser.getId().toString());
        assertThat(authResponse.email()).isEqualTo(email);
        assertThat(authResponse.role()).isEqualTo("CUSTOMER");

        // Verify that failedLoginAttempts is 0 in database
        User updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(updatedUser.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("POST /api/auth/login - invalid credentials (unknown email or wrong password) returns 401")
    void login_withInvalidCredentials_shouldReturn401Unauthorized() {
        String email = "login_fail_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        createAndSaveUser(email, null, "Existing Player", "CorrectPassword123!");

        // 1. Wrong password
        LoginRequest wrongPasswordRequest = new LoginRequest(email, "WrongPassword!");
        ResponseEntity<ErrorResponse> wrongPassResponse = restTemplate.postForEntity(
                LOGIN_URL,
                wrongPasswordRequest,
                ErrorResponse.class
        );
        assertThat(wrongPassResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassResponse.getBody()).isNotNull();
        assertThat(wrongPassResponse.getBody().error()).isEqualTo(ErrorCodes.INVALID_CREDENTIALS);

        // 2. Unknown email
        LoginRequest unknownEmailRequest = new LoginRequest("unknown_" + UUID.randomUUID() + "@example.com", "SomePass123!");
        ResponseEntity<ErrorResponse> unknownEmailResponse = restTemplate.postForEntity(
                LOGIN_URL,
                unknownEmailRequest,
                ErrorResponse.class
        );
        assertThat(unknownEmailResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmailResponse.getBody()).isNotNull();
        assertThat(unknownEmailResponse.getBody().error()).isEqualTo(ErrorCodes.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("POST /api/auth/login - self-deactivated account returns 403 Forbidden with ACCOUNT_DEACTIVATED")
    void login_whenAccountSelfDeactivated_shouldReturn403Forbidden() {
        String email = "self_deactivated_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "ValidPassword123!";

        User user = createAndSaveUser(email, null, "Self Deactivated User", password);
        user.deactivate(DeactivatedBy.SELF);
        userRepository.save(user);

        LoginRequest request = new LoginRequest(email, password);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                LOGIN_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.ACCOUNT_DEACTIVATED);
        assertThat(errorResponse.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("POST /api/auth/login - admin-deactivated account returns 403 Forbidden with ACCOUNT_DEACTIVATED_BY_ADMIN")
    void login_whenAccountAdminDeactivated_shouldReturn403Forbidden() {
        String email = "admin_deactivated_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "ValidPassword123!";

        User user = createAndSaveUser(email, null, "Admin Deactivated User", password);
        user.deactivate(DeactivatedBy.ADMIN);
        userRepository.save(user);

        LoginRequest request = new LoginRequest(email, password);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                LOGIN_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.ACCOUNT_DEACTIVATED_BY_ADMIN);
        assertThat(errorResponse.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("POST /api/auth/login - lockout after 5 failed attempts returns 423 Locked with Retry-After header")
    void login_lockoutAfter5FailedAttempts_shouldReturn423LockedWithRetryAfterHeader() {
        String email = "lockout_candidate_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String correctPassword = "CorrectPassword123!";
        User savedUser = createAndSaveUser(email, null, "Lockout Candidate", correctPassword);

        LoginRequest badRequest = new LoginRequest(email, "WrongPassword123!");

        // First 4 failed attempts should yield 401 UNAUTHORIZED
        for (int i = 1; i <= 4; i++) {
            ResponseEntity<ErrorResponse> resp = restTemplate.postForEntity(
                    LOGIN_URL,
                    badRequest,
                    ErrorResponse.class
            );
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().error()).isEqualTo(ErrorCodes.INVALID_CREDENTIALS);
        }

        // 5th failed attempt triggers account lockout: HTTP 423 LOCKED
        ResponseEntity<ErrorResponse> fifthResponse = restTemplate.postForEntity(
                LOGIN_URL,
                badRequest,
                ErrorResponse.class
        );

        assertThat(fifthResponse.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        ErrorResponse fifthError = fifthResponse.getBody();
        assertThat(fifthError).isNotNull();
        assertThat(fifthError.error()).isEqualTo(ErrorCodes.ACCOUNT_LOCKED);
        assertThat(fifthError.status()).isEqualTo(HttpStatus.LOCKED.value());

        // Verify Retry-After header presence and value
        String retryAfterHeader = fifthResponse.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfterHeader).isNotNull();
        long retryAfterSeconds = Long.parseLong(retryAfterHeader);
        assertThat(retryAfterSeconds).isGreaterThan(0).isLessThanOrEqualTo(900); // 15-minute window

        // Verify DB state: lockedUntil is set and failedLoginAttempts >= 5
        User lockedUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(lockedUser.isLocked()).isTrue();
        assertThat(lockedUser.getLockedUntil()).isNotNull();
        assertThat(lockedUser.getFailedLoginAttempts()).isGreaterThanOrEqualTo(5);

        // Subsequent attempt (even with CORRECT password) should also be rejected with 423 LOCKED
        LoginRequest correctRequest = new LoginRequest(email, correctPassword);
        ResponseEntity<ErrorResponse> subsequentResponse = restTemplate.postForEntity(
                LOGIN_URL,
                correctRequest,
                ErrorResponse.class
        );
        assertThat(subsequentResponse.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(subsequentResponse.getBody()).isNotNull();
        assertThat(subsequentResponse.getBody().error()).isEqualTo(ErrorCodes.ACCOUNT_LOCKED);
    }

    // =========================================================================
    // POST /api/auth/login/phone
    // =========================================================================

    @Test
    @DisplayName("POST /api/auth/login/phone - successful phone login returns 200 OK with valid JWT")
    void loginByPhone_withValidCredentials_shouldReturn200WithJwt() {
        String phone = "+9477" + UUID.randomUUID().toString().replaceAll("[^0-9]", "3").substring(0, 7);
        String password = "PhoneLoginSecret123!";
        User savedUser = createAndSaveUser(
                "phone_user_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                phone,
                "Phone User",
                password
        );

        PhoneLoginRequest request = new PhoneLoginRequest(phone, password);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                PHONE_LOGIN_URL,
                request,
                AuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse authResponse = response.getBody();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.accessToken()).isNotBlank();
        assertThat(authResponse.tokenType()).isEqualTo("Bearer");
        assertThat(authResponse.userId()).isEqualTo(savedUser.getId().toString());
        assertThat(authResponse.role()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("POST /api/auth/login/phone - invalid phone or wrong password returns 401 Unauthorized")
    void loginByPhone_withInvalidCredentials_shouldReturn401Unauthorized() {
        String phone = "+9477" + UUID.randomUUID().toString().replaceAll("[^0-9]", "4").substring(0, 7);
        createAndSaveUser(
                "phone_user2_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                phone,
                "Phone User 2",
                "CorrectPassword123!"
        );

        // 1. Existing phone, wrong password
        PhoneLoginRequest wrongPasswordRequest = new PhoneLoginRequest(phone, "WrongPassword123!");
        ResponseEntity<ErrorResponse> wrongPassResponse = restTemplate.postForEntity(
                PHONE_LOGIN_URL,
                wrongPasswordRequest,
                ErrorResponse.class
        );
        assertThat(wrongPassResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassResponse.getBody()).isNotNull();
        assertThat(wrongPassResponse.getBody().error()).isEqualTo(ErrorCodes.INVALID_CREDENTIALS);

        // 2. Unknown phone number
        PhoneLoginRequest unknownPhoneRequest = new PhoneLoginRequest("+94779998888", "AnyPassword123!");
        ResponseEntity<ErrorResponse> unknownPhoneResponse = restTemplate.postForEntity(
                PHONE_LOGIN_URL,
                unknownPhoneRequest,
                ErrorResponse.class
        );
        assertThat(unknownPhoneResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownPhoneResponse.getBody()).isNotNull();
        assertThat(unknownPhoneResponse.getBody().error()).isEqualTo(ErrorCodes.INVALID_CREDENTIALS);
    }

    // =========================================================================
    // POST /api/auth/password/forgot
    // =========================================================================

    @Test
    @DisplayName("POST /api/auth/password/forgot - known email returns 202 Accepted and issues email verification code")
    void forgotPassword_withKnownEmail_shouldReturn202AndIssueEmailCode() {
        String email = "forgot_email_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Forgot Password Email User", "Password123!");

        ForgotPasswordRequest request = new ForgotPasswordRequest(email);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                FORGOT_PASSWORD_URL,
                request,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Verify code issuance in DB
        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        PasswordResetToken token = tokens.get(0);
        assertThat(token.getUser().getId()).isEqualTo(user.getId());
        assertThat(token.getChannel()).isEqualTo(PasswordResetChannel.EMAIL);
        assertThat(token.getPurpose()).isEqualTo(VerificationPurpose.PASSWORD_RESET);
        assertThat(token.isUsed()).isFalse();
        assertThat(token.getFailedAttempts()).isEqualTo(0);
        assertThat(token.getExpiresAt()).isAfter(Instant.now());

        // Verify notification dispatch
        verify(notificationClient, atLeastOnce()).sendPasswordResetEmail(eq(email), anyString());
    }

    @Test
    @DisplayName("POST /api/auth/password/forgot - known phone returns 202 Accepted and issues SMS verification code")
    void forgotPassword_withKnownPhone_shouldReturn202AndIssueSmsCode() {
        String phone = "+9477" + UUID.randomUUID().toString().replaceAll("[^0-9]", "5").substring(0, 7);
        User user = createAndSaveUser(
                "forgot_phone_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                phone,
                "Forgot Password Phone User",
                "Password123!"
        );

        ForgotPasswordRequest request = new ForgotPasswordRequest(phone);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                FORGOT_PASSWORD_URL,
                request,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Verify code issuance in DB
        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        PasswordResetToken token = tokens.get(0);
        assertThat(token.getUser().getId()).isEqualTo(user.getId());
        assertThat(token.getChannel()).isEqualTo(PasswordResetChannel.PHONE);
        assertThat(token.getPurpose()).isEqualTo(VerificationPurpose.PASSWORD_RESET);
        assertThat(token.isUsed()).isFalse();

        // Verify SMS notification dispatch
        verify(notificationClient, atLeastOnce()).sendPasswordResetSms(eq(phone), anyString());
    }

    @Test
    @DisplayName("POST /api/auth/password/forgot - unknown identifier returns 202 Accepted (anti-enumeration) without issuing token")
    void forgotPassword_withUnknownIdentifier_shouldReturn202AndNotIssueCode() {
        String unknownEmail = "unknown_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        ForgotPasswordRequest request = new ForgotPasswordRequest(unknownEmail);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                FORGOT_PASSWORD_URL,
                request,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(passwordResetTokenRepository.count()).isEqualTo(0);
        verify(notificationClient, never()).sendPasswordResetEmail(anyString(), anyString());
        verify(notificationClient, never()).sendPasswordResetSms(anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/auth/password/forgot - blank identifier returns 400 Bad Request with VALIDATION_ERROR")
    void forgotPassword_withBlankIdentifier_shouldReturn400BadRequest() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("   ");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                FORGOT_PASSWORD_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThat(errorResponse.fieldErrors()).containsKey("identifier");
    }

    @Test
    @DisplayName("POST /api/auth/password/forgot - rate limit exceeded prevents issuing more than 3 codes per window")
    void forgotPassword_rateLimitExceeded_shouldNotIssueMoreThanMaxCodesPerWindow() {
        String email = "ratelimit_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        createAndSaveUser(email, null, "Rate Limit User", "Password123!");
        ForgotPasswordRequest request = new ForgotPasswordRequest(email);

        // Issue 3 codes (the maximum allowed within 15 minutes)
        for (int i = 0; i < 3; i++) {
            ResponseEntity<Void> resp = restTemplate.postForEntity(FORGOT_PASSWORD_URL, request, Void.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
        assertThat(passwordResetTokenRepository.count()).isEqualTo(3);

        // 4th request within the window should still return 202 but not create a new token in DB
        ResponseEntity<Void> fourthResp = restTemplate.postForEntity(FORGOT_PASSWORD_URL, request, Void.class);
        assertThat(fourthResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(passwordResetTokenRepository.count()).isEqualTo(3);
    }

    // =========================================================================
    // POST /api/auth/password/reset
    // =========================================================================

    @Test
    @DisplayName("POST /api/auth/password/reset - valid OTP updates password, resets lockout, and returns 200 OK")
    void resetPassword_withValidOtp_shouldUpdatePasswordAndReturn200() {
        String email = "reset_valid_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Reset Password User", "OldPassword123!");
        user.setFailedLoginAttempts(3);
        userRepository.save(user);

        String otp = "123456";
        PasswordResetToken token = createAndSaveToken(user, otp, VerificationPurpose.PASSWORD_RESET,
                PasswordResetChannel.EMAIL, Instant.now().plus(15, ChronoUnit.MINUTES), 0);

        String newPassword = "BrandNewPassword123!";
        ResetPasswordRequest request = new ResetPasswordRequest(email, otp, newPassword);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                RESET_PASSWORD_URL,
                request,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify password updated in DB
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(newPassword, updatedUser.getPasswordHash())).isTrue();
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(updatedUser.getLockedUntil()).isNull();

        // Verify token marked as used
        PasswordResetToken updatedToken = passwordResetTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(updatedToken.isUsed()).isTrue();
    }

    @Test
    @DisplayName("POST /api/auth/password/reset - invalid OTP returns 400 Bad Request and increments failedAttempts")
    void resetPassword_withInvalidOtp_shouldReturn400BadRequestAndIncrementFailedAttempts() {
        String email = "reset_invalid_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Invalid OTP User", "OldPassword123!");

        PasswordResetToken token = createAndSaveToken(user, "123456", VerificationPurpose.PASSWORD_RESET,
                PasswordResetChannel.EMAIL, Instant.now().plus(15, ChronoUnit.MINUTES), 0);

        ResetPasswordRequest request = new ResetPasswordRequest(email, "999999", "NewPassword123!");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                RESET_PASSWORD_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.INVALID_RESET_CODE);

        // Verify token failedAttempts incremented to 1, still not used
        PasswordResetToken updatedToken = passwordResetTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(updatedToken.getFailedAttempts()).isEqualTo(1);
        assertThat(updatedToken.isUsed()).isFalse();

        // Verify password remained unchanged
        User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassword123!", unchangedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("POST /api/auth/password/reset - expired OTP returns 400 Bad Request with INVALID_RESET_CODE")
    void resetPassword_withExpiredOtp_shouldReturn400BadRequest() {
        String email = "reset_expired_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Expired OTP User", "OldPassword123!");

        // Expired 5 minutes ago
        createAndSaveToken(user, "123456", VerificationPurpose.PASSWORD_RESET,
                PasswordResetChannel.EMAIL, Instant.now().minus(5, ChronoUnit.MINUTES), 0);

        ResetPasswordRequest request = new ResetPasswordRequest(email, "123456", "NewPassword123!");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                RESET_PASSWORD_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.INVALID_RESET_CODE);
    }

    @Test
    @DisplayName("POST /api/auth/password/reset - burned OTP after 5 failed attempts rejects further reset attempts")
    void resetPassword_burnOtpAfter5FailedAttempts_shouldBurnCodeAndRejectSubsequentAttempts() {
        String email = "reset_burned_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Burned OTP User", "OldPassword123!");

        // Token currently at 4 failed attempts (limit is 5)
        PasswordResetToken token = createAndSaveToken(user, "123456", VerificationPurpose.PASSWORD_RESET,
                PasswordResetChannel.EMAIL, Instant.now().plus(15, ChronoUnit.MINUTES), 4);

        // 5th failed attempt: burns the token
        ResetPasswordRequest wrongRequest = new ResetPasswordRequest(email, "000000", "NewPassword123!");
        ResponseEntity<ErrorResponse> fifthResponse = restTemplate.postForEntity(
                RESET_PASSWORD_URL,
                wrongRequest,
                ErrorResponse.class
        );

        assertThat(fifthResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fifthResponse.getBody()).isNotNull();
        assertThat(fifthResponse.getBody().error()).isEqualTo(ErrorCodes.INVALID_RESET_CODE);

        // Verify token in DB is now burned: failedAttempts = 5 and used = true
        PasswordResetToken burnedToken = passwordResetTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(burnedToken.getFailedAttempts()).isEqualTo(5);
        assertThat(burnedToken.isUsed()).isTrue();

        // Attempting to submit the CORRECT code now must still fail because the token is burned
        ResetPasswordRequest correctRequest = new ResetPasswordRequest(email, "123456", "NewPassword123!");
        ResponseEntity<ErrorResponse> burnedResponse = restTemplate.postForEntity(
                RESET_PASSWORD_URL,
                correctRequest,
                ErrorResponse.class
        );

        assertThat(burnedResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(burnedResponse.getBody()).isNotNull();
        assertThat(burnedResponse.getBody().error()).isEqualTo(ErrorCodes.INVALID_RESET_CODE);
    }

    @Test
    @DisplayName("POST /api/auth/password/reset - validation failure returns 400 Bad Request with field errors")
    void resetPassword_withValidationFailure_shouldReturn400BadRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest("", "", "short");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                RESET_PASSWORD_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThat(errorResponse.fieldErrors()).containsKey("identifier");
        assertThat(errorResponse.fieldErrors()).containsKey("code");
        assertThat(errorResponse.fieldErrors()).containsKey("newPassword");
    }

    // =========================================================================
    // POST /api/auth/reactivate/request
    // =========================================================================

    @Test
    @DisplayName("POST /api/auth/reactivate/request - self-deactivated account returns 202 Accepted and issues code")
    void reactivateRequest_withSelfDeactivatedAccount_shouldReturn202AndIssueEmailCode() {
        String email = "reactivate_self_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Self Deactivated User", "Password123!");
        user.deactivate(DeactivatedBy.SELF);
        userRepository.save(user);

        ReactivateAccountRequest request = new ReactivateAccountRequest(email);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                REACTIVATE_REQUEST_URL,
                request,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Verify code issuance in DB with ACCOUNT_REACTIVATION purpose
        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        PasswordResetToken token = tokens.get(0);
        assertThat(token.getUser().getId()).isEqualTo(user.getId());
        assertThat(token.getPurpose()).isEqualTo(VerificationPurpose.ACCOUNT_REACTIVATION);
        assertThat(token.getChannel()).isEqualTo(PasswordResetChannel.EMAIL);
        assertThat(token.isUsed()).isFalse();

        // Verify reactivation email notification dispatch
        verify(notificationClient, atLeastOnce()).sendAccountReactivationEmail(eq(email), anyString());
    }

    @Test
    @DisplayName("POST /api/auth/reactivate/request - active or admin-deactivated account returns 202 (anti-enumeration) without issuing code")
    void reactivateRequest_withActiveOrAdminDeactivatedAccount_shouldReturn202AndNotIssueCode() {
        // 1. Active user
        String activeEmail = "active_user_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        createAndSaveUser(activeEmail, null, "Active User", "Password123!");

        ResponseEntity<Void> activeResp = restTemplate.postForEntity(
                REACTIVATE_REQUEST_URL,
                new ReactivateAccountRequest(activeEmail),
                Void.class
        );
        assertThat(activeResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(passwordResetTokenRepository.count()).isEqualTo(0);

        // 2. Admin-deactivated user
        String adminDeactivatedEmail = "admin_deact_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User adminUser = createAndSaveUser(adminDeactivatedEmail, null, "Admin Deactivated User", "Password123!");
        adminUser.deactivate(DeactivatedBy.ADMIN);
        userRepository.save(adminUser);

        ResponseEntity<Void> adminDeactResp = restTemplate.postForEntity(
                REACTIVATE_REQUEST_URL,
                new ReactivateAccountRequest(adminDeactivatedEmail),
                Void.class
        );
        assertThat(adminDeactResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(passwordResetTokenRepository.count()).isEqualTo(0);

        // Verify no reactivation email was ever sent
        verify(notificationClient, never()).sendAccountReactivationEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/auth/reactivate/request - unknown identifier returns 202 Accepted without issuing code")
    void reactivateRequest_withUnknownIdentifier_shouldReturn202AndNotIssueCode() {
        String unknownEmail = "unknown_reactivate_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        ReactivateAccountRequest request = new ReactivateAccountRequest(unknownEmail);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                REACTIVATE_REQUEST_URL,
                request,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(passwordResetTokenRepository.count()).isEqualTo(0);
        verify(notificationClient, never()).sendAccountReactivationEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/auth/reactivate/request - blank identifier returns 400 Bad Request with VALIDATION_ERROR")
    void reactivateRequest_withBlankIdentifier_shouldReturn400BadRequest() {
        ReactivateAccountRequest request = new ReactivateAccountRequest("   ");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                REACTIVATE_REQUEST_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThat(errorResponse.fieldErrors()).containsKey("identifier");
    }

    // =========================================================================
    // POST /api/auth/reactivate/confirm
    // =========================================================================

    @Test
    @DisplayName("POST /api/auth/reactivate/confirm - valid OTP reactivates account and returns 200 OK with JWT")
    void reactivateConfirm_withValidOtp_shouldReactivateAccountAndReturn200WithJwt() {
        String email = "confirm_valid_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Confirm Reactivate User", "Password123!");
        user.deactivate(DeactivatedBy.SELF);
        user.setFailedLoginAttempts(3);
        userRepository.save(user);

        String otp = "654321";
        PasswordResetToken token = createAndSaveToken(user, otp, VerificationPurpose.ACCOUNT_REACTIVATION,
                PasswordResetChannel.EMAIL, Instant.now().plus(15, ChronoUnit.MINUTES), 0);

        ConfirmReactivationRequest request = new ConfirmReactivationRequest(email, otp);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                REACTIVATE_CONFIRM_URL,
                request,
                AuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse authResponse = response.getBody();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.accessToken()).isNotBlank();
        assertThat(authResponse.tokenType()).isEqualTo("Bearer");
        assertThat(authResponse.userId()).isEqualTo(user.getId().toString());
        assertThat(authResponse.email()).isEqualTo(email);
        assertThat(authResponse.role()).isEqualTo("CUSTOMER");

        // Verify account is reactivated in DB
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.isEnabled()).isTrue();
        assertThat(updatedUser.getDeactivatedBy()).isNull();
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(updatedUser.getLockedUntil()).isNull();

        // Verify token is consumed
        PasswordResetToken updatedToken = passwordResetTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(updatedToken.isUsed()).isTrue();
    }

    @Test
    @DisplayName("POST /api/auth/reactivate/confirm - invalid OTP returns 400 Bad Request and account remains deactivated")
    void reactivateConfirm_withInvalidOtp_shouldReturn400BadRequestAndRemainDeactivated() {
        String email = "confirm_invalid_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Invalid Confirm User", "Password123!");
        user.deactivate(DeactivatedBy.SELF);
        userRepository.save(user);

        createAndSaveToken(user, "654321", VerificationPurpose.ACCOUNT_REACTIVATION,
                PasswordResetChannel.EMAIL, Instant.now().plus(15, ChronoUnit.MINUTES), 0);

        ConfirmReactivationRequest request = new ConfirmReactivationRequest(email, "000000");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                REACTIVATE_CONFIRM_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.INVALID_RESET_CODE);

        // Account must remain deactivated
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.isEnabled()).isFalse();
        assertThat(updatedUser.getDeactivatedBy()).isEqualTo(DeactivatedBy.SELF);
    }

    @Test
    @DisplayName("POST /api/auth/reactivate/confirm - admin-deactivated account returns 400 Bad Request with INVALID_RESET_CODE")
    void reactivateConfirm_withAdminDeactivatedAccount_shouldReturn400BadRequest() {
        String email = "confirm_admin_deact_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Admin Deactivated User", "Password123!");
        user.deactivate(DeactivatedBy.ADMIN);
        userRepository.save(user);

        ConfirmReactivationRequest request = new ConfirmReactivationRequest(email, "654321");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                REACTIVATE_CONFIRM_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.INVALID_RESET_CODE);
    }

    @Test
    @DisplayName("POST /api/auth/reactivate/confirm - validation failure returns 400 Bad Request with field errors")
    void reactivateConfirm_withValidationFailure_shouldReturn400BadRequest() {
        ConfirmReactivationRequest request = new ConfirmReactivationRequest("", "");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                REACTIVATE_CONFIRM_URL,
                request,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThat(errorResponse.fieldErrors()).containsKey("identifier");
        assertThat(errorResponse.fieldErrors()).containsKey("code");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private User createAndSaveUser(String email, String phone, String fullName, String rawPassword) {
        User user = User.builder()
                .email(email.toLowerCase().trim())
                .phoneNumber(phone)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(Role.CUSTOMER)
                .enabled(true)
                .failedLoginAttempts(0)
                .build();
        return userRepository.save(user);
    }

    private PasswordResetToken createAndSaveToken(User user, String rawCode, VerificationPurpose purpose,
                                                  PasswordResetChannel channel, Instant expiresAt, int failedAttempts) {
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(rawCode))
                .channel(channel)
                .purpose(purpose)
                .expiresAt(expiresAt)
                .used(false)
                .failedAttempts(failedAttempts)
                .build();
        return passwordResetTokenRepository.save(token);
    }
}
