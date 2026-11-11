package com.gamingcastle.bookingservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String gatewaySecret;
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    public UserClient() {
        this("http://localhost:8081", "");
    }

    public UserClient(
            @Value("${services.user.base-url:http://localhost:8081}") String userServiceUrl,
            @Value("${gateway.internal-secret:}") String gatewaySecret) {
        this.restTemplate = new RestTemplate();
        String cleaned = userServiceUrl.endsWith("/") ? userServiceUrl.substring(0, userServiceUrl.length() - 1) : userServiceUrl;
        this.baseUrl = cleaned + "/api/users";
        this.gatewaySecret = gatewaySecret;
    }

    public Optional<String> getUserFullName(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        if (nameCache.containsKey(userId)) {
            return Optional.ofNullable(nameCache.get(userId));
        }

        Optional<UserSummaryDto> dtoOpt = getUserById(userId);
        if (dtoOpt.isPresent()) {
            UserSummaryDto dto = dtoOpt.get();
            String name = dto.fullName();
            if (name == null || name.isBlank()) {
                name = dto.username();
            }
            if (name != null && !name.isBlank()) {
                nameCache.put(userId, name);
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    public Optional<UserSummaryDto> getUserById(UUID userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (gatewaySecret != null && !gatewaySecret.isBlank()) {
                headers.set("X-Gateway-Secret", gatewaySecret);
            }
            headers.set("X-User-Id", userId.toString());

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<UserSummaryDto> response = restTemplate.exchange(
                    baseUrl + "/me",
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserSummaryDto(
            UUID id,
            String username,
            String email,
            String fullName,
            String role,
            String phoneNumber,
            String avatarUrl
    ) {
        public UserSummaryDto(UUID id, String username, String email, String fullName, String role) {
            this(id, username, email, fullName, role, null, null);
        }
    }
}
