package com.gamingcastle.loyaltyservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamingcastle.loyaltyservice.api.APIEndPoints;
import com.gamingcastle.loyaltyservice.dto.response.ErrorResponse;
import com.gamingcastle.loyaltyservice.exception.ErrorCodes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";

    private static final String SERVICE_ROLE = "SERVICE";

    private final ObjectMapper objectMapper;
    private final String expectedGatewaySecret;

    public GatewayAuthenticationFilter(ObjectMapper objectMapper,
                                       @Value("${gateway.internal-secret}") String expectedGatewaySecret) {
        this.objectMapper = objectMapper;
        this.expectedGatewaySecret = expectedGatewaySecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        try {
            if (isPublicPath(path)) {
                filterChain.doFilter(request, response);
                return;
            }

            if (!secretMatches(request.getHeader(GATEWAY_SECRET_HEADER))) {
                writeError(request, response, HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED,
                        "Request must come through the API Gateway");
                return;
            }

            if (path.startsWith(APIEndPoints.internalPathPrefix)) {
                if (request.getHeader(USER_ID_HEADER) != null) {
                    writeError(request, response, HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN,
                            "Internal endpoints must not include a user identity header");
                    return;
                }
                authenticate(null, SERVICE_ROLE);
                filterChain.doFilter(request, response);
                return;
            }

            String userIdHeader = request.getHeader(USER_ID_HEADER);
            String roleHeader = request.getHeader(USER_ROLE_HEADER);
            if (userIdHeader == null || roleHeader == null) {
                writeError(request, response, HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED,
                        "Missing gateway identity headers");
                return;
            }

            UUID userId;
            try {
                userId = UUID.fromString(userIdHeader);
            } catch (IllegalArgumentException ex) {
                writeError(request, response, HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED,
                        "Invalid user id header");
                return;
            }

            String role = roleHeader.startsWith("ROLE_") ? roleHeader.substring(5) : roleHeader;
            if (SERVICE_ROLE.equalsIgnoreCase(role)) {
                // A user token must never carry the internal service role.
                writeError(request, response, HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN,
                        ErrorCodes.FORBIDDEN_MESSAGE);
                return;
            }

            authenticate(userId, role);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(UUID userId, String role) {
        GatewayUserPrincipal principal = new GatewayUserPrincipal(userId, role);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean secretMatches(String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedGatewaySecret.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator");
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(code, message, status, request.getRequestURI()));
    }
}