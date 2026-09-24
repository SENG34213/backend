package com.gamingcastle.bookingservice.dto;

import com.gamingcastle.bookingservice.entity.Booking;
import com.gamingcastle.bookingservice.entity.BookingSource;
import com.gamingcastle.bookingservice.entity.BookingStatus;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID userId,
        UUID stationId,
        String stationCode,
        Instant startTime,
        Instant endTime,
        BookingStatus status,
        BookingSource source,
        UUID paymentId,
        Instant createdAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getStation().getId(),
                booking.getStation().getStationCode(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus(),
                booking.getSource(),
                booking.getPaymentId(),
                booking.getCreatedAt()
        );
    }
}