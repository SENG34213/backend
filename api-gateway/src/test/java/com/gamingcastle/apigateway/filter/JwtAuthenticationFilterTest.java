package com.gamingcastle.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-jwt-secret-key-that-is-at-least-32-bytes-long!";
    private static final String GATEWAY_INTERNAL_SECRET = "test-gateway-internal-secret";

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(filter, "gatewayInternalSecret", GATEWAY_INTERNAL_SECRET);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private String createToken(String subject, String role, String email, String fullName, long expirationMs) {
        return createTokenWithSecret(subject, role, email, fullName, expirationMs, SECRET);
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

    // ── getOrder ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrder should return -1 to execute before routing filters")
    void getOrder_shouldReturnNegativeOne() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }

    // ── Public paths tests ──────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/login/phone",
            "/api/auth/password/forgot",
            "/api/auth/password/reset",
            "/api/auth/reactivate/request",
            "/api/auth/reactivate/confirm",
            "/actuator/health",
            "/api/stations",
            "/api/tournaments"
    })
    @DisplayName("Public paths without Authorization header should proceed anonymously")
    void publicPaths_shouldProceedAnonymously_withoutAuthHeader(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Public path with valid Bearer token should extract claims and forward headers")
    void publicPath_withValidToken_shouldForwardHeaders() {
        String userId = UUID.randomUUID().toString();
        String token = createToken(userId, "CUSTOMER", "cust@test.com", "Test Customer", 60000);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/tournaments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(captor.capture());
        ServerWebExchange mutatedExchange = captor.getValue();
        HttpHeaders headers = mutatedExchange.getRequest().getHeaders();

        assertThat(headers.getFirst("X-User-Id")).isEqualTo(userId);
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("CUSTOMER");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("cust@test.com");
        assertThat(headers.getFirst("X-User-FullName")).isEqualTo("Test Customer");
        assertThat(headers.getFirst("X-Gateway-Secret")).isEqualTo(GATEWAY_INTERNAL_SECRET);
    }

    @Test
    @DisplayName("Public path with valid Bearer token having null email and fullName should forward empty strings")
    void publicPath_withValidToken_nullEmailAndFullName_shouldForwardEmptyStrings() {
        String userId = UUID.randomUUID().toString();
        String token = createToken(userId, "CUSTOMER", null, null, 60000);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/tournaments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(captor.capture());
        HttpHeaders headers = captor.getValue().getRequest().getHeaders();

        assertThat(headers.getFirst("X-User-Id")).isEqualTo(userId);
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("CUSTOMER");
        assertThat(headers.getFirst("X-User-Email")).isEmpty();
        assertThat(headers.getFirst("X-User-FullName")).isEmpty();
    }

    @Test
    @DisplayName("Public path with non-Bearer Authorization header should proceed anonymously without error")
    void publicPath_withNonBearerAuthHeader_shouldProceedAnonymously() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/tournaments")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Public path with invalid/expired token should continue anonymously without throwing or 401")
    void publicPath_withInvalidToken_shouldProceedAnonymously() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/tournaments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ── Protected paths tests ───────────────────────────────────────────────

    @Test
    @DisplayName("Protected path without Authorization header should return 401 Unauthorized")
    void protectedPath_missingAuthHeader_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/bookings/mine").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        StepVerifier.create(exchange.getResponse().getBody())
                .consumeNextWith(buffer -> {
                    String body = buffer.toString(StandardCharsets.UTF_8);
                    assertThat(body).contains("Missing or malformed Authorization header");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Protected path with non-Bearer Authorization header should return 401 Unauthorized")
    void protectedPath_nonBearerAuthHeader_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/bookings/mine")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        StepVerifier.create(exchange.getResponse().getBody())
                .consumeNextWith(buffer -> {
                    String body = buffer.toString(StandardCharsets.UTF_8);
                    assertThat(body).contains("Missing or malformed Authorization header");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Protected path with expired JWT token should return 401 Invalid or expired token")
    void protectedPath_expiredToken_shouldReturn401() {
        String userId = UUID.randomUUID().toString();
        // Expired 5 seconds ago
        String expiredToken = createToken(userId, "CUSTOMER", "user@test.com", "User", -5000);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/bookings/mine")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        StepVerifier.create(exchange.getResponse().getBody())
                .consumeNextWith(buffer -> {
                    String body = buffer.toString(StandardCharsets.UTF_8);
                    assertThat(body).contains("Invalid or expired token");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Protected path with JWT signed with wrong secret key should return 401 Invalid or expired token")
    void protectedPath_wrongSecretToken_shouldReturn401() {
        String userId = UUID.randomUUID().toString();
        String wrongSecret = "wrong-secret-key-that-is-at-least-32-bytes-long!";
        String tamperedToken = createTokenWithSecret(userId, "CUSTOMER", "user@test.com", "User", 60000, wrongSecret);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/bookings/mine")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        StepVerifier.create(exchange.getResponse().getBody())
                .consumeNextWith(buffer -> {
                    String body = buffer.toString(StandardCharsets.UTF_8);
                    assertThat(body).contains("Invalid or expired token");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Protected path with malformed JWT string should return 401 Invalid or expired token")
    void protectedPath_malformedToken_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/bookings/mine")
                .header(HttpHeaders.AUTHORIZATION, "Bearer totally-not-a-valid-jwt")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        StepVerifier.create(exchange.getResponse().getBody())
                .consumeNextWith(buffer -> {
                    String body = buffer.toString(StandardCharsets.UTF_8);
                    assertThat(body).contains("Invalid or expired token");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Protected path with valid JWT should extract claims and forward headers")
    void protectedPath_validToken_shouldForwardHeaders() {
        String userId = UUID.randomUUID().toString();
        String token = createToken(userId, "ADMIN", "admin@gamingcastle.com", "Admin User", 60000);

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/bookings/admin/slots")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(captor.capture());
        ServerWebExchange mutatedExchange = captor.getValue();
        HttpHeaders headers = mutatedExchange.getRequest().getHeaders();

        assertThat(headers.getFirst("X-User-Id")).isEqualTo(userId);
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("ADMIN");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("admin@gamingcastle.com");
        assertThat(headers.getFirst("X-User-FullName")).isEqualTo("Admin User");
        assertThat(headers.getFirst("X-Gateway-Secret")).isEqualTo(GATEWAY_INTERNAL_SECRET);
    }

    @Test
    @DisplayName("Protected path with valid JWT having null email and fullName should forward empty strings")
    void protectedPath_validToken_nullEmailAndFullName_shouldForwardEmptyStrings() {
        String userId = UUID.randomUUID().toString();
        String token = createToken(userId, "CUSTOMER", null, null, 60000);

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(captor.capture());
        HttpHeaders headers = captor.getValue().getRequest().getHeaders();

        assertThat(headers.getFirst("X-User-Id")).isEqualTo(userId);
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("CUSTOMER");
        assertThat(headers.getFirst("X-User-Email")).isEmpty();
        assertThat(headers.getFirst("X-User-FullName")).isEmpty();
        assertThat(headers.getFirst("X-Gateway-Secret")).isEqualTo(GATEWAY_INTERNAL_SECRET);
    }
}
