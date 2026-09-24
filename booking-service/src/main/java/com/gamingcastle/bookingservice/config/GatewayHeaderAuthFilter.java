package com.gamingcastle.bookingservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String userRole = request.getHeader("X-User-Role");

        if (userId == null || userRole == null || userId.isBlank() || userRole.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing identity headers");
            return;
        }

        filterChain.doFilter(request, response);
    }
}