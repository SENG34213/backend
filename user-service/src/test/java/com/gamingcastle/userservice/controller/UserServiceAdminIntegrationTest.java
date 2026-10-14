package com.gamingcastle.userservice.controller;

import com.gamingcastle.userservice.client.NotificationClient;
import com.gamingcastle.userservice.dto.response.ErrorResponse;
import com.gamingcastle.userservice.dto.response.UserSummaryResponse;
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
 * Integration tests for the administrative endpoints in UserController:
 * - GET /api/users (paginated listing)
 * - GET /api/users/{userId} (user details by id)
 * - PATCH /api/users/{userId}/deactivate (admin deactivation / takeover)
 * - PATCH /api/users/{userId}/activate (admin reactivation)
 *
 * Verifies role-based access control (ROLE_ADMIN vs non-admin), Gateway authentication,
 * error handling (404, 409, 403), and database persistence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserServiceAdminIntegrationTest {

    private static final String USERS_URL = "/api/users";

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

    private User createAndSaveUser(String email, String phone, String fullName, Role role) {
        User user = User.builder()
                .email(email.toLowerCase().trim())
                .phoneNumber(phone)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode("SecurePass123!"))
                .role(role)
                .enabled(true)
                .failedLoginAttempts(0)
                .build();
        return userRepository.save(user);
    }

    // =========================================================================
    // 1. GET /api/users (Paginated user listing)
    // =========================================================================

    @Test
    @DisplayName("GET /api/users - admin caller returns 200 OK with paginated user summaries")
    void getAllUsers_asAdmin_shouldReturn200AndPagedSummaries() {
        User admin = createAndSaveUser("admin_list@example.com", "+94770000001", "Admin Boss", Role.ADMIN);
        User user1 = createAndSaveUser("player1_list@example.com", "+94770000002", "Player One", Role.CUSTOMER);
        User user2 = createAndSaveUser("player2_list@example.com", "+94770000003", "Player Two", Role.CUSTOMER);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(admin.getId()));

        ResponseEntity<String> response = restTemplate.exchange(
                USERS_URL + "?page=0&size=10",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("totalElements\":3");
        assertThat(body).contains("admin_list@example.com");
        assertThat(body).contains("player1_list@example.com");
        assertThat(body).contains("player2_list@example.com");
    }

    @Test
    @DisplayName("GET /api/users - customer caller is denied with 403 Forbidden")
    void getAllUsers_asCustomer_shouldReturn403Forbidden() {
        User customer = createAndSaveUser("player_forbidden@example.com", "+94770000004", "Regular Player", Role.CUSTOMER);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(customer.getId()));

        ResponseEntity<String> response = restTemplate.exchange(
                USERS_URL,
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 2. GET /api/users/{userId} (User details by ID)
    // =========================================================================

    @Test
    @DisplayName("GET /api/users/{userId} - admin caller returns 200 OK with user summary")
    void getUserById_asAdmin_withExistingUser_shouldReturn200AndSummary() {
        User admin = createAndSaveUser("admin_get@example.com", null, "Admin Viewer", Role.ADMIN);
        User target = createAndSaveUser("target_get@example.com", "+94771112233", "Target Player", Role.CUSTOMER);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(admin.getId()));

        ResponseEntity<UserSummaryResponse> response = restTemplate.exchange(
                USERS_URL + "/" + target.getId(),
                HttpMethod.GET,
                entity,
                UserSummaryResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserSummaryResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(target.getId());
        assertThat(body.email()).isEqualTo("target_get@example.com");
        assertThat(body.fullName()).isEqualTo("Target Player");
        assertThat(body.phoneNumber()).isEqualTo("+94771112233");
        assertThat(body.role()).isEqualTo("CUSTOMER");
        assertThat(body.enabled()).isTrue();
        assertThat(body.deactivatedBy()).isNull();
    }

    @Test
    @DisplayName("GET /api/users/{userId} - admin caller querying non-existent user returns 404 Not Found")
    void getUserById_asAdmin_withUnknownUser_shouldReturn404NotFound() {
        User admin = createAndSaveUser("admin_unknown@example.com", null, "Admin Query", Role.ADMIN);
        UUID nonExistentId = UUID.randomUUID();

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(admin.getId()));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                USERS_URL + "/" + nonExistentId,
                HttpMethod.GET,
                entity,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/users/{userId} - customer caller is denied with 403 Forbidden")
    void getUserById_asCustomer_shouldReturn403Forbidden() {
        User customer = createAndSaveUser("player_view@example.com", null, "Customer", Role.CUSTOMER);
        User target = createAndSaveUser("other_target@example.com", null, "Other", Role.CUSTOMER);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(customer.getId()));

        ResponseEntity<String> response = restTemplate.exchange(
                USERS_URL + "/" + target.getId(),
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 3. PATCH /api/users/{userId}/deactivate (Admin deactivation / takeover)
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/users/{userId}/deactivate - admin deactivates active target returning 204 No Content")
    void deactivateUser_asAdmin_onActiveTarget_shouldReturn204AndSetDeactivatedByAdmin() {
        User admin = createAndSaveUser("admin_deact@example.com", null, "Admin Actor", Role.ADMIN);
        User target = createAndSaveUser("target_active@example.com", null, "Active Target", Role.CUSTOMER);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(admin.getId()));

        ResponseEntity<Void> response = restTemplate.exchange(
                USERS_URL + "/" + target.getId() + "/deactivate",
                HttpMethod.PATCH,
                entity,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify in database: enabled=false, deactivatedBy=ADMIN
        User updatedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertThat(updatedTarget.isEnabled()).isFalse();
        assertThat(updatedTarget.getDeactivatedBy()).isEqualTo(DeactivatedBy.ADMIN);
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/deactivate - admin takes over self-deactivated account returning 204")
    void deactivateUser_asAdmin_onSelfDeactivatedTarget_shouldTakeOverAndSetDeactivatedByAdmin() {
        User admin = createAndSaveUser("admin_takeover@example.com", null, "Admin Takeover", Role.ADMIN);
        User target = createAndSaveUser("target_self_deact@example.com", null, "Self Deactivated", Role.CUSTOMER);
        target.deactivate(DeactivatedBy.SELF);
        userRepository.save(target);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(admin.getId()));

        ResponseEntity<Void> response = restTemplate.exchange(
                USERS_URL + "/" + target.getId() + "/deactivate",
                HttpMethod.PATCH,
                entity,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify in database: deactivatedBy changed from SELF to ADMIN
        User updatedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertThat(updatedTarget.isEnabled()).isFalse();
        assertThat(updatedTarget.getDeactivatedBy()).isEqualTo(DeactivatedBy.ADMIN);
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/deactivate - deactivating already admin-deactivated user returns 409 Conflict")
    void deactivateUser_asAdmin_onAlreadyAdminDeactivatedTarget_shouldReturn409Conflict() {
        User admin = createAndSaveUser("admin_already@example.com", null, "Admin Checking", Role.ADMIN);
        User target = createAndSaveUser("target_already_deact@example.com", null, "Already Deactivated", Role.CUSTOMER);
        target.deactivate(DeactivatedBy.ADMIN);
        userRepository.save(target);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(admin.getId()));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                USERS_URL + "/" + target.getId() + "/deactivate",
                HttpMethod.PATCH,
                entity,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.USER_ALREADY_DEACTIVATED);
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/deactivate - deactivating unknown user returns 404 Not Found")
    void deactivateUser_asAdmin_onUnknownUser_shouldReturn404NotFound() {
        User admin = createAndSaveUser("admin_notfound@example.com", null, "Admin Deact", Role.ADMIN);
        UUID nonExistentId = UUID.randomUUID();

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(admin.getId()));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                USERS_URL + "/" + nonExistentId + "/deactivate",
                HttpMethod.PATCH,
                entity,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/deactivate - customer caller is denied with 403 Forbidden")
    void deactivateUser_asCustomer_shouldReturn403Forbidden() {
        User customer = createAndSaveUser("customer_deact@example.com", null, "Customer", Role.CUSTOMER);
        User target = createAndSaveUser("target_user@example.com", null, "Target", Role.CUSTOMER);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(customer.getId()));

        ResponseEntity<String> response = restTemplate.exchange(
                USERS_URL + "/" + target.getId() + "/deactivate",
                HttpMethod.PATCH,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 4. PATCH /api/users/{userId}/activate (Admin reactivation)
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/users/{userId}/activate - admin reactivates deactivated target returning 204 No Content")
    void activateUser_asAdmin_onDeactivatedTarget_shouldReturn204AndReactivateAccount() {
        User admin = createAndSaveUser("admin_act@example.com", null, "Admin Activator", Role.ADMIN);
        User target = createAndSaveUser("target_deactivated@example.com", null, "Deactivated Target", Role.CUSTOMER);
        target.deactivate(DeactivatedBy.ADMIN);
        target.setFailedLoginAttempts(5);
        userRepository.save(target);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(admin.getId()));

        ResponseEntity<Void> response = restTemplate.exchange(
                USERS_URL + "/" + target.getId() + "/activate",
                HttpMethod.PATCH,
                entity,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify in database: enabled=true, deactivatedBy=null, failedLoginAttempts=0
        User updatedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertThat(updatedTarget.isEnabled()).isTrue();
        assertThat(updatedTarget.getDeactivatedBy()).isNull();
        assertThat(updatedTarget.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(updatedTarget.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/activate - activating already active user returns 409 Conflict")
    void activateUser_asAdmin_onAlreadyActiveTarget_shouldReturn409Conflict() {
        User admin = createAndSaveUser("admin_already_act@example.com", null, "Admin Check", Role.ADMIN);
        User target = createAndSaveUser("target_active_already@example.com", null, "Active Target", Role.CUSTOMER);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(admin.getId()));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                USERS_URL + "/" + target.getId() + "/activate",
                HttpMethod.PATCH,
                entity,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.USER_ALREADY_ACTIVE);
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/activate - activating unknown user returns 404 Not Found")
    void activateUser_asAdmin_onUnknownUser_shouldReturn404NotFound() {
        User admin = createAndSaveUser("admin_act_unknown@example.com", null, "Admin Act", Role.ADMIN);
        UUID nonExistentId = UUID.randomUUID();

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(admin.getId()));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                USERS_URL + "/" + nonExistentId + "/activate",
                HttpMethod.PATCH,
                entity,
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/activate - customer caller is denied with 403 Forbidden")
    void activateUser_asCustomer_shouldReturn403Forbidden() {
        User customer = createAndSaveUser("customer_act@example.com", null, "Customer", Role.CUSTOMER);
        User target = createAndSaveUser("target_to_act@example.com", null, "Target", Role.CUSTOMER);
        target.deactivate(DeactivatedBy.SELF);
        userRepository.save(target);

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(customer.getId()));

        ResponseEntity<String> response = restTemplate.exchange(
                USERS_URL + "/" + target.getId() + "/activate",
                HttpMethod.PATCH,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
