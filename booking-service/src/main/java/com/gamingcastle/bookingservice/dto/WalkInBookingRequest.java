package com.gamingcastle.bookingservice.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-09: admin-entered walk-in booking on behalf of a customer at the front
 * desk. Unlike BookingRequest, this DOES carry a target user id — the
 * admin is booking on someone else's behalf, so the customer being booked
 * for has to be identified explicitly. Only reachable by ADMIN (enforced
 * in the controller via @PreAuthorize).
 */
public record WalkInBookingRequest(
        @NotNull UUID targetUserId,
        @NotNull UUID stationId,
        @NotNull @Future Instant startTime,
        @NotNull @Future Instant endTime
) {}
