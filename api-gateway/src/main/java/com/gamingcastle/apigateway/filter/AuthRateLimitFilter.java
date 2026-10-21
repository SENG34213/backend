package com.gamingcastle.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway-level token bucket rate limiter protecting sensitive authentication
 * endpoints against brute-force attacks and credential stuffing (NFR-04 / Recommendation 3).
 *
 * Runs before JWT validation (Order = -2). Limits requests per client IP with
 * configurable replenishRate (default: 5 req/sec) and burstCapacity (default: 10 tokens).
 */
@Component
public class AuthRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    @Value("${gateway.ratelimit.auth.replenish-rate:5}")
    private double replenishRate; // tokens added per second

    @Value("${gateway.ratelimit.auth.burst-capacity:10}")
    private double burstCapacity; // max token bucket capacity

    private static final List<String> RATE_LIMITED_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/password",
            "/api/auth/reactivate"
    );

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (!isRateLimitedPath(path)) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(request);
        TokenBucket bucket = buckets.computeIfAbsent(clientIp, k -> new TokenBucket(burstCapacity, System.currentTimeMillis()));

        synchronized (bucket) {
            long now = System.currentTimeMillis();
            long elapsedMs = now - bucket.lastRefillTime;
            bucket.tokens = Math.min(burstCapacity, bucket.tokens + (elapsedMs * replenishRate / 1000.0));
            bucket.lastRefillTime = now;

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return chain.filter(exchange);
            } else {
                log.warn("Rate limit exceeded on '{}' from IP={}", path, clientIp);
                return tooManyRequests(exchange);
            }
        }
    }

    private boolean isRateLimitedPath(String path) {
        return RATE_LIMITED_PATHS.stream().anyMatch(path::startsWith);
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown-client";
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", "2");

        byte[] bytes = ("{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Too many authentication requests. Please wait a few seconds before trying again.\",\"status\":429}")
                .getBytes(StandardCharsets.UTF_8);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -2; // Runs before JwtAuthenticationFilter (order -1)
    }

    private static class TokenBucket {
        double tokens;
        long lastRefillTime;

        TokenBucket(double tokens, long lastRefillTime) {
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
        }
    }
}
