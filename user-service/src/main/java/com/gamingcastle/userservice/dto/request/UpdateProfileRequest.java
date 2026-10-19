package com.gamingcastle.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "fullName is required") String fullName,
        String phoneNumber,
        String avatarUrl
) {
    public UpdateProfileRequest(String fullName, String phoneNumber) {
        this(fullName, phoneNumber, null);
    }
}
