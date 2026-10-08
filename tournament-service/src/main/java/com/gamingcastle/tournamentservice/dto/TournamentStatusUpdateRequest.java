package com.gamingcastle.tournamentservice.dto;

import com.gamingcastle.tournamentservice.entity.TournamentStatus;
import jakarta.validation.constraints.NotNull;

public record TournamentStatusUpdateRequest(
        @NotNull TournamentStatus status
) {}
