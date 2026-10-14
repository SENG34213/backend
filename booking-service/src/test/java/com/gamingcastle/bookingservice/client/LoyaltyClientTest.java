package com.gamingcastle.bookingservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LoyaltyClientTest {

    private static final String BASE_URL = "http://localhost:8085/";
    private static final String GATEWAY_SECRET = "test-gateway-secret-loyalty";

    private LoyaltyClient loyaltyClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        RestTemplateBuilder builder = new RestTemplateBuilder() {
            @Override
            public RestTemplate build() {
                return restTemplate;
            }
            @Override
            public RestTemplateBuilder setConnectTimeout(Duration connectTimeout) {
                return this;
            }
            @Override
            public RestTemplateBuilder setReadTimeout(Duration readTimeout) {
                return this;
            }
        };

        loyaltyClient = new LoyaltyClient(builder, GATEWAY_SECRET, BASE_URL, 1000, 1000);
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("reverse: succeeds on first attempt with 200 OK and headers")
    void reverse_shouldSucceedOnFirstAttempt() {
        UUID bookingId = UUID.randomUUID();

        mockServer.expect(once(), requestTo("http://localhost:8085/api/internal/loyalty/reverse"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.reason").value("Booking cancelled"))
                .andRespond(withSuccess());

        loyaltyClient.reverse(bookingId, "Booking cancelled");

        mockServer.verify();
    }

    @Test
    @DisplayName("reverse: retries once on server error and succeeds on second attempt")
    void reverse_shouldRetryAndSucceedOnSecondAttempt() {
        UUID bookingId = UUID.randomUUID();

        mockServer.expect(once(), requestTo("http://localhost:8085/api/internal/loyalty/reverse"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        mockServer.expect(once(), requestTo("http://localhost:8085/api/internal/loyalty/reverse"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        loyaltyClient.reverse(bookingId, "Booking cancelled");

        mockServer.verify();
    }

    @Test
    @DisplayName("reverse: logs error and completes cleanly when both attempts fail")
    void reverse_shouldHandleExhaustedRetriesGracefully() {
        UUID bookingId = UUID.randomUUID();

        mockServer.expect(once(), requestTo("http://localhost:8085/api/internal/loyalty/reverse"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        mockServer.expect(once(), requestTo("http://localhost:8085/api/internal/loyalty/reverse"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatCode(() -> loyaltyClient.reverse(bookingId, "Booking cancelled"))
                .doesNotThrowAnyException();

        mockServer.verify();
    }
}
