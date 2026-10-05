package com.gamingcastle.notificationservice.dto;

public record BookingEmailRequest(
    String email,
    String customerName,
    String bookingId,
    String gameStationName,
    String bookingDate,
    String timeSlot,
    String amount
) {}
