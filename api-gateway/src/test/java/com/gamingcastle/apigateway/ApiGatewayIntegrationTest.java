package com.gamingcastle.apigateway;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWireMock(port = 0)
class ApiGatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${gateway.internal-secret}")
    private String gatewayInternalSecret;

    @BeforeEach
    void setUp() {
        WireMock.reset();
    }

    private String createToken(String subject, String role, String email, String fullName, long expirationMs) {
        return createTokenWithSecret(subject, role, email, fullName, expirationMs, jwtSecret);
    }

    private String createTokenWithSecret(String subject, String role, String email, String fullName, long expirationMs, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("email", email)
                .claim("fullName", fullName)
                .issuedAt(new Date(System.currentTimeMillis() - 1000))
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    // ── Public Endpoint Routing ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login routes to user-service without requiring auth header")
    void publicEndpoint_login_withoutToken_routesSuccessfully() {
        stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"mock-jwt-token\",\"user\":{\"id\":\"user-123\"}}")));

        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"test@test.com\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.token").isEqualTo("mock-jwt-token");

        verify(postRequestedFor(urlEqualTo("/api/auth/login"))
                .withoutHeader("X-User-Id"));
    }

    @Test
    @DisplayName("POST /api/auth/register routes to user-service without requiring auth header")
    void publicEndpoint_register_withoutToken_routesSuccessfully() {
        stubFor(post(urlEqualTo("/api/auth/register"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"new-user-123\",\"message\":\"Registered\"}")));

        webTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"new@test.com\",\"fullName\":\"New User\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("new-user-123");

        verify(postRequestedFor(urlEqualTo("/api/auth/register")));
    }

    @Test
    @DisplayName("POST /api/auth/password/forgot routes without requiring auth header")
    void publicEndpoint_forgotPassword_withoutToken_routesSuccessfully() {
        stubFor(post(urlEqualTo("/api/auth/password/forgot"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Reset link sent\"}")));

        webTestClient.post()
                .uri("/api/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"test@test.com\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Reset link sent");

        verify(postRequestedFor(urlEqualTo("/api/auth/password/forgot")));
    }

    @Test
    @DisplayName("POST /api/auth/reactivate/request routes without requiring auth header")
    void publicEndpoint_reactivate_withoutToken_routesSuccessfully() {
        stubFor(post(urlEqualTo("/api/auth/reactivate/request"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Reactivation code sent\"}")));

        webTestClient.post()
                .uri("/api/auth/reactivate/request")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"deactivated@test.com\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Reactivation code sent");

        verify(postRequestedFor(urlEqualTo("/api/auth/reactivate/request")));
    }

    @Test
    @DisplayName("GET /api/stations routes to booking-service without requiring auth header")
    void publicEndpoint_stations_withoutToken_routesSuccessfully() {
        stubFor(get(urlEqualTo("/api/stations"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":1,\"name\":\"Station 1\"}]")));

        webTestClient.get()
                .uri("/api/stations")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("Station 1");

        verify(getRequestedFor(urlEqualTo("/api/stations"))
                .withoutHeader("X-User-Id"));
    }

    @Test
    @DisplayName("GET /api/tournaments routes to tournament-service without requiring auth header")
    void publicEndpoint_tournaments_withoutToken_routesSuccessfully() {
        stubFor(get(urlEqualTo("/api/tournaments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":10,\"name\":\"Summer Showdown\"}]")));

        webTestClient.get()
                .uri("/api/tournaments")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("Summer Showdown");

        verify(getRequestedFor(urlEqualTo("/api/tournaments"))
                .withoutHeader("X-User-Id"));
    }

    @Test
    @DisplayName("GET /api/tournaments with valid Bearer token forwards extracted headers downstream")
    void publicEndpoint_withValidToken_routesAndForwardsGatewayHeaders() {
        String userId = UUID.randomUUID().toString();
        String token = createToken(userId, "CUSTOMER", "tourney@test.com", "Tourney Fan", 60000);

        stubFor(get(urlEqualTo("/api/tournaments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        webTestClient.get()
                .uri("/api/tournaments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        verify(getRequestedFor(urlEqualTo("/api/tournaments"))
                .withHeader("X-User-Id", equalTo(userId))
                .withHeader("X-User-Role", equalTo("CUSTOMER"))
                .withHeader("X-User-Email", equalTo("tourney@test.com"))
                .withHeader("X-User-FullName", equalTo("Tourney Fan"))
                .withHeader("X-Gateway-Secret", equalTo(gatewayInternalSecret)));
    }

    @Test
    @DisplayName("GET /api/tournaments with invalid Bearer token proceeds anonymously without error")
    void publicEndpoint_withInvalidToken_proceedsAnonymouslyWithoutHeaders() {
        stubFor(get(urlEqualTo("/api/tournaments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        webTestClient.get()
                .uri("/api/tournaments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-tampered-token")
                .exchange()
                .expectStatus().isOk();

        verify(getRequestedFor(urlEqualTo("/api/tournaments"))
                .withoutHeader("X-User-Id"));
    }

    // ── Protected Endpoint Authentication Failures ──────────────────────────

    @Test
    @DisplayName("GET /api/bookings/mine without token returns 401 Unauthorized")
    void protectedEndpoint_bookings_withoutToken_returns401Unauthorized() {
        webTestClient.get()
                .uri("/api/bookings/mine")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("Missing or malformed Authorization header");

        verify(0, getRequestedFor(urlMatching("/api/bookings.*")));
    }

    @Test
    @DisplayName("GET /api/bookings/mine with non-Bearer header returns 401 Unauthorized")
    void protectedEndpoint_bookings_nonBearerHeader_returns401Unauthorized() {
        webTestClient.get()
                .uri("/api/bookings/mine")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("Missing or malformed Authorization header");

        verify(0, getRequestedFor(urlMatching("/api/bookings.*")));
    }

    @Test
    @DisplayName("POST /api/payments with expired token returns 401 Unauthorized")
    void protectedEndpoint_payments_expiredToken_returns401Unauthorized() {
        String userId = UUID.randomUUID().toString();
        String expiredToken = createToken(userId, "CUSTOMER", "expired@test.com", "Expired User", -5000);

        webTestClient.post()
                .uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .bodyValue("{\"bookingId\":\"123\",\"amount\":500.0}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("Invalid or expired token");

        verify(0, postRequestedFor(urlMatching("/api/payments.*")));
    }

    @Test
    @DisplayName("POST /api/payments with token signed with wrong secret returns 401 Unauthorized")
    void protectedEndpoint_payments_wrongSecretToken_returns401Unauthorized() {
        String userId = UUID.randomUUID().toString();
        String wrongSecret = "wrong-secret-key-that-is-at-least-32-bytes-long!";
        String tamperedToken = createTokenWithSecret(userId, "CUSTOMER", "user@test.com", "User", 60000, wrongSecret);

        webTestClient.post()
                .uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                .bodyValue("{\"bookingId\":\"123\",\"amount\":500.0}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("Invalid or expired token");

        verify(0, postRequestedFor(urlMatching("/api/payments.*")));
    }

    @Test
    @DisplayName("POST /api/payments with malformed token returns 401 Unauthorized")
    void protectedEndpoint_payments_malformedToken_returns401Unauthorized() {
        webTestClient.post()
                .uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt.structure")
                .bodyValue("{\"bookingId\":\"123\",\"amount\":500.0}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("Invalid or expired token");

        verify(0, postRequestedFor(urlMatching("/api/payments.*")));
    }

    // ── Protected Endpoint Routing & Header Mutation ────────────────────────

    @Test
    @DisplayName("GET /api/bookings/mine with valid CUSTOMER JWT routes to booking-service with mutated headers")
    void protectedEndpoint_bookings_validCustomerToken_routesAndForwardsHeaders() {
        String userId = UUID.randomUUID().toString();
        String token = createToken(userId, "CUSTOMER", "cust@test.com", "Alice Smith", 60000);

        stubFor(get(urlEqualTo("/api/bookings/mine"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"b-1\",\"stationId\":1}]")));

        webTestClient.get()
                .uri("/api/bookings/mine")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("b-1");

        verify(getRequestedFor(urlEqualTo("/api/bookings/mine"))
                .withHeader("X-User-Id", equalTo(userId))
                .withHeader("X-User-Role", equalTo("CUSTOMER"))
                .withHeader("X-User-Email", equalTo("cust@test.com"))
                .withHeader("X-User-FullName", equalTo("Alice Smith"))
                .withHeader("X-Gateway-Secret", equalTo(gatewayInternalSecret)));
    }

    @Test
    @DisplayName("POST /api/bookings/admin/slots with valid ADMIN JWT routes with X-User-Role: ADMIN")
    void protectedEndpoint_adminBookings_validAdminToken_routesAndForwardsHeaders() {
        String adminId = UUID.randomUUID().toString();
        String token = createToken(adminId, "ADMIN", "admin@gamingcastle.com", "System Admin", 60000);

        stubFor(post(urlEqualTo("/api/bookings/admin/slots"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"Slots created\"}")));

        webTestClient.post()
                .uri("/api/bookings/admin/slots")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"slotCount\":5}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("Slots created");

        verify(postRequestedFor(urlEqualTo("/api/bookings/admin/slots"))
                .withHeader("X-User-Id", equalTo(adminId))
                .withHeader("X-User-Role", equalTo("ADMIN"))
                .withHeader("X-Gateway-Secret", equalTo(gatewayInternalSecret)));
    }

    @Test
    @DisplayName("GET /api/users/profile with valid token routes to user-service")
    void protectedEndpoint_users_validCustomerToken_routesToUserService() {
        String userId = UUID.randomUUID().toString();
        String token = createToken(userId, "CUSTOMER", "user@test.com", "Regular User", 60000);

        stubFor(get(urlEqualTo("/api/users/profile"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + userId + "\",\"email\":\"user@test.com\"}")));

        webTestClient.get()
                .uri("/api/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("user@test.com");

        verify(getRequestedFor(urlEqualTo("/api/users/profile"))
                .withHeader("X-User-Id", equalTo(userId))
                .withHeader("X-User-Role", equalTo("CUSTOMER"))
                .withHeader("X-Gateway-Secret", equalTo(gatewayInternalSecret)));
    }

    @Test
    @DisplayName("GET /api/loyalty/points with valid token routes to loyalty-service")
    void protectedEndpoint_loyalty_validCustomerToken_routesToLoyaltyService() {
        String userId = UUID.randomUUID().toString();
        String token = createToken(userId, "CUSTOMER", "loyalty@test.com", "Points Collector", 60000);

        stubFor(get(urlEqualTo("/api/loyalty/points"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"userId\":\"" + userId + "\",\"points\":150}")));

        webTestClient.get()
                .uri("/api/loyalty/points")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.points").isEqualTo(150);

        verify(getRequestedFor(urlEqualTo("/api/loyalty/points"))
                .withHeader("X-User-Id", equalTo(userId))
                .withHeader("X-User-Role", equalTo("CUSTOMER"))
                .withHeader("X-Gateway-Secret", equalTo(gatewayInternalSecret)));
    }

    // ── Actuator Health Check ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /actuator/health returns 200 OK without requiring authentication")
    void actuatorHealth_withoutToken_returns200Ok() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
