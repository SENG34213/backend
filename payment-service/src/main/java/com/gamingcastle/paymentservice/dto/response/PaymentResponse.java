package com.gamingcastle.paymentservice.dto.response;

import com.gamingcastle.paymentservice.entity.PaymentMethod;
import com.gamingcastle.paymentservice.entity.PaymentStatus;
import com.gamingcastle.paymentservice.entity.ReferenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID id;

    private UUID userId;

    private ReferenceType referenceType;

    private UUID referenceId;

    private BigDecimal amount;

    private PaymentMethod method;

    private PaymentStatus status;

    private String idempotencyKey;

    private String receiptNumber;

    private Instant createdAt;

    private Instant updatedAt;
}