package com.gamingcastle.bookingservice.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID bookingId,
        UUID userId,
        UUID stationId,
        String stationCode,
        String gameTitle,
        int rating,
        String comment,
        Instant createdAt,
        Instant updatedAt
) {}
