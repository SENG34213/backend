package com.gamingcastle.loyaltyservice.dto.response;

public record LoyaltyStatusResponse(
        String status,
        long pointsRedeemed,
        long pointsReturned,
        long newBalance
) {
}
