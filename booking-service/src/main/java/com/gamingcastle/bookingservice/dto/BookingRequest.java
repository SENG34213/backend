package com.gamingcastle.bookingservice.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-07/FR-08: request to book a station for a time window. userId is
 * deliberately NOT a field here — it comes from the X-User-Id header the
 * Gateway forwards (trusted caller identity), never from the request body,
 * so a customer can never book a slot "as" someone else.
 */
public record BookingRequest(
        @NotNull UUID stationId,
        @NotNull @Future Instant startTime,
        @NotNull @Future Instant endTime
) {}