package com.gamingcastle.paymentservice.dto.request;

import com.gamingcastle.paymentservice.entity.PaymentMethod;
import com.gamingcastle.paymentservice.entity.ReferenceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Reference type is required")
    private ReferenceType referenceType;

    @NotNull(message = "Reference ID is required")
    private UUID referenceId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private PaymentMethod method;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @Min(value = 0, message = "Loyalty points to redeem cannot be negative")
    private Integer loyaltyPointsToRedeem;

    private UUID customerUserId;
}