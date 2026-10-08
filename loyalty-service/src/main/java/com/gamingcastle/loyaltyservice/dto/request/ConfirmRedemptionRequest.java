package com.gamingcastle.loyaltyservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmRedemptionRequest(
        @NotNull(message = "bookingId is required") UUID bookingId
) {
}
