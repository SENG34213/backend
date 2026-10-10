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
     * Result of a booking fee payment charge.
     */
    public record PaymentResult(
            UUID paymentId,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            BigDecimal payableAmount
    ) {}

    /**
     * Charges the booking fee for a given user and booking.
     *
     * @param amount         the total booking fee before loyalty discount
     * @param userId         the customer's UUID
     * @param bookingId      the booking UUID (used as referenceId and idempotency key seed)
     * @param method         payment method — e.g. "CARD", "ONLINE", "CASH"
     * @param pointsToRedeem optional loyalty points to redeem
     * @return PaymentResult containing paymentId and price breakdown
     * @throws BookingException (HTTP 402) if payment fails or service is unreachable
     */
    public PaymentResult chargeBookingFee(BigDecimal amount, UUID userId, UUID bookingId, String method, Integer pointsToRedeem) {
        String idempotencyKey = userId.toString() + "-" + bookingId.toString();
        String selectedMethod = (method != null && !method.isBlank()) ? method.toUpperCase() : "CARD";

        PaymentRequest request = new PaymentRequest(
                "BOOKING",
                bookingId,
                amount,
                selectedMethod,
                idempotencyKey,
                pointsToRedeem,
                userId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (gatewaySecret != null && !gatewaySecret.isBlank()) {
            headers.set("X-Gateway-Secret", gatewaySecret);
        }

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                    paymentBaseUrl + "/api/internal/payments",
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

            BigDecimal total = body.originalAmount() != null ? body.originalAmount() : amount;
            BigDecimal discount = body.loyaltyDiscount() != null ? body.loyaltyDiscount() : BigDecimal.ZERO;
            BigDecimal payable = body.amount() != null ? body.amount() : amount;

            log.info("Booking payment SUCCESS: userId={} bookingId={} paymentId={} payableAmount={}",
                    userId, bookingId, body.id(), payable);

            return new PaymentResult(body.id(), total, discount, payable);

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
            String idempotencyKey,
            Integer loyaltyPointsToRedeem,
            UUID customerUserId
    ) {}

    /** Mirrors PaymentResponse in payment-service (only fields we need). */
    record PaymentResponse(
            UUID id,
            String status,
            String receiptNumber,
            BigDecimal amount,
            BigDecimal originalAmount,
            BigDecimal loyaltyDiscount
    ) {}
}
