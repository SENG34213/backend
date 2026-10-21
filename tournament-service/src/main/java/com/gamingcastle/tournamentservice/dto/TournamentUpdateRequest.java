package com.gamingcastle.tournamentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record TournamentUpdateRequest(
        @NotBlank(message = "Tournament name is required") String name,
        @NotBlank(message = "Game title is required") String gameTitle,
        @NotNull(message = "Start date is required") Instant startDate,
        Instant endDate,
        @NotNull(message = "Entry fee is required") @DecimalMin(value = "0.0", message = "Entry fee must be positive or zero") BigDecimal entryFee,
        @Min(value = 2, message = "Maximum participants must be at least 2") int maxParticipants,
        @NotNull(message = "Registration deadline is required") Instant registrationDeadline,
        String imageUrl
) {}
