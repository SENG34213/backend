package com.gamingcastle.userservice.dto.request;

import com.gamingcastle.userservice.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * G9 — Admin update user details and role.
 */
public record AdminUpdateUserRequest(
        @NotBlank(message = "Full name must not be blank")
        String fullName,

        @Pattern(regexp = "^$|^\\+94[0-9]{9}$", message = "Phone must match +94XXXXXXXXX")
        String phoneNumber,

        Role role,

        Boolean enabled,

        String avatarUrl
) {
    public AdminUpdateUserRequest(String fullName, String phoneNumber, Role role, Boolean enabled) {
        this(fullName, phoneNumber, role, enabled, null);
    }
}
