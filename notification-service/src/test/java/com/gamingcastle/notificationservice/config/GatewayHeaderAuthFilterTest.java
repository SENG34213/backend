package com.gamingcastle.notificationservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayHeaderAuthFilterTest {

    private static final String GATEWAY_SECRET = "test-gateway-secret";

    private GatewayHeaderAuthFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new GatewayHeaderAuthFilter();
        ReflectionTestUtils.setField(filter, "expectedGatewaySecret", GATEWAY_SECRET);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilterInternal: bypasses filter when path is whitelisted (swagger or actuator)")
    void doFilterInternal_shouldBypass_whenWhitelistedPath() throws ServletException, IOException {
        String[] whitelistedPaths = {
                "/swagger-ui/index.html",
                "/v3/api-docs",
                "/swagger-ui.html",
                "/actuator/health"
        };

        for (String path : whitelistedPaths) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, atLeastOnce()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("doFilterInternal: returns 401 Unauthorized when gateway secret is missing or incorrect")
    void doFilterInternal_shouldReject_whenSecretMissingOrInvalid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/notifications/welcome");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Request must come through the API Gateway");
        verify(filterChain, never()).doFilter(any(), any());

        // Incorrect secret
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRequestURI("/api/notifications/welcome");
        request2.addHeader(GatewayHeaderAuthFilter.GATEWAY_SECRET_HEADER, "wrong-secret");
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        filter.doFilterInternal(request2, response2, filterChain);

        assertThat(response2.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("doFilterInternal: returns 401 Unauthorized when userId or role header is missing or blank")
    void doFilterInternal_shouldReject_whenIdentityHeadersMissingOrBlank() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/notifications/welcome");
        request.addHeader(GatewayHeaderAuthFilter.GATEWAY_SECRET_HEADER, GATEWAY_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid gateway identity headers");

        // Blank userId
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRequestURI("/api/notifications/welcome");
        request2.addHeader(GatewayHeaderAuthFilter.GATEWAY_SECRET_HEADER, GATEWAY_SECRET);
        request2.addHeader(GatewayHeaderAuthFilter.USER_ID_HEADER, "   ");
        request2.addHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER, "SERVICE");
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        filter.doFilterInternal(request2, response2, filterChain);
        assertThat(response2.getStatus()).isEqualTo(401);

        // Blank role
        MockHttpServletRequest request3 = new MockHttpServletRequest();
        request3.setRequestURI("/api/notifications/welcome");
        request3.addHeader(GatewayHeaderAuthFilter.GATEWAY_SECRET_HEADER, GATEWAY_SECRET);
        request3.addHeader(GatewayHeaderAuthFilter.USER_ID_HEADER, UUID.randomUUID().toString());
        request3.addHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER, "  ");
        MockHttpServletResponse response3 = new MockHttpServletResponse();

        filter.doFilterInternal(request3, response3, filterChain);
        assertThat(response3.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("doFilterInternal: returns 401 Unauthorized when userId is not a valid UUID")
    void doFilterInternal_shouldReject_whenUserIdNotValidUUID() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/notifications/welcome");
        request.addHeader(GatewayHeaderAuthFilter.GATEWAY_SECRET_HEADER, GATEWAY_SECRET);
        request.addHeader(GatewayHeaderAuthFilter.USER_ID_HEADER, "invalid-uuid-123");
        request.addHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER, "SERVICE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid gateway identity headers");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("doFilterInternal: returns 401 Unauthorized when user role is unknown")
    void doFilterInternal_shouldReject_whenRoleIsUnknown() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/notifications/welcome");
        request.addHeader(GatewayHeaderAuthFilter.GATEWAY_SECRET_HEADER, GATEWAY_SECRET);
        request.addHeader(GatewayHeaderAuthFilter.USER_ID_HEADER, UUID.randomUUID().toString());
        request.addHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER, "SUPERADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid gateway identity headers");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("doFilterInternal: sets security context and proceeds down chain when headers valid")
    void doFilterInternal_shouldAuthenticate_whenHeadersValid() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/notifications/welcome");
        request.addHeader(GatewayHeaderAuthFilter.GATEWAY_SECRET_HEADER, GATEWAY_SECRET);
        request.addHeader(GatewayHeaderAuthFilter.USER_ID_HEADER, userId.toString());
        request.addHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER, "SERVICE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(inv -> {
            // Context should be populated inside filterChain
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Security context should be cleared after request finishes
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
