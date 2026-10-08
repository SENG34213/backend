package com.gamingcastle.tournamentservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BracketResponse(
        UUID id,
        UUID tournamentId,
        Instant generatedAt,
        List<MatchResponse> matches
) {}
