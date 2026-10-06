package com.gamingcastle.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmReactivationRequest(
        @NotBlank String identifier,
        @NotBlank String code
) {}