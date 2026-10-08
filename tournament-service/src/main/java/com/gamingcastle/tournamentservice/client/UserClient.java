package com.gamingcastle.tournamentservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);
    private final RestTemplate restTemplate;
    private final String baseUrl = "http://localhost:8081/api/users";

    @Value("${gateway.internal-secret:}")
    private String gatewaySecret;

    public UserClient() {
        this.restTemplate = new RestTemplate();
    }

    public Optional<UserSummaryDto> getUserById(UUID userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (gatewaySecret != null && !gatewaySecret.isBlank()) {
                headers.set("X-Gateway-Secret", gatewaySecret);
            }
            // Provide caller identity header so GatewayAuthenticationFilter allows lookup
            headers.set("X-User-Id", userId.toString());

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<UserSummaryDto> response = restTemplate.exchange(
                    baseUrl + "/" + userId,
                    HttpMethod.GET,
                    entity,
                    UserSummaryDto.class
            );

            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.warn("Could not fetch user info for userId {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public record UserSummaryDto(
            UUID id,
            String username,
            String email,
            String fullName,
            String role
    ) {}
}
