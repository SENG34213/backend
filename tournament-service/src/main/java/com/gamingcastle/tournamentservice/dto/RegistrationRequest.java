package com.gamingcastle.tournamentservice.dto;

import java.util.UUID;

public record RegistrationRequest(
        UUID userId,
        String gamerTag,
        Integer loyaltyPointsToRedeem
) {
    public RegistrationRequest(UUID userId) {
        this(userId, null, null);
    }

    public RegistrationRequest(UUID userId, Integer loyaltyPointsToRedeem) {
        this(userId, null, loyaltyPointsToRedeem);
    }
}
