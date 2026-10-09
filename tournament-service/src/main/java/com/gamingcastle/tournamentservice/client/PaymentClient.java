package com.gamingcastle.tournamentservice.client;

import com.gamingcastle.tournamentservice.exception.PaymentFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * HTTP client for the Payment Service (port 8083).
 *
 * UC-08 / FR-16: Charges the tournament entry fee via the payment-service REST API.
 * Uses an idempotency key (userId + registrationId) to prevent double charges on
 * network retries.
 */
@Service
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestTemplate restTemplate;

    @Value("${services.payment.base-url:http://localhost:8083}")
    private String paymentBaseUrl;

    @Value("${gateway.internal-secret:}")
    private String gatewaySecret;

    public PaymentClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Charges the tournament entry fee for the given user and registration.
     *
     * @param amount         the entry fee amount
     * @param userId         the customer's UUID (used as the payer identity)
     * @param registrationId the tournament registration UUID (used as referenceId)
     * @return the UUID of the successfully created payment record
     * @throws PaymentFailedException if the payment-service returns FAILED or is unreachable
     */
    public UUID chargeEntryFee(BigDecimal amount, UUID userId, UUID registrationId) {
        String idempotencyKey = userId.toString() + "-" + registrationId.toString();

        PaymentRequest request = new PaymentRequest(
                "TOURNAMENT_ENTRY",
                registrationId,
                amount,
                "ONLINE",
                idempotencyKey
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId.toString());
        headers.set("X-User-Role", "CUSTOMER");
        if (gatewaySecret != null && !gatewaySecret.isBlank()) {
            headers.set("X-Gateway-Secret", gatewaySecret);
        }

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                    paymentBaseUrl + "/api/payments",
                    entity,
                    PaymentResponse.class
            );

            PaymentResponse body = response.getBody();
            if (body == null) {
                throw new PaymentFailedException("Payment service returned an empty response for registrationId: " + registrationId);
            }

            if (!"SUCCESS".equalsIgnoreCase(body.status())) {
                throw new PaymentFailedException(
                        "Entry fee payment failed (status=" + body.status() + ") for registrationId: " + registrationId);
            }

            log.info("Entry fee payment SUCCESS for userId={} registrationId={} paymentId={}", userId, registrationId, body.id());
            return body.id();

        } catch (HttpClientErrorException e) {
            log.error("Payment service rejected entry fee request for registrationId={}: HTTP {} {}",
                    registrationId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentFailedException(
                    "Payment service error (" + e.getStatusCode() + ") for registrationId: " + registrationId);
        } catch (PaymentFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error calling payment service for registrationId={}: {}", registrationId, e.getMessage());
            throw new PaymentFailedException(
                    "Could not reach payment service for registrationId: " + registrationId + " — " + e.getMessage());
        }
    }

    // ── Internal DTOs ─────────────────────────────────────────────────────────

    /** Mirrors PaymentRequest in payment-service. */
    record PaymentRequest(
            String referenceType,
            UUID referenceId,
            BigDecimal amount,
            String method,
            String idempotencyKey
    ) {}

    /** Mirrors PaymentResponse in payment-service (only fields we need). */
    record PaymentResponse(
            UUID id,
            String status,
            String receiptNumber
    ) {}
}
