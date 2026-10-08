package com.gamingcastle.loyaltyservice.security;

import java.util.UUID;

public record GatewayUserPrincipal(UUID userId, String role) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}