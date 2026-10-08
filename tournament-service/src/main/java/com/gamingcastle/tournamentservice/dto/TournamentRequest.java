package com.gamingcastle.tournamentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record TournamentRequest(
        @NotBlank String name,
        @NotBlank String gameTitle,
        @NotNull @Future Instant startDate,
        Instant endDate,
        @NotNull @DecimalMin("0.0") BigDecimal entryFee,
        @Min(2) int maxParticipants,
        @NotNull @Future Instant registrationDeadline
) {}
