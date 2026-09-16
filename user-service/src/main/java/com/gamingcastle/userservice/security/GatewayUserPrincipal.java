package com.gamingcastle.userservice.security;

import java.util.UUID;

public record GatewayUserPrincipal(UUID userId, String role) {
}
