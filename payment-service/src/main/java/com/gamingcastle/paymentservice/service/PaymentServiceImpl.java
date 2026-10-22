package com.gamingcastle.paymentservice.service;

import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.AdminPaymentStatsResponse;
import com.gamingcastle.paymentservice.dto.response.PaymentResponse;
import com.gamingcastle.paymentservice.client.LoyaltyClient;
import com.gamingcastle.paymentservice.entity.Payment;
import com.gamingcastle.paymentservice.entity.PaymentMethod;
import com.gamingcastle.paymentservice.entity.PaymentStatus;
import com.gamingcastle.paymentservice.entity.ReferenceType;
import com.gamingcastle.paymentservice.gateway.PaymentGateway;
import com.gamingcastle.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final ZoneId SLST = ZoneId.of("Asia/Colombo");

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final LoyaltyClient loyaltyClient;

    @Value("${payhere.merchant.id:1211111}")
    private String payHereMerchantId;

    @Value("${payhere.merchant.secret:replace-with-secret}")
    private String payHereMerchantSecret;

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, UUID userId) {

        Optional<Payment> existingPayment =
                paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());

        if (existingPayment.isPresent()) {
            return toResponse(existingPayment.get());
        }

        validateAmount(request.getAmount());

        boolean bookingPayment = request.getReferenceType() == ReferenceType.BOOKING;
        int pointsToRedeem = request.getLoyaltyPointsToRedeem() == null
                ? 0 : request.getLoyaltyPointsToRedeem();
        if (pointsToRedeem < 0) {
            throw new IllegalArgumentException("Loyalty points to redeem cannot be negative");
        }

        LoyaltyClient.ReserveResponse reservation = null;
        if (bookingPayment && pointsToRedeem > 0) {
            reservation = loyaltyClient.reserve(
                    userId, request.getReferenceId(), request.getAmount(), pointsToRedeem);
        }

        BigDecimal payableAmount = reservation == null
                ? request.getAmount() : reservation.payableAmount();
        if (payableAmount == null || payableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            if (reservation != null) {
                loyaltyClient.release(request.getReferenceId(), "Invalid payable amount returned by loyalty service");
            }
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        Payment payment = Payment.builder()
                .userId(userId)
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .amount(payableAmount)
                .originalAmount(request.getAmount())
                .loyaltyPointsUsed(reservation == null ? 0 : Math.toIntExact(reservation.pointsReserved()))
                .loyaltyDiscount(reservation == null ? BigDecimal.ZERO : reservation.discountAmount())
                .method(request.getMethod())
                .status(PaymentStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        boolean successful;
        try {
            successful = paymentGateway.processPayment(payableAmount);
        } catch (RuntimeException exception) {
            if (reservation != null) {
                loyaltyClient.release(request.getReferenceId(), "Gateway failure during payment processing");
            }
            throw exception;
        }

        if (successful) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setReceiptNumber(generateReceiptNumber());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        if (!successful && reservation != null) {
            loyaltyClient.release(request.getReferenceId(), "Payment failed");
        }

        try {
            Payment savedPayment = paymentRepository.save(payment);
            if (successful && bookingPayment) {
                if (reservation != null) {
                    loyaltyClient.confirm(request.getReferenceId());
                }
                loyaltyClient.award(userId, request.getReferenceId(), payableAmount, savedPayment.getId());
            }
            return toResponse(savedPayment);

        } catch (DataIntegrityViolationException exception) {

            return paymentRepository
                    .findByIdempotencyKey(request.getIdempotencyKey())
                    .map(this::toResponse)
                    .orElseThrow(() -> exception);
        }
    }

    @Override
    @Transactional
    public PaymentResponse processCashPayment(PaymentRequest request, UUID userId) {

        Optional<Payment> existingPayment =
                paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());

        if (existingPayment.isPresent()) {
            return toResponse(existingPayment.get());
        }

        validateAmount(request.getAmount());
        boolean bookingPayment = request.getReferenceType() == ReferenceType.BOOKING;
        UUID customerUserId = request.getCustomerUserId();
        if (bookingPayment && customerUserId == null) {
            throw new IllegalArgumentException("customerUserId is required for booking cash payments");
        }
        UUID paymentUserId = customerUserId == null ? userId : customerUserId;

        Payment payment = Payment.builder()
                .userId(paymentUserId)
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .amount(request.getAmount())
                .originalAmount(request.getAmount())
                .recordedBy(userId)
                .method(request.getMethod())
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey(request.getIdempotencyKey())
                .receiptNumber(generateReceiptNumber())
                .build();

        try {
            Payment savedPayment = paymentRepository.save(payment);
            if (bookingPayment) {
                loyaltyClient.award(paymentUserId, request.getReferenceId(), request.getAmount(), savedPayment.getId());
            }
            return toResponse(savedPayment);

        } catch (DataIntegrityViolationException exception) {

            return paymentRepository
                    .findByIdempotencyKey(request.getIdempotencyKey())
                    .map(this::toResponse)
                    .orElseThrow(() -> exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentReceipt(UUID paymentId, UUID userId, String role) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found: " + paymentId
                ));

        if ("ADMIN".equals(role)) {
            return toResponse(payment);
        }

        if ("CUSTOMER".equals(role)) {
            if (!payment.getUserId().equals(userId)) {
                throw new IllegalArgumentException("You are not authorized to access this payment");
            }
            return toResponse(payment);
        }

        throw new IllegalArgumentException("You are not authorized to access this payment");
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyPayments(UUID userId) {
        return paymentRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── G7: Admin paged payment log ───────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAdminPayments(
            PaymentStatus status,
            PaymentMethod method,
            ReferenceType referenceType,
            UUID userId,
            Instant from,
            Instant to,
            Pageable pageable) {

        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (method != null) {
                predicates.add(cb.equal(root.get("method"), method));
            }
            if (referenceType != null) {
                predicates.add(cb.equal(root.get("referenceType"), referenceType));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return paymentRepository
                .findAll(spec, pageable)
                .map(this::toResponse);
    }

    // ── G11: Admin dashboard payment stats ───────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AdminPaymentStatsResponse getPaymentStats(int trendDays) {

        ZonedDateTime nowSlst = ZonedDateTime.now(SLST);

        Instant todayStart = nowSlst.toLocalDate().atStartOfDay(SLST).toInstant();
        Instant todayEnd   = todayStart.plus(1, ChronoUnit.DAYS);
        Instant monthStart = nowSlst.toLocalDate().withDayOfMonth(1).atStartOfDay(SLST).toInstant();

        BigDecimal todayRevenue = paymentRepository.sumRevenueInRange(todayStart, todayEnd);
        BigDecimal monthRevenue = paymentRepository.sumRevenueInRange(monthStart, todayEnd);

        long successTotal = paymentRepository.countByStatusInRange(
                PaymentStatus.SUCCESS, Instant.EPOCH, Instant.now());
        long failedTotal = paymentRepository.countByStatusInRange(
                PaymentStatus.FAILED, Instant.EPOCH, Instant.now());

        Instant trendFrom = nowSlst.toLocalDate()
                .minusDays(trendDays - 1L)
                .atStartOfDay(SLST)
                .toInstant();

        List<Object[]> rows = paymentRepository.dailyRevenueSince(trendFrom);

        List<AdminPaymentStatsResponse.DailyRevenue> trend = rows.stream()
                .map(r -> new AdminPaymentStatsResponse.DailyRevenue(
                        r[0].toString(),
                        new BigDecimal(r[1].toString())))
                .toList();

        return new AdminPaymentStatsResponse(todayRevenue, monthRevenue, successTotal, failedTotal, trend);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
    }

    private String generateReceiptNumber() {
        return "GC-" + UUID.randomUUID();
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .userId(payment.getUserId())
                .referenceType(payment.getReferenceType())
                .referenceId(payment.getReferenceId())
                .amount(payment.getAmount())
                .originalAmount(payment.getOriginalAmount())
                .loyaltyPointsUsed(payment.getLoyaltyPointsUsed())
                .loyaltyDiscount(payment.getLoyaltyDiscount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .idempotencyKey(payment.getIdempotencyKey())
                .receiptNumber(payment.getReceiptNumber())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public PaymentResponse handlePayHereNotification(
            String merchantId,
            String orderId,
            String paymentId,
            String payhereAmount,
            String payhereCurrency,
            String statusCode,
            String md5sig) {

        log.info("Received PayHere IPN notification: orderId={}, status={}, amount={}",
                orderId, statusCode, payhereAmount);

        boolean verified = verifyPayHereSignature(merchantId, orderId, payhereAmount, payhereCurrency, statusCode, md5sig);
        if (!verified) {
            log.error("PayHere notification rejected: Invalid MD5 signature for orderId={}", orderId);
            throw new IllegalArgumentException("Invalid PayHere signature verification");
        }

        Payment payment = null;
        try {
            UUID parsedId = UUID.fromString(orderId);
            payment = paymentRepository.findById(parsedId)
                    .orElse(null);
            if (payment == null) {
                payment = paymentRepository.findByReferenceId(parsedId)
                        .orElse(null);
            }
        } catch (IllegalArgumentException ignored) {
            payment = paymentRepository.findByIdempotencyKey(orderId).orElse(null);
        }

        if (payment == null) {
            log.warn("Payment not found for PayHere orderId={}", orderId);
            throw new IllegalArgumentException("No payment found matching orderId: " + orderId);
        }

        // Status code 2 = Success in PayHere
        if ("2".equals(statusCode)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            if (payment.getReceiptNumber() == null) {
                payment.setReceiptNumber(generateReceiptNumber());
            }
            log.info("PayHere payment marked SUCCESS for paymentId={}", payment.getId());
        } else if ("-1".equals(statusCode) || "-2".equals(statusCode) || "-3".equals(statusCode)) {
            payment.setStatus(PaymentStatus.FAILED);
            log.warn("PayHere payment failed/cancelled (statusCode={}) for paymentId={}", statusCode, payment.getId());
        }

        Payment saved = paymentRepository.save(payment);
        return toResponse(saved);
    }

    private boolean verifyPayHereSignature(String merchantId, String orderId, String amount, String currency, String statusCode, String md5sig) {
        if (md5sig == null || md5sig.isBlank()) {
            return false;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            String secretHash = toHex(md.digest(payHereMerchantSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8))).toUpperCase();
            String raw = (merchantId != null && !merchantId.isBlank() ? merchantId : payHereMerchantId)
                    + orderId
                    + amount
                    + currency
                    + statusCode
                    + secretHash;
            String computed = toHex(md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))).toUpperCase();
            return computed.equalsIgnoreCase(md5sig.trim());
        } catch (Exception e) {
            log.error("MD5 calculation error in IPN verification", e);
            return false;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}