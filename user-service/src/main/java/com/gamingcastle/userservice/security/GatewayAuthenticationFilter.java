package com.gamingcastle.userservice.security;

import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Trusts the Gateway for WHO the caller is (X-User-Id), but never trusts the
 * client-supplied X-User-Role header for WHAT the caller is allowed to do.
 * The role used for @PreAuthorize checks always comes from a fresh DB lookup
 * of the user identified by X-User-Id — so a request can't grant itself
 * ADMIN just by setting a header, even if it somehow reaches this service
 * without going through the Gateway's signed-JWT flow.
 */
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";

    @Value("${gateway.internal-secret}")
    private String expectedGatewaySecret;

    private final UserRepository userRepository;

    public GatewayAuthenticationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (isPublicPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String gatewaySecret = request.getHeader(GATEWAY_SECRET_HEADER);
        String userIdHeader = request.getHeader(USER_ID_HEADER);

        if (!expectedGatewaySecret.equals(gatewaySecret)) {
            unauthorized(response, "Request must come through the API Gateway");
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException | NullPointerException ex) {
            unauthorized(response, "Missing or invalid user id");
            return;
        }

        // Authoritative role check: always re-read the user's current role
        // and status from the database. Never trust USER_ROLE_HEADER for
        // this — it's just what the caller (or the Gateway) claims, and
        // this service must not rely on that claim to grant authorities.
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            unauthorized(response, "Unknown user");
            return;
        }

        User user = userOpt.get();
        if (!user.isEnabled()) {
            unauthorized(response, "Account is deactivated");
            return;
        }

        try {
            String authority = "ROLE_" + user.getRole().name();

            GatewayUserPrincipal principal = new GatewayUserPrincipal(userId, user.getRole().name());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority(authority))
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}"
        );
    }
}