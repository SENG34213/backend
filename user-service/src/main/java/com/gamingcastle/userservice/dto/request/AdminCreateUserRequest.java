package com.gamingcastle.userservice.dto.request;

import com.gamingcastle.userservice.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Pattern(regexp = "^$|^(?:\\+94|0)[0-9]{9}$", message = "Phone must match +94XXXXXXXXX or 0XXXXXXXXX")
        String phoneNumber,

        Role role,

        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,

        String avatarUrl,

        Boolean enabled
) {}
