package com.gamingcastle.bookingservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserClientTest {

    private static final String GATEWAY_SECRET = "test-gateway-secret-user";

    private UserClient userClient;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userClient = new UserClient();
        ReflectionTestUtils.setField(userClient, "gatewaySecret", GATEWAY_SECRET);

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(userClient, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
        objectMapper = new ObjectMapper();

        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getUserById: returns user summary when user service responds 200 OK")
    void getUserById_shouldReturnUser_whenSuccessful() throws Exception {
        UserClient.UserSummaryDto dto = new UserClient.UserSummaryDto(
                userId, "player", "player@example.com", "Test Player", "CUSTOMER");

        mockServer.expect(requestTo("http://localhost:8081/api/users/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(header("X-User-Id", userId.toString()))
                .andRespond(withSuccess(objectMapper.writeValueAsString(dto), MediaType.APPLICATION_JSON));

        Optional<UserClient.UserSummaryDto> result = userClient.getUserById(userId);

        assertThat(result).isPresent();
        assertThat(result.get().email()).isEqualTo("player@example.com");
        assertThat(result.get().fullName()).isEqualTo("Test Player");
        mockServer.verify();
    }

    @Test
    @DisplayName("getUserById: omits X-Gateway-Secret when blank")
    void getUserById_shouldOmitSecret_whenBlank() throws Exception {
        ReflectionTestUtils.setField(userClient, "gatewaySecret", "");

        UserClient.UserSummaryDto dto = new UserClient.UserSummaryDto(
                userId, "player", "player@example.com", "Test Player", "CUSTOMER");

        mockServer.expect(requestTo("http://localhost:8081/api/users/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", userId.toString()))
                .andRespond(withSuccess(objectMapper.writeValueAsString(dto), MediaType.APPLICATION_JSON));

        Optional<UserClient.UserSummaryDto> result = userClient.getUserById(userId);

        assertThat(result).isPresent();
        mockServer.verify();
    }

    @Test
    @DisplayName("getUserById: returns empty Optional when service returns error")
    void getUserById_shouldReturnEmpty_whenServiceFails() {
        mockServer.expect(requestTo("http://localhost:8081/api/users/me"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<UserClient.UserSummaryDto> result = userClient.getUserById(userId);

        assertThat(result).isEmpty();
        mockServer.verify();
    }
}
