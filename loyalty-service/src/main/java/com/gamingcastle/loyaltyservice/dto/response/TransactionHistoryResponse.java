package com.gamingcastle.loyaltyservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionHistoryResponse(
        UUID id,
        UUID userId,
        UUID bookingId,
        String type,
        String status,
        long points,
        BigDecimal discountAmount,
        String description,
        long balanceAfter,
        Instant createdAt
) {
}
