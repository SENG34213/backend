package com.gamingcastle.loyaltyservice.dto.response;

public record AwardPointsResponse(
        long pointsAwarded,
        long newBalance,
        String tier,
        boolean alreadyProcessed
) {
}
