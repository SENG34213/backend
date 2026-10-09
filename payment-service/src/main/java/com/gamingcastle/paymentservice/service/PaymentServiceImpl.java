package com.gamingcastle.paymentservice.service;

import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.PaymentResponse;
import com.gamingcastle.paymentservice.client.LoyaltyClient;
import com.gamingcastle.paymentservice.entity.Payment;
import com.gamingcastle.paymentservice.entity.PaymentStatus;
import com.gamingcastle.paymentservice.entity.ReferenceType;
import com.gamingcastle.paymentservice.gateway.PaymentGateway;
import com.gamingcastle.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final LoyaltyClient loyaltyClient;

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
    public PaymentResponse processCashPayment(
            PaymentRequest request,
            UUID userId) {

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

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
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
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentReceipt(
            UUID paymentId,
            UUID userId,
            String role) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found: " + paymentId
                ));

        if ("ADMIN".equals(role)) {
            return toResponse(payment);
        }

        if ("CUSTOMER".equals(role)
                && !payment.getUserId().equals(userId)) {

            throw new IllegalArgumentException(
                    "You are not authorized to access this payment"
            );
        }

        throw new IllegalArgumentException(
                "You are not authorized to access this payment"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyPayments(UUID userId) {

        return paymentRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }
}