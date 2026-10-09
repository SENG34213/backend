package com.gamingcastle.loyaltyservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthenticationFilterTest {

    private static final String SECRET = "test-secret";
    private static final String CUSTOMER_PATH = "/api/loyalty/balance/" + UUID.randomUUID();
    private static final String INTERNAL_PATH = "/api/internal/loyalty/award";

    private GatewayAuthenticationFilter filter;
    private final AtomicBoolean chainCalled = new AtomicBoolean();
    private final AtomicReference<Authentication> seen = new AtomicReference<>();

    private final FilterChain chain = (request, response) -> {
        chainCalled.set(true);
        seen.set(SecurityContextHolder.getContext().getAuthentication());
    };

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new GatewayAuthenticationFilter(objectMapper, SECRET);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private List<String> authorities() {
        return seen.get().getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void doFilter_shouldReturn401_whenSecretIsMissing() throws Exception {
        MockHttpServletRequest request = request(CUSTOMER_PATH);
        request.addHeader(GatewayAuthenticationFilter.USER_ID_HEADER, UUID.randomUUID().toString());
        request.addHeader(GatewayAuthenticationFilter.USER_ROLE_HEADER, "CUSTOMER");

        MockHttpServletResponse response = run(request);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
        assertThat(chainCalled).isFalse();
    }

    @Test
    void doFilter_shouldReturn401_whenSecretIsWrong() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PATH);
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, "wrong");

        assertThat(run(request).getStatus()).isEqualTo(401);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void doFilter_shouldAllowActuatorWithoutSecret() throws Exception {
        MockHttpServletResponse response = run(request("/actuator/health"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void doFilter_shouldGrantServiceRole_onInternalPathWithSecretOnly() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PATH);
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, SECRET);

        run(request);

        assertThat(chainCalled).isTrue();
        assertThat(authorities()).containsExactly("ROLE_SERVICE");
    }

    @Test
    void doFilter_shouldReturn403_whenInternalPathCarriesAUserIdentity() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PATH);
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, SECRET);
        request.addHeader(GatewayAuthenticationFilter.USER_ID_HEADER, UUID.randomUUID().toString());

        MockHttpServletResponse response = run(request);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void doFilter_shouldReturn401_whenIdentityHeadersAreMissing() throws Exception {
        MockHttpServletRequest request = request(CUSTOMER_PATH);
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, SECRET);

        assertThat(run(request).getStatus()).isEqualTo(401);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void doFilter_shouldReturn401_whenUserIdIsNotAUuid() throws Exception {
        MockHttpServletRequest request = request(CUSTOMER_PATH);
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, SECRET);
        request.addHeader(GatewayAuthenticationFilter.USER_ID_HEADER, "not-a-uuid");
        request.addHeader(GatewayAuthenticationFilter.USER_ROLE_HEADER, "CUSTOMER");

        assertThat(run(request).getStatus()).isEqualTo(401);
    }

    @Test
    void doFilter_shouldAuthenticateACustomer() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = request(CUSTOMER_PATH);
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, SECRET);
        request.addHeader(GatewayAuthenticationFilter.USER_ID_HEADER, userId.toString());
        request.addHeader(GatewayAuthenticationFilter.USER_ROLE_HEADER, "CUSTOMER");

        run(request);

        assertThat(chainCalled).isTrue();
        GatewayUserPrincipal principal = (GatewayUserPrincipal) seen.get().getPrincipal();
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.isAdmin()).isFalse();
        assertThat(authorities()).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void doFilter_shouldAcceptARolePrefixedAdminRole() throws Exception {
        MockHttpServletRequest request = request("/api/admin/loyalty/rules");
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, SECRET);
        request.addHeader(GatewayAuthenticationFilter.USER_ID_HEADER, UUID.randomUUID().toString());
        request.addHeader(GatewayAuthenticationFilter.USER_ROLE_HEADER, "ROLE_ADMIN");

        run(request);

        GatewayUserPrincipal principal = (GatewayUserPrincipal) seen.get().getPrincipal();
        assertThat(principal.isAdmin()).isTrue();
        assertThat(authorities()).containsExactly("ROLE_ADMIN");
    }

    @Test
    void doFilter_shouldReturn403_whenAUserTokenClaimsTheServiceRole() throws Exception {
        MockHttpServletRequest request = request(CUSTOMER_PATH);
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, SECRET);
        request.addHeader(GatewayAuthenticationFilter.USER_ID_HEADER, UUID.randomUUID().toString());
        request.addHeader(GatewayAuthenticationFilter.USER_ROLE_HEADER, "SERVICE");

        assertThat(run(request).getStatus()).isEqualTo(403);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void doFilter_shouldClearTheSecurityContextAfterTheRequest() throws Exception {
        MockHttpServletRequest request = request(INTERNAL_PATH);
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, SECRET);

        run(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}