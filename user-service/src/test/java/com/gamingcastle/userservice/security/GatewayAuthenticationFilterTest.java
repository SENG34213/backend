package com.gamingcastle.userservice.security;

import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayAuthenticationFilterTest {

    private static final String EXPECTED_SECRET = "test-gateway-secret-123";

    @Mock
    private UserRepository userRepository;

    private GatewayAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GatewayAuthenticationFilter(userRepository);
        ReflectionTestUtils.setField(filter, "expectedGatewaySecret", EXPECTED_SECRET);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- Public endpoint bypass ---

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
            "/swagger-ui/index.html",
            "/v3/api-docs/swagger-config"
    })
    @DisplayName("doFilter: should bypass public paths without gateway secret or user headers")
    void doFilter_shouldBypassPublicPaths(String publicPath) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", publicPath);
        request.setRequestURI(publicPath);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(userRepository, never()).findById(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // --- Missing / Invalid Gateway Secret ---

    @Test
    @DisplayName("doFilter: should return 401 Unauthorized when X-Gateway-Secret header is missing")
    void doFilter_shouldReturn401_whenGatewaySecretIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setRequestURI("/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Request must come through the API Gateway");
        assertThat(response.getContentType()).isEqualTo("application/json");
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("doFilter: should return 401 Unauthorized when X-Gateway-Secret header is invalid")
    void doFilter_shouldReturn401_whenGatewaySecretIsInvalid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setRequestURI("/api/users/me");
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Request must come through the API Gateway");
    }

    // --- Missing / Malformed X-User-Id ---

    @Test
    @DisplayName("doFilter: should return 401 Unauthorized when X-User-Id header is missing")
    void doFilter_shouldReturn401_whenUserIdHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setRequestURI("/api/users/me");
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, EXPECTED_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid user id");
    }

    @Test
    @DisplayName("doFilter: should return 401 Unauthorized when X-User-Id header is not a valid UUID")
    void doFilter_shouldReturn401_whenUserIdHeaderIsMalformed() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setRequestURI("/api/users/me");
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, EXPECTED_SECRET);
        request.addHeader(GatewayAuthenticationFilter.USER_ID_HEADER, "invalid-uuid-string");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid user id");
    }

    // --- Unknown / Deactivated User in Database ---

    @Test
    @DisplayName("doFilter: should return 401 Unauthorized when user is not found in database")
    void doFilter_shouldReturn401_whenUserNotFoundInDatabase() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setRequestURI("/api/users/me");
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, EXPECTED_SECRET);
        request.addHeader(GatewayAuthenticationFilter.USER_ID_HEADER, userId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Unknown user");
    }

    @Test
    @DisplayName("doFilter: should return 401 Unauthorized when user account is deactivated (enabled=false)")
    void doFilter_shouldReturn401_whenUserIsDeactivated() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        User disabledUser = User.builder()
                .id(userId)
                .email("player@example.com")
                .role(Role.CUSTOMER)
                .enabled(false)
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setRequestURI("/api/users/me");
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, EXPECTED_SECRET);
        request.addHeader(GatewayAuthenticationFilter.USER_ID_HEADER, userId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(userRepository.findById(userId)).thenReturn(Optional.of(disabledUser));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Account is deactivated");
    }

    // --- Successful Authentication & Security Context Lifecycle ---

    @Test
    @DisplayName("doFilter: should authenticate user, set SecurityContext with DB role, and clear context in finally")
    void doFilter_shouldAuthenticateAndSetSecurityContext_givenActiveUser() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        User activeUser = User.builder()
                .id(userId)
                .email("admin@example.com")
                .role(Role.ADMIN)
                .enabled(true)
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setRequestURI("/api/users");
        request.addHeader(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, EXPECTED_SECRET);
        request.addHeader(GatewayAuthenticationFilter.USER_ID_HEADER, userId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        FilterChain customChain = (ServletRequest req, ServletResponse res) -> {
            chainExecuted.set(true);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isInstanceOf(GatewayUserPrincipal.class);
            GatewayUserPrincipal principal = (GatewayUserPrincipal) auth.getPrincipal();
            assertThat(principal.userId()).isEqualTo(userId);
            assertThat(principal.role()).isEqualTo("ADMIN");
            assertThat(auth.getAuthorities())
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        };

        filter.doFilter(request, response, customChain);

        assertThat(chainExecuted.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);

        // Verify finally block clears the SecurityContext
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
