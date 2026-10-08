package com.gamingcastle.loyaltyservice.dto.response;

import java.util.UUID;

public record AdminAdjustmentResponse(
        UUID userId,
        long pointsAdjusted,
        long newBalance,
        String reason
) {
}
