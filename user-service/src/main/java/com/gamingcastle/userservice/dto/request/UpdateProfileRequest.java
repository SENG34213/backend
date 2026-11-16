package com.gamingcastle.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
        @NotBlank(message = "fullName is required") String fullName,
        @Pattern(regexp = "^$|^(?:\\+94|0)[0-9]{9}$", message = "Phone must match +94XXXXXXXXX or 0XXXXXXXXX")
        String phoneNumber,
        String avatarUrl
) {
    public UpdateProfileRequest(String fullName, String phoneNumber) {
        this(fullName, phoneNumber, null);
    }
}
