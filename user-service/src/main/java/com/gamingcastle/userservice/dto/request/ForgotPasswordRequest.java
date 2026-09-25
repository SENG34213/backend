package com.gamingcastle.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * FR-04/FR-05: kicks off a password reset. {@code identifier} is either the
 * account's email (routes the code via email, FR-04) or its phone number
 * (routes the code via SMS, FR-05) — whichever the user has to hand.
 */
public record ForgotPasswordRequest(
        @NotBlank String identifier
) {}
