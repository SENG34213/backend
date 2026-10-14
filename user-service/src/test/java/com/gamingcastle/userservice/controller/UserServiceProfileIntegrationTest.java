package com.gamingcastle.userservice.controller;

import com.gamingcastle.userservice.client.NotificationClient;
import com.gamingcastle.userservice.dto.request.UpdateProfileRequest;
import com.gamingcastle.userservice.dto.response.ErrorResponse;
import com.gamingcastle.userservice.dto.response.UserProfileResponse;
import com.gamingcastle.userservice.entity.DeactivatedBy;
import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.exception.ErrorCodes;
import com.gamingcastle.userservice.repository.PasswordResetTokenRepository;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.security.GatewayAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the user profile & self-management endpoints in UserController:
 * - GET /api/users/me
 * - PATCH /api/users/me
 * - PATCH /api/users/me/deactivate
 *
 * Runs against the real Spring Boot application context with the dedicated PostgreSQL test database.
 * Exercises real HTTP requests through GatewayAuthenticationFilter to test security, header enforcement,
 * persistence, and business logic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserServiceProfileIntegrationTest {

    private static final String PROFILE_URL = "/api/users/me";
    private static final String DEACTIVATE_ME_URL = "/api/users/me/deactivate";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    @MockBean
    private NotificationClient notificationClient;

    @BeforeEach
    void cleanDatabase() {
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private HttpHeaders authorizedHeaders(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, gatewaySecret);
        headers.set(GatewayAuthenticationFilter.USER_ID_HEADER, userId.toString());
        return headers;
    }

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

    // =========================================================================
    // 1. GET /api/users/me
    // =========================================================================

    @Test
    @DisplayName("GET /api/users/me - valid authenticated user returns 200 with full profile data")
    void getMyProfile_withValidAuthenticatedUser_shouldReturn200WithProfileData() {
        String email = "profile_user_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String phone = "+94771234567";
        String fullName = "Alice Smith";
        User user = createAndSaveUser(email, phone, fullName, "Password123!");

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(user.getId()));

        ResponseEntity<UserProfileResponse> response = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.GET,
                entity,
                UserProfileResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserProfileResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(user.getId());
        assertThat(body.email()).isEqualTo(email.toLowerCase());
        assertThat(body.fullName()).isEqualTo(fullName);
        assertThat(body.phoneNumber()).isEqualTo(phone);
        assertThat(body.role()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("GET /api/users/me - missing or invalid X-Gateway-Secret is rejected with 401 Unauthorized")
    void getMyProfile_withMissingOrInvalidGatewaySecret_shouldReturn401Unauthorized() {
        User user = createAndSaveUser("no_secret_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                null, "No Secret User", "Password123!");

        // 1. Completely missing X-Gateway-Secret header
        HttpHeaders headersWithoutSecret = new HttpHeaders();
        headersWithoutSecret.set(GatewayAuthenticationFilter.USER_ID_HEADER, user.getId().toString());

        ResponseEntity<String> responseNoSecret = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.GET,
                new HttpEntity<>(headersWithoutSecret),
                String.class
        );
        assertThat(responseNoSecret.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseNoSecret.getBody()).contains("Request must come through the API Gateway");

        // 2. Invalid X-Gateway-Secret header
        HttpHeaders headersWrongSecret = new HttpHeaders();
        headersWrongSecret.set(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, "wrong-secret-token");
        headersWrongSecret.set(GatewayAuthenticationFilter.USER_ID_HEADER, user.getId().toString());

        ResponseEntity<String> responseWrongSecret = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.GET,
                new HttpEntity<>(headersWrongSecret),
                String.class
        );
        assertThat(responseWrongSecret.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseWrongSecret.getBody()).contains("Request must come through the API Gateway");
    }

    @Test
    @DisplayName("GET /api/users/me - missing or invalid X-User-Id is rejected with 401 Unauthorized")
    void getMyProfile_withMissingOrInvalidUserId_shouldReturn401Unauthorized() {
        // 1. Missing X-User-Id header
        HttpHeaders headersNoUserId = new HttpHeaders();
        headersNoUserId.set(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, gatewaySecret);

        ResponseEntity<String> responseNoUserId = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.GET,
                new HttpEntity<>(headersNoUserId),
                String.class
        );
        assertThat(responseNoUserId.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseNoUserId.getBody()).contains("Missing or invalid user id");

        // 2. Malformed X-User-Id header (not a UUID)
        HttpHeaders headersMalformedUserId = new HttpHeaders();
        headersMalformedUserId.set(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, gatewaySecret);
        headersMalformedUserId.set(GatewayAuthenticationFilter.USER_ID_HEADER, "not-a-valid-uuid");

        ResponseEntity<String> responseMalformed = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.GET,
                new HttpEntity<>(headersMalformedUserId),
                String.class
        );
        assertThat(responseMalformed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseMalformed.getBody()).contains("Missing or invalid user id");
    }

    @Test
    @DisplayName("GET /api/users/me - unknown user id is rejected with 401 Unauthorized")
    void getMyProfile_withUnknownUser_shouldReturn401Unauthorized() {
        UUID unknownUserId = UUID.randomUUID();
        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(unknownUserId));

        ResponseEntity<String> response = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Unknown user");
    }

    @Test
    @DisplayName("GET /api/users/me - deactivated user is rejected with 401 Unauthorized")
    void getMyProfile_withDeactivatedUser_shouldReturn401Unauthorized() {
        String email = "deactivated_get_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Deactivated User", "Password123!");
        user.deactivate(DeactivatedBy.SELF);
        userRepository.save(user);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(user.getId()));

        ResponseEntity<String> response = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Account is deactivated");
    }

    // =========================================================================
    // 2. PATCH /api/users/me
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/users/me - valid fullName update returns 200, updates DB, and preserves other fields")
    void updateCurrentUser_withValidFullName_shouldReturn200AndPersistInDb() {
        String email = "update_user_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String initialPhone = "+94771112233";
        User user = createAndSaveUser(email, initialPhone, "Original Name", "Password123!");

        UpdateProfileRequest request = new UpdateProfileRequest("Updated New Name", initialPhone);
        HttpEntity<UpdateProfileRequest> entity = new HttpEntity<>(request, authorizedHeaders(user.getId()));

        ResponseEntity<UserProfileResponse> response = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.PATCH,
                entity,
                UserProfileResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserProfileResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.fullName()).isEqualTo("Updated New Name");
        assertThat(body.phoneNumber()).isEqualTo(initialPhone);
        assertThat(body.email()).isEqualTo(email.toLowerCase());
        assertThat(body.role()).isEqualTo("CUSTOMER");

        // Verify in database: fullName changed, email/role/enabled preserved
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getFullName()).isEqualTo("Updated New Name");
        assertThat(updatedUser.getPhoneNumber()).isEqualTo(initialPhone);
        assertThat(updatedUser.getEmail()).isEqualTo(email.toLowerCase());
        assertThat(updatedUser.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(updatedUser.isEnabled()).isTrue();
        assertThat(updatedUser.getDeactivatedBy()).isNull();
    }

    @Test
    @DisplayName("PATCH /api/users/me - optional phoneNumber update updates phone and preserves other fields")
    void updateCurrentUser_withOptionalPhoneNumberUpdate_shouldUpdatePhoneAndPreserveOtherFields() {
        String email = "update_phone_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, "+94770001111", "Constant Name", "Password123!");

        String newPhone = "+94779876543";
        UpdateProfileRequest request = new UpdateProfileRequest("Constant Name", newPhone);
        HttpEntity<UpdateProfileRequest> entity = new HttpEntity<>(request, authorizedHeaders(user.getId()));

        ResponseEntity<UserProfileResponse> response = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.PATCH,
                entity,
                UserProfileResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().phoneNumber()).isEqualTo(newPhone);

        // Verify database state
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getPhoneNumber()).isEqualTo(newPhone);
        assertThat(updatedUser.getFullName()).isEqualTo("Constant Name");
        assertThat(updatedUser.getEmail()).isEqualTo(email.toLowerCase());
        assertThat(updatedUser.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(updatedUser.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("PATCH /api/users/me - blank fullName validation returns 400 Bad Request with field errors")
    void updateCurrentUser_withBlankFullName_shouldReturn400BadRequest() {
        User user = createAndSaveUser("blank_name_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                null, "Initial Name", "Password123!");

        UpdateProfileRequest request = new UpdateProfileRequest("   ", "+94771234567");
        HttpEntity<UpdateProfileRequest> entity = new HttpEntity<>(request, authorizedHeaders(user.getId()));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.PATCH,
                entity,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.error()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThat(errorResponse.fieldErrors()).containsKey("fullName");

        // Verify DB state was NOT modified
        User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unchangedUser.getFullName()).isEqualTo("Initial Name");
    }

    @Test
    @DisplayName("PATCH /api/users/me - null fullName validation returns 400 Bad Request with VALIDATION_ERROR")
    void updateCurrentUser_withNullFullName_shouldReturn400BadRequest() {
        User user = createAndSaveUser("null_name_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                null, "Initial Name", "Password123!");

        UpdateProfileRequest request = new UpdateProfileRequest(null, "+94771234567");
        HttpEntity<UpdateProfileRequest> entity = new HttpEntity<>(request, authorizedHeaders(user.getId()));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.PATCH,
                entity,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThat(response.getBody().fieldErrors()).containsKey("fullName");
    }

    // =========================================================================
    // 3. PATCH /api/users/me/deactivate
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/users/me/deactivate - active user successfully self-deactivates returning 204 No Content")
    void deactivateMyAccount_whenActive_shouldReturn204AndSetDeactivatedBySelf() {
        String email = "deact_success_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Active To Deactivate", "Password123!");

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(user.getId()));

        ResponseEntity<Void> response = restTemplate.exchange(
                DEACTIVATE_ME_URL,
                HttpMethod.PATCH,
                entity,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify DB persistence: enabled=false, deactivatedBy=SELF
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.isEnabled()).isFalse();
        assertThat(updatedUser.getDeactivatedBy()).isEqualTo(DeactivatedBy.SELF);
    }

    @Test
    @DisplayName("PATCH /api/users/me/deactivate - second self-deactivation is rejected and deactivated user cannot access protected endpoints")
    void deactivateMyAccount_whenAlreadyDeactivated_shouldBeRejectedAndBlockAccess() {
        String email = "deact_repeat_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User user = createAndSaveUser(email, null, "Repeat Deactivate User", "Password123!");
        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(user.getId()));

        // First deactivation succeeds
        ResponseEntity<Void> firstResponse = restTemplate.exchange(
                DEACTIVATE_ME_URL,
                HttpMethod.PATCH,
                entity,
                Void.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify account is deactivated in database
        User dbUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(dbUser.isEnabled()).isFalse();
        assertThat(dbUser.getDeactivatedBy()).isEqualTo(DeactivatedBy.SELF);

        // 1. Second self-deactivation attempt:
        // GatewayAuthenticationFilter blocks deactivated accounts with 401 Unauthorized ("Account is deactivated")
        ResponseEntity<String> secondResponse = restTemplate.exchange(
                DEACTIVATE_ME_URL,
                HttpMethod.PATCH,
                entity,
                String.class
        );
        assertThat(secondResponse.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.CONFLICT);
        if (secondResponse.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            assertThat(secondResponse.getBody()).contains("Account is deactivated");
        }

        // 2. Verify the deactivated user can no longer access protected endpoints (e.g. GET /api/users/me)
        ResponseEntity<String> profileAccessResponse = restTemplate.exchange(
                PROFILE_URL,
                HttpMethod.GET,
                entity,
                String.class
        );
        assertThat(profileAccessResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(profileAccessResponse.getBody()).contains("Account is deactivated");
    }
}
