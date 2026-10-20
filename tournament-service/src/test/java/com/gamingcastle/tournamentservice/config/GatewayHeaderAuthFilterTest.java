package com.gamingcastle.tournamentservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GatewayHeaderAuthFilterTest {

    private static final String SECRET = "test-secret";
    private GatewayHeaderAuthFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new GatewayHeaderAuthFilter();
        ReflectionTestUtils.setField(filter, "expectedGatewaySecret", SECRET);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/swagger-ui/index.html",
            "/v3/api-docs/swagger-config",
            "/swagger-ui.html",
            "/actuator/health",
            "/error"
    })
    @DisplayName("Should bypass swagger, openapi, actuator, and error endpoints without secret check")
    void shouldBypassEndpoints(String uri) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should reject requests missing X-Gateway-Secret with 401 UNAUTHORIZED")
    void shouldReject_whenGatewaySecretMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tournaments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Request must come through the API Gateway");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should reject requests with incorrect X-Gateway-Secret with 401 UNAUTHORIZED")
    void shouldReject_whenGatewaySecretInvalid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tournaments");
        request.addHeader("X-Gateway-Secret", "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Request must come through the API Gateway");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should permit guest browsing for GET /api/tournaments without userId header")
    void shouldAllowGuest_forGetTournaments_withoutUserId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tournaments");
        request.addHeader("X-Gateway-Secret", SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Should reject requests with missing X-User-Id header")
    void shouldReject_whenUserIdMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tournaments");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Role", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid gateway identity headers");
    }

    @Test
    @DisplayName("Should reject requests with missing X-User-Role header")
    void shouldReject_whenUserRoleMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tournaments");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid gateway identity headers");
    }

    @Test
    @DisplayName("Should reject requests with malformed UUID in X-User-Id")
    void shouldReject_whenUserIdMalformed() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tournaments");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Id", "not-a-uuid");
        request.addHeader("X-User-Role", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid gateway identity headers");
    }

    @Test
    @DisplayName("Should reject requests with unsupported role (not CUSTOMER or ADMIN)")
    void shouldReject_whenRoleUnsupported() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tournaments");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-User-Role", "GUEST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid gateway identity headers");
    }

    @Test
    @DisplayName("Should authenticate valid CUSTOMER and set ROLE_CUSTOMER authority")
    void shouldAuthenticate_validCustomer() throws ServletException, IOException {
        UUID customerId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tournaments/" + UUID.randomUUID() + "/register");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Id", customerId.toString());
        request.addHeader("X-User-Role", "CUSTOMER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(inv -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isEqualTo(customerId.toString());
            assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Cleaned up after request in finally block
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Should authenticate valid ADMIN and set ROLE_ADMIN authority")
    void shouldAuthenticate_validAdmin() throws ServletException, IOException {
        UUID adminId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tournaments");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Id", adminId.toString());
        request.addHeader("X-User-Role", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(inv -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isEqualTo(adminId.toString());
            assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
