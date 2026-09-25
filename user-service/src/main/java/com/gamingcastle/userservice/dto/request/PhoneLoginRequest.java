package com.gamingcastle.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;

/** FR-03: login with phone number + password instead of email + password. */
public record PhoneLoginRequest(
        @NotBlank String phoneNumber,
        @NotBlank String password
) {}
