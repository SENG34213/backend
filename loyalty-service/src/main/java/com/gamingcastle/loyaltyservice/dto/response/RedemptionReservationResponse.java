package com.gamingcastle.loyaltyservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record RedemptionReservationResponse(
        UUID transactionId,
        long pointsReserved,
        BigDecimal discountAmount,
        BigDecimal payableAmount,
        long newBalance
) {
}
