package com.gamingcastle.paymentservice.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHeaderAuthFilterTest {

    private GatewayHeaderAuthFilter filter;
    private static final String SECRET = "shared-gateway-secret";

    @BeforeEach
    void setUp() {
        filter = new GatewayHeaderAuthFilter();
        ReflectionTestUtils.setField(filter, "expectedGatewaySecret", SECRET);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilter: rejects request without X-Gateway-Secret header")
    void shouldRejectRequestWithoutGatewaySecret() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments");
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-User-Role", "CUSTOMER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { throw new AssertionError("Chain should not run"); };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Request must come through the API Gateway");
    }

    @Test
    @DisplayName("doFilter: rejects request with invalid X-Gateway-Secret header")
    void shouldRejectRequestWithInvalidGatewaySecret() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments");
        request.addHeader("X-Gateway-Secret", "wrong-secret");
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-User-Role", "CUSTOMER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { throw new AssertionError("Chain should not run"); };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("doFilter: bypasses swagger and OpenAPI documentation endpoints")
    void shouldBypassSwaggerAndOpenApiEndpoints() throws Exception {
        AtomicBoolean chainRan = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainRan.set(true);

        for (String path : List.of("/swagger-ui/index.html", "/v3/api-docs/swagger-config", "/swagger-ui.html")) {
            chainRan.set(false);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(chainRan.get()).isTrue();
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("doFilter: authenticates internal request as ROLE_SERVICE")
    void shouldAuthenticateInternalRequestAsService() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/payments");
        request.addHeader("X-Gateway-Secret", SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean authenticated = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"))) {
                authenticated.set(true);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(authenticated.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(); // cleared in finally
    }

    @Test
    @DisplayName("doFilter: rejects request when X-User-Id is missing or blank")
    void shouldRejectRequestWhenUserIdIsMissingOrBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments/mine");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Role", "CUSTOMER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { throw new AssertionError("Chain should not run"); };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid gateway identity headers");
    }

    @Test
    @DisplayName("doFilter: rejects request when X-User-Role is missing or blank")
    void shouldRejectRequestWhenUserRoleIsMissingOrBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments/mine");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { throw new AssertionError("Chain should not run"); };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid gateway identity headers");
    }

    @Test
    @DisplayName("doFilter: rejects request when X-User-Id is not a valid UUID")
    void shouldRejectRequestWhenUserIdIsNotValidUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments/mine");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Id", "not-a-uuid");
        request.addHeader("X-User-Role", "CUSTOMER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { throw new AssertionError("Chain should not run"); };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid gateway identity headers");
    }

    @Test
    @DisplayName("doFilter: rejects request when X-User-Role is not CUSTOMER or ADMIN")
    void shouldRejectRequestWhenRoleIsNotCustomerOrAdmin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments/mine");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-User-Role", "MODERATOR");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { throw new AssertionError("Chain should not run"); };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid gateway identity headers");
    }

    @Test
    @DisplayName("doFilter: authenticates valid CUSTOMER request")
    void shouldAuthenticateValidCustomerRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments/mine");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-User-Role", "CUSTOMER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean authenticated = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal().equals(userId)
                    && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))) {
                authenticated.set(true);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(authenticated.get()).isTrue();
    }

    @Test
    @DisplayName("doFilter: authenticates valid ADMIN request")
    void shouldAuthenticateValidAdminRequest() throws Exception {
        UUID adminId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments/admin/stats");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Id", adminId.toString());
        request.addHeader("X-User-Role", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean authenticated = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal().equals(adminId)
                    && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                authenticated.set(true);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(authenticated.get()).isTrue();
    }
}
