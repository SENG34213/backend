package com.gamingcastle.bookingservice.dto;

import com.gamingcastle.bookingservice.entity.BookingSource;
import com.gamingcastle.bookingservice.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * G10 — Enriched booking row for the admin booking-management table.
 *
 * Extends the public BookingResponse with stationType and hourlyRate so the
 * admin table can show console type and price without an extra API call.
 * Customer name is intentionally NOT included here (cross-service join would
 * violate microservices boundaries); the frontend fetches user details from
 * user-service using userId when needed, or the admin search endpoint returns it.
 */
public record AdminBookingRow(
        UUID id,
        UUID userId,
        UUID stationId,
        String stationCode,
        String stationType,
        BigDecimal hourlyRate,
        Instant startTime,
        Instant endTime,
        long durationMinutes,
        BookingStatus status,
        BookingSource source,
        UUID paymentId,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal payableAmount,
        Instant createdAt
) {}
