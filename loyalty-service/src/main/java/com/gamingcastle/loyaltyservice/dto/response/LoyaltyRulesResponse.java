package com.gamingcastle.loyaltyservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record LoyaltyRulesResponse(
        UUID id,
        BigDecimal amountPerPoint,
        BigDecimal pointValueLkr,
        long minRedeemPoints,
        long redeemStep,
        long maxDiscountPercent,
        long reservationTimeoutMinutes,
        long silverThreshold,
        BigDecimal silverMultiplier,
        long goldThreshold,
        BigDecimal goldMultiplier,
        String updatedBy
) {
}
