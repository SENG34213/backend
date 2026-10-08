package com.gamingcastle.paymentservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamingcastle.paymentservice.exception.LoyaltyRejectedException;
import com.gamingcastle.paymentservice.exception.LoyaltyUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
public class LoyaltyClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String gatewaySecret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoyaltyClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${gateway.internal-secret}") String gatewaySecret,
            @Value("${loyalty.base-url}") String baseUrl,
            @Value("${loyalty.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${loyalty.read-timeout-ms:3000}") long readTimeoutMs) {

        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.gatewaySecret = gatewaySecret;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    public ReserveResponse reserve(UUID userId, UUID bookingId, BigDecimal bookingTotal, long points) {
        ReserveRequest request = new ReserveRequest(userId, bookingId, bookingTotal, points);

        try {
            ResponseEntity<ReserveResponse> response = post(
                    "/api/internal/loyalty/redeem/reserve",
                    request,
                    ReserveResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            throw new LoyaltyUnavailableException("Loyalty points are temporarily unavailable; please pay without points or try again");

        } catch (HttpClientErrorException.UnprocessableEntity ex) {
            throw new LoyaltyRejectedException(extractMessage(ex.getResponseBodyAsString()));
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException ex) {
            throw new LoyaltyUnavailableException("Loyalty points are temporarily unavailable; please pay without points or try again");
        }
    }

    public void confirm(UUID bookingId) {
        sendWithRetry("confirm", null, bookingId, null, "/api/internal/loyalty/redeem/confirm",
                new ConfirmRequest(bookingId));
    }

    public void release(UUID bookingId, String reason) {
        sendWithRetry("release", null, bookingId, null, "/api/internal/loyalty/redeem/release",
                new ReleaseRequest(bookingId, reason));
    }

    public void award(UUID userId, UUID bookingId, BigDecimal amountPaid, UUID paymentId) {
        sendWithRetry("award", userId, bookingId, paymentId, "/api/internal/loyalty/award",
                new AwardRequest(userId, bookingId, amountPaid, paymentId));
    }

    private <T> ResponseEntity<T> post(String path, Object request, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", gatewaySecret);

        HttpEntity<Object> entity = new HttpEntity<>(request, headers);
        String url = baseUrl + path;

        return restTemplate.exchange(url, HttpMethod.POST, entity, responseType);
    }

    private void sendWithRetry(String operation, UUID userId, UUID bookingId, UUID paymentId, String path, Object body) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                post(path, body, Object.class);
                return;
            } catch (Exception ex) {
                if (attempt == 2) {
                    log.error(
                            "Loyalty {} failed after retry. operation={}, bookingId={}, userId={}, paymentId={}, amount={}",
                            operation,
                            operation,
                            bookingId,
                            userId,
                            paymentId,
                            body instanceof AwardRequest awardRequest ? awardRequest.amountPaid() : null,
                            ex
                    );
                } else {
                    try {
                        Thread.sleep(250L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private String extractMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Loyalty request rejected";
        }

        try {
            JsonNode node = objectMapper.readTree(responseBody);
            if (node.has("message")) {
                return node.get("message").asText();
            }
            if (node.has("error")) {
                return node.get("error").asText();
            }
        } catch (Exception ignored) {
            return responseBody;
        }

        return responseBody;
    }

    public record ReserveRequest(
            UUID userId,
            UUID bookingId,
            BigDecimal bookingTotal,
            long points
    ) {
    }

    public record ReserveResponse(
            UUID transactionId,
            long pointsReserved,
            BigDecimal discountAmount,
            BigDecimal payableAmount,
            long newBalance
    ) {
    }

    public record ConfirmRequest(UUID bookingId) {
    }

    public record ReleaseRequest(UUID bookingId, String reason) {
    }

    public record AwardRequest(
            UUID userId,
            UUID bookingId,
            BigDecimal amountPaid,
            UUID paymentId
    ) {
    }
}
