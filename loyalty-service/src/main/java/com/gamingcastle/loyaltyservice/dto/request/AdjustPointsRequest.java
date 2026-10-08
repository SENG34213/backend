package com.gamingcastle.loyaltyservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdjustPointsRequest(
        @NotNull(message = "userId is required") UUID userId,
        @NotBlank(message = "reason is required") String reason,
        long points
) {
}
