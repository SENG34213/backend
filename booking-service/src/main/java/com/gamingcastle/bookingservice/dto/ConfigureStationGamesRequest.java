package com.gamingcastle.bookingservice.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ConfigureStationGamesRequest(
    @NotNull(message = "Game IDs list is required")
    List<UUID> gameIds
) {}
