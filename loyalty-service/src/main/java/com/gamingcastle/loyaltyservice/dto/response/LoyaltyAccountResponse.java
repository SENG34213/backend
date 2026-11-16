package com.gamingcastle.loyaltyservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record LoyaltyAccountResponse(
        UUID userId,
        long pointsBalance,
        long lifetimeEarned,
        String tier,
        BigDecimal pointValue,
        BigDecimal equivalentDiscount,
        long silverThreshold,
        long goldThreshold,
        BigDecimal silverMultiplier,
        BigDecimal goldMultiplier
) {
    public LoyaltyAccountResponse(
            UUID userId,
            long pointsBalance,
            long lifetimeEarned,
            String tier,
            BigDecimal pointValue,
            BigDecimal equivalentDiscount
    ) {
        this(userId, pointsBalance, lifetimeEarned, tier, pointValue, equivalentDiscount,
                1000L, 3000L, new BigDecimal("1.25"), new BigDecimal("1.50"));
    }
}
