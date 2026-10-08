package com.gamingcastle.bookingservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
public class LoyaltyClient {

    private final RestTemplate restTemplate;
    private final String gatewaySecret;
    private final String baseUrl;

    public LoyaltyClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${gateway.internal-secret}") String gatewaySecret,
            @Value("${loyalty.base-url}") String baseUrl,
            @Value("${loyalty.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${loyalty.read-timeout-ms:3000}") long readTimeoutMs) {

        this.gatewaySecret = gatewaySecret;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    public void reverse(UUID bookingId, String reason) {
        ReverseRequest request = new ReverseRequest(bookingId, reason);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", gatewaySecret);

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                ResponseEntity<Void> response = restTemplate.postForEntity(
                        baseUrl + "/api/internal/loyalty/reverse",
                        new HttpEntity<>(request, headers),
                        Void.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    return;
                }

                if (attempt == 2) {
                    log.error("Loyalty reverse failed after retry for bookingId={}, reason={}, status={}", bookingId, reason, response.getStatusCode());
                }
            } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException ex) {
                if (attempt == 2) {
                    log.error("Loyalty reverse failed after retry for bookingId={}, reason={}", bookingId, reason, ex);
                    return;
                }
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    log.error("Loyalty reverse interrupted for bookingId={}, reason={}", bookingId, reason, interruptedException);
                    return;
                }
            }
        }
    }

    public record ReverseRequest(UUID bookingId, String reason) {}
}
