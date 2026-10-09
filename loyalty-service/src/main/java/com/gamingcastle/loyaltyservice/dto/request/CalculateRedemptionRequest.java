package com.gamingcastle.loyaltyservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record CalculateRedemptionRequest(
        @NotNull(message = "userId is required") UUID userId,
        @NotNull(message = "bookingTotal is required") BigDecimal bookingTotal,
        @PositiveOrZero(message = "pointsToRedeem must not be negative") long pointsToRedeem
) {
}
