package com.gamingcastle.paymentservice.service;

import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.AdminPaymentStatsResponse;
import com.gamingcastle.paymentservice.dto.response.PaymentResponse;
import com.gamingcastle.paymentservice.entity.PaymentMethod;
import com.gamingcastle.paymentservice.entity.PaymentStatus;
import com.gamingcastle.paymentservice.entity.ReferenceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentService {

    PaymentResponse processPayment(PaymentRequest request, UUID userId);

    PaymentResponse processCashPayment(PaymentRequest request, UUID userId);

    PaymentResponse getPaymentReceipt(UUID paymentId, UUID userId, String role);

    List<PaymentResponse> getMyPayments(UUID userId);

    /** G7 — Admin: paged filtered payment log. All filter params are optional. */
    Page<PaymentResponse> getAdminPayments(
            PaymentStatus status,
            PaymentMethod method,
            ReferenceType referenceType,
            UUID userId,
            Instant from,
            Instant to,
            Pageable pageable
    );

    /** G11 — Admin dashboard stats for the payment service slice. */
    AdminPaymentStatsResponse getPaymentStats(int trendDays);
}