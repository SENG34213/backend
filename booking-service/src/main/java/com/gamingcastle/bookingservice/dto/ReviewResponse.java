package com.gamingcastle.bookingservice.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID bookingId,
        UUID userId,
        String userName,
        UUID stationId,
        String stationCode,
        String gameTitle,
        Double rating,
        String comment,
        Instant createdAt,
        Instant updatedAt
) {
    public ReviewResponse(
            UUID id,
            UUID bookingId,
            UUID userId,
            UUID stationId,
            String stationCode,
            String gameTitle,
            Double rating,
            String comment,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, bookingId, userId, null, stationId, stationCode, gameTitle, rating, comment, createdAt, updatedAt);
    }
}
