package com.gamingcastle.loyaltyservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AwardPointsRequest(
        @NotNull(message = "userId is required") UUID userId,
        @NotNull(message = "bookingId is required") UUID bookingId,
        @NotNull(message = "amountPaid is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "amountPaid must be greater than zero")
        BigDecimal amountPaid,
        UUID paymentId
) {
}
