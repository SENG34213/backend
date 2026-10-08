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
        Instant registeredAt
) {}
