package com.gamingcastle.loyaltyservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record RedeemPointsRequest(
        @NotNull(message = "userId is required") UUID userId,
        @NotNull(message = "bookingId is required") UUID bookingId,
        @NotNull(message = "bookingTotal is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "bookingTotal must be greater than zero")
        BigDecimal bookingTotal,
        @Positive(message = "points must be greater than zero") long points
) {
}
