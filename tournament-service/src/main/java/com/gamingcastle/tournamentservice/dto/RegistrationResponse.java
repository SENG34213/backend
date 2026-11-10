package com.gamingcastle.tournamentservice.dto;

import com.gamingcastle.tournamentservice.entity.RegistrationStatus;
import java.time.Instant;
import java.util.UUID;

public record RegistrationResponse(
        UUID id,
        UUID tournamentId,
        String tournamentName,
        UUID userId,
        RegistrationStatus status,
        UUID paymentId,
        Instant registeredAt,
        String gamerTag,
        String userName,
        String userEmail
) {
    public RegistrationResponse(
            UUID id,
            UUID tournamentId,
            String tournamentName,
            UUID userId,
            RegistrationStatus status,
            UUID paymentId,
            Instant registeredAt
    ) {
        this(id, tournamentId, tournamentName, userId, status, paymentId, registeredAt, null, null, null);
    }
}
