package com.gamingcastle.bookingservice.dto;

import java.time.Instant;

public record SlotAvailabilityResponse(
        Instant startTime,
        Instant endTime,
        boolean available
) {}
