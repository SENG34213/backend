package com.gamingcastle.bookingservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmBookingRequest(
        @NotNull
        UUID paymentId
) {
}