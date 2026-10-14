package com.gamingcastle.bookingservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayHeaderAuthFilterTest {

    private GatewayHeaderAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new GatewayHeaderAuthFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilter: proceeds without auth when X-User-Id header is missing")
    void doFilter_shouldProceed_whenUserIdMissing() throws ServletException, IOException {
        request.addHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER, "CUSTOMER");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("doFilter: proceeds without auth when X-User-Role header is missing")
    void doFilter_shouldProceed_whenUserRoleMissing() throws ServletException, IOException {
        request.addHeader(GatewayHeaderAuthFilter.USER_ID_HEADER, UUID.randomUUID().toString());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("doFilter: proceeds without auth when headers are blank")
    void doFilter_shouldProceed_whenHeadersBlank() throws ServletException, IOException {
        request.addHeader(GatewayHeaderAuthFilter.USER_ID_HEADER, "   ");
        request.addHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER, "");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("doFilter: proceeds without auth when role is unsupported")
    void doFilter_shouldProceed_whenRoleUnsupported() throws ServletException, IOException {
        request.addHeader(GatewayHeaderAuthFilter.USER_ID_HEADER, UUID.randomUUID().toString());
        request.addHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER, "STAFF");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("doFilter: sets ROLE_CUSTOMER authentication for valid CUSTOMER header")
    void doFilter_shouldAuthenticateCustomer_whenValidHeaders() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        request.addHeader(GatewayHeaderAuthFilter.USER_ID_HEADER, userId.toString());
        request.addHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER, "customer"); // test case insensitivity

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(capturedAuth.get()).isNotNull();
        assertThat(capturedAuth.get().getPrincipal()).isEqualTo(userId);
        assertThat(capturedAuth.get().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CUSTOMER");

        // Verify security context is cleared in finally block
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("doFilter: sets ROLE_ADMIN authentication for valid ADMIN header")
    void doFilter_shouldAuthenticateAdmin_whenValidHeaders() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        request.addHeader(GatewayHeaderAuthFilter.USER_ID_HEADER, userId.toString());
        request.addHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER, "ADMIN");

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(capturedAuth.get()).isNotNull();
        assertThat(capturedAuth.get().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("doFilter: returns 401 Unauthorized when X-User-Id is not a valid UUID")
    void doFilter_shouldReturn401_whenUserIdNotUuid() throws ServletException, IOException {
        request.addHeader(GatewayHeaderAuthFilter.USER_ID_HEADER, "invalid-uuid-string");
        request.addHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER, "CUSTOMER");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getErrorMessage()).isEqualTo("Invalid gateway identity headers");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
