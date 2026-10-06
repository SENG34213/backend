package com.gamingcastle.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReactivateAccountRequest(@NotBlank String identifier) {}
