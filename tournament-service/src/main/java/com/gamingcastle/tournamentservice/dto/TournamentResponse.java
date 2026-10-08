package com.gamingcastle.tournamentservice.dto;

import com.gamingcastle.tournamentservice.entity.TournamentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TournamentResponse(
        UUID id,
        String name,
        String gameTitle,
        Instant startDate,
        Instant endDate,
        BigDecimal entryFee,
        int maxParticipants,
        int participantCount,
        Instant registrationDeadline,
        TournamentStatus status,
        UUID createdByAdminId,
        Instant createdAt,
        boolean isRegistrationOpen
) {}
