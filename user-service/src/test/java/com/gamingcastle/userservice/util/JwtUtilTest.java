package com.gamingcastle.userservice.util;

import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-32-bytes-long!";
    private static final long EXPIRATION_MS = 900000L; // 15 minutes

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", EXPIRATION_MS);
    }

    @Test
    @DisplayName("generateToken: should generate a valid HMAC-SHA signed JWT with subject, email, fullName, and role")
    void generateToken_shouldProduceValidSignedToken() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("player@example.com")
                .fullName("Test Player")
                .role(Role.CUSTOMER)
                .build();

        String token = jwtUtil.generateToken(user);

        assertThat(token).isNotBlank();

        // Parse and verify token signatures and claims
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("player@example.com");
        assertThat(claims.get("fullName", String.class)).isEqualTo("Test Player");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");

        assertThat(claims.getIssuedAt()).isBeforeOrEqualTo(new Date());
        assertThat(claims.getExpiration()).isAfter(new Date());
    }
}
