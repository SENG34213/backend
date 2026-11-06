package com.gamingcastle.bookingservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotNull(message = "Rating is required")
        @DecimalMin(value = "1.0", message = "Rating must be between 1.0 and 5.0")
        @DecimalMax(value = "5.0", message = "Rating must be between 1.0 and 5.0")
        Double rating,

        @Size(max = 1000, message = "Comment must not exceed 1000 characters")
        String comment
) {}
