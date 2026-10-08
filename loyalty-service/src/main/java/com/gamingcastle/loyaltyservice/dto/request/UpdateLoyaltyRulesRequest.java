package com.gamingcastle.loyaltyservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateLoyaltyRulesRequest(
        @NotNull(message = "amountPerPoint is required") @DecimalMin(value = "0.01", message = "amountPerPoint must be positive") BigDecimal amountPerPoint,
        @NotNull(message = "pointValueLkr is required") @DecimalMin(value = "0.01", message = "pointValueLkr must be positive") BigDecimal pointValueLkr,
        @Min(value = 1, message = "minRedeemPoints must be at least 1") long minRedeemPoints,
        @Min(value = 1, message = "redeemStep must be at least 1") long redeemStep,
        @Min(value = 1, message = "maxDiscountPercent must be at least 1") @Max(value = 100, message = "maxDiscountPercent must be at most 100") long maxDiscountPercent,
        @Min(value = 1, message = "reservationTimeoutMinutes must be at least 1") long reservationTimeoutMinutes,
        @Min(value = 0, message = "silverThreshold must be non-negative") long silverThreshold,
        @NotNull(message = "silverMultiplier is required") @DecimalMin(value = "1.00", message = "silverMultiplier must be at least 1.00") BigDecimal silverMultiplier,
        @Min(value = 0, message = "goldThreshold must be non-negative") long goldThreshold,
        @NotNull(message = "goldMultiplier is required") @DecimalMin(value = "1.00", message = "goldMultiplier must be at least 1.00") BigDecimal goldMultiplier
) {
}