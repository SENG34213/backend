package com.gamingcastle.tournamentservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class UserClientTest {

    private static final String SECRET = "test-secret";
    private UserClient userClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        userClient = new UserClient();
        RestTemplate restTemplate = new RestTemplate();
        ReflectionTestUtils.setField(userClient, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(userClient, "gatewaySecret", SECRET);

        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    @DisplayName("getUserById should return UserSummaryDto when user-service returns 200 OK")
    void getUserById_shouldReturnUser_whenFound() {
        UUID userId = UUID.randomUUID();
        String json = """
                {
                    "id": "%s",
                    "username": "gamer1",
                    "email": "gamer1@test.com",
                    "fullName": "Gamer One",
                    "role": "CUSTOMER"
                }
                """.formatted(userId);

        mockServer.expect(requestTo("http://localhost:8081/api/users/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Gateway-Secret", SECRET))
                .andExpect(header("X-User-Id", userId.toString()))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<UserClient.UserSummaryDto> result = userClient.getUserById(userId);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(userId);
        assertThat(result.get().email()).isEqualTo("gamer1@test.com");
        assertThat(result.get().fullName()).isEqualTo("Gamer One");
        mockServer.verify();
    }

    @Test
    @DisplayName("getUserById should return Optional.empty() when user-service returns 404 or fails")
    void getUserById_shouldReturnEmpty_whenNotFound() {
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8081/api/users/me"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        Optional<UserClient.UserSummaryDto> result = userClient.getUserById(userId);

        assertThat(result).isEmpty();
        mockServer.verify();
    }
}
