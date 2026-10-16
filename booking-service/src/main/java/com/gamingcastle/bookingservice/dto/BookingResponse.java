package com.gamingcastle.bookingservice.dto;

import com.gamingcastle.bookingservice.entity.Booking;
import com.gamingcastle.bookingservice.entity.BookingSource;
import com.gamingcastle.bookingservice.entity.BookingStatus;

import java.math.BigDecimal;
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
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal payableAmount,
        Instant createdAt
) {
    public static BookingResponse from(Booking booking) {
        if (booking == null) return null;
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (booking.getStation() != null && booking.getStation().getHourlyRate() != null && booking.getStartTime() != null && booking.getEndTime() != null) {
            long durationMinutes = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toMinutes();
            BigDecimal hours = BigDecimal.valueOf(durationMinutes).divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP);
            totalAmount = booking.getStation().getHourlyRate().multiply(hours).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return from(booking, totalAmount, BigDecimal.ZERO, totalAmount);
    }

    public static BookingResponse from(Booking booking, BigDecimal totalAmount, BigDecimal discountAmount, BigDecimal payableAmount) {
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
                totalAmount,
                discountAmount,
                payableAmount,
                booking.getCreatedAt()
        );
    }
}