package com.gamingcastle.loyaltyservice.dto.response;

public record ReverseLoyaltyResponse(
        long earnedPointsReversed,
        long redeemedPointsRestored,
        boolean reservationReleased,
        long newBalance
) {
}
