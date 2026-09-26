package com.gamingcastle.userservice.config;

import com.gamingcastle.userservice.security.GatewayAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * User Service sits behind the Gateway, which already validates JWTs and
 * forwards X-User-Id / X-User-Role headers (see api-gateway's
 * JwtAuthenticationFilter). So this service's own security config only needs
 * to: (1) hash passwords with bcrypt, (2) keep itself stateless, and
 * (3) allow the public auth endpoints through in case it's ever called
 * directly (e.g. during local dev/testing without the Gateway).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    public SecurityConfig(GatewayAuthenticationFilter gatewayAuthenticationFilter) {
        this.gatewayAuthenticationFilter = gatewayAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // cost factor 12, per the OWASP A02 requirement in the course guideline
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless JWT API, no cookies/CSRF risk
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/actuator/**", "/api/auth/password/forgot", "/api/auth/password/reset").permitAll()
                .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
        .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
