package com.gamingcastle.bookingservice.client;

import com.gamingcastle.bookingservice.exception.BookingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
 * FR-14: Charges the booking fee via the payment-service REST API.
 * Uses an idempotency key (userId-bookingId) to prevent double charges on retries.
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
     * Charges the booking fee for a given user and booking.
     *
     * @param amount    the booking fee
     * @param userId    the customer's UUID
     * @param bookingId the booking UUID (used as referenceId and idempotency key seed)
     * @param method    payment method — "CARD" or "CASH"
     * @return the UUID of the successfully created payment record
     * @throws BookingException (HTTP 402) if payment fails or service is unreachable
     */
    public UUID chargeBookingFee(BigDecimal amount, UUID userId, UUID bookingId, String method) {
        String idempotencyKey = userId.toString() + "-" + bookingId.toString();

        PaymentRequest request = new PaymentRequest(
                "BOOKING",
                bookingId,
                amount,
                method,
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
                throw new BookingException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED",
                        "Payment service returned an empty response for bookingId: " + bookingId);
            }

            if (!"SUCCESS".equalsIgnoreCase(body.status())) {
                throw new BookingException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED",
                        "Booking payment failed (status=" + body.status() + ") for bookingId: " + bookingId);
            }

            log.info("Booking payment SUCCESS: userId={} bookingId={} paymentId={}", userId, bookingId, body.id());
            return body.id();

        } catch (BookingException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("Payment service rejected booking fee for bookingId={}: HTTP {} {}",
                    bookingId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BookingException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED",
                    "Payment service error (" + e.getStatusCode() + ") for bookingId: " + bookingId);
        } catch (Exception e) {
            log.error("Could not reach payment service for bookingId={}: {}", bookingId, e.getMessage());
            throw new BookingException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED",
                    "Could not reach payment service for bookingId: " + bookingId + " — " + e.getMessage());
        }
    }

    // ── Internal DTOs ──────────────────────────────────────────────────────────

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
