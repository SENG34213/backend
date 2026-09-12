package com.gamingcastle.userservice.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        String userId,
        String email,
        String role
) {
    public static AuthResponse of(String token, String userId, String email, String role) {
        return new AuthResponse(token, "Bearer", userId, email, role);
    }
}
