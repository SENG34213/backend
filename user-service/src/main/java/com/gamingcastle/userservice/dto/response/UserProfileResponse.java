package com.gamingcastle.userservice.dto.response;

import java.util.UUID;

/** US-2: shape returned by GET /api/users/me. Never includes passwordHash. */
public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        String role
) {}
