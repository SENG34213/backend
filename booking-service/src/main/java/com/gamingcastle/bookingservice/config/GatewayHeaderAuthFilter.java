package com.gamingcastle.bookingservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Trusts the identity headers the API Gateway forwards after validating the
 * caller's JWT (see api-gateway's JwtAuthenticationFilter). This service
 * never sees or validates a JWT itself — by the time a request reaches
 * here, X-User-Id/X-User-Role are only trustworthy IF this service is only
 * ever reachable through the Gateway. Known simplification for this
 * project's scale, same category as api-gateway's shared JWT secret — not
 * enforced at the network level here, just assumed.
 */
@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader(USER_ID_HEADER);
        String role = request.getHeader(USER_ROLE_HEADER);

        if (userId != null && role != null) {
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
