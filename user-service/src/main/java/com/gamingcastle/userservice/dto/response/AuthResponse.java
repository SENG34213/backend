package com.gamingcastle.userservice.dto.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        String userId,
        String email,
        String role,
        Boolean notificationSent
) {
    public static AuthResponse of(String token, String userId, String email, String role, boolean notificationSent) {
        return new AuthResponse(token, "Bearer", userId, email, role, notificationSent);
    }

    public static AuthResponse of(String token,String userId,String email,String role) {
        return new AuthResponse(token, "Bearer", userId, email, role,null);
    }
}
