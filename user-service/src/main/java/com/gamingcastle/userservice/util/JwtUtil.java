package com.gamingcastle.userservice.util;

import com.gamingcastle.userservice.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and (for local checks, e.g. in tests) validates JWTs.
 *
 * IMPORTANT: this secret MUST match the one configured in api-gateway's
 * application.yml (jwt.secret) — the Gateway validates tokens issued here.
 * In a real deployment this should come from a shared config server / vault,
 * not be duplicated in two files — flagged here as a known simplification
 * for this student-project scope (see ADR notes).
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:900000}") // default 15 minutes, matches ADR-07
    private long expirationMs;

    public String generateToken(User user) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }
}
