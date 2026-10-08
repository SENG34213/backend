package com.gamingcastle.tournamentservice.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MatchResultRequest(
        @NotNull UUID winnerId
) {}
