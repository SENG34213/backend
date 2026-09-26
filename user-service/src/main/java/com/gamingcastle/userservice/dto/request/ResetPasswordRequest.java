package com.gamingcastle.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-04/FR-05: completes a password reset using the code sent to email or phone. */
public record ResetPasswordRequest(
        @NotBlank String identifier,
        @NotBlank String code,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String newPassword
) {}
