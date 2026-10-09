package com.gamingcastle.loyaltyservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record LoyaltyAccountResponse(
        UUID userId,
        long pointsBalance,
        long lifetimeEarned,
        String tier,
        BigDecimal pointValue,
        BigDecimal equivalentDiscount
) {
}
