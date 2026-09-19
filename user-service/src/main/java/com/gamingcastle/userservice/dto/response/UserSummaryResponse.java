package com.gamingcastle.userservice.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        String role,
        boolean enabled,
        Instant createdAt
) {}
