package com.gamingcastle.tournamentservice.dto;

import com.gamingcastle.tournamentservice.entity.MatchStatus;
import java.time.Instant;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        UUID bracketId,
        int round,
        UUID participant1Id,
        UUID participant2Id,  // null = bye
        UUID winnerId,        // null until played
        MatchStatus status,
        Instant scheduledTime
) {}
