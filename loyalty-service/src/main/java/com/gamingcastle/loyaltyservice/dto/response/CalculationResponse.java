package com.gamingcastle.loyaltyservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CalculationResponse(
        UUID userId,
        BigDecimal bookingTotal,
        long pointsToRedeem,
        BigDecimal discountAmount,
        BigDecimal payableAmount,
        boolean valid,
        String reason
) {
}
