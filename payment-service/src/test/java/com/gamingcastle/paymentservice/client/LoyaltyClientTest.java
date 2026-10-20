package com.gamingcastle.paymentservice.client;

import com.gamingcastle.paymentservice.exception.LoyaltyRejectedException;
import com.gamingcastle.paymentservice.exception.LoyaltyUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LoyaltyClientTest {

    private static final String BASE_URL = "http://localhost:8085";
    private static final String SECRET = "test-secret";

    private LoyaltyClient loyaltyClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();

        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any())).thenReturn(builder);
        when(builder.setReadTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);

        loyaltyClient = new LoyaltyClient(builder, SECRET, BASE_URL, 100, 100);
    }

    @Test
    @DisplayName("reserve: returns ReserveResponse when successful")
    void reserve_shouldReturnResponse_whenSuccessful() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        String jsonResponse = """
                {
                    "transactionId": "%s",
                    "pointsReserved": 50,
                    "discountAmount": 10.00,
                    "payableAmount": 90.00,
                    "newBalance": 200
                }
                """.formatted(txId);

        mockServer.expect(requestTo("http://localhost:8085/api/internal/loyalty/redeem/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", SECRET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        LoyaltyClient.ReserveResponse response =
                loyaltyClient.reserve(userId, bookingId, new BigDecimal("100.00"), 50);

        assertThat(response).isNotNull();
        assertThat(response.transactionId()).isEqualTo(txId);
        assertThat(response.pointsReserved()).isEqualTo(50L);
        assertThat(response.discountAmount()).isEqualByComparingTo("10.00");
        assertThat(response.payableAmount()).isEqualByComparingTo("90.00");
        assertThat(response.newBalance()).isEqualTo(200L);

        mockServer.verify();
    }

    @Test
    @DisplayName("reserve: throws LoyaltyRejectedException on 422 Unprocessable Entity")
    void reserve_shouldThrowLoyaltyRejectedException_when422() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        String errorResponse = "{\"message\":\"Insufficient points\"}";

        mockServer.expect(requestTo("http://localhost:8085/api/internal/loyalty/redeem/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withRawStatus(422).contentType(MediaType.APPLICATION_JSON).body(errorResponse));

        assertThatThrownBy(() -> loyaltyClient.reserve(userId, bookingId, new BigDecimal("100.00"), 50))
                .isInstanceOf(LoyaltyRejectedException.class)
                .hasMessage("Insufficient points");

        mockServer.verify();
    }

    @Test
    @DisplayName("reserve: throws LoyaltyUnavailableException on 500 Internal Server Error")
    void reserve_shouldThrowLoyaltyUnavailableException_when500() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8085/api/internal/loyalty/redeem/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> loyaltyClient.reserve(userId, bookingId, new BigDecimal("100.00"), 50))
                .isInstanceOf(LoyaltyUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");

        mockServer.verify();
    }

    @Test
    @DisplayName("confirm: posts to confirm endpoint")
    void confirm_shouldPostToConfirmEndpoint() {
        UUID bookingId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8085/api/internal/loyalty/redeem/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", SECRET))
                .andRespond(withStatus(HttpStatus.OK));

        loyaltyClient.confirm(bookingId);

        mockServer.verify();
    }

    @Test
    @DisplayName("release: posts to release endpoint")
    void release_shouldPostToReleaseEndpoint() {
        UUID bookingId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8085/api/internal/loyalty/redeem/release"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", SECRET))
                .andRespond(withStatus(HttpStatus.OK));

        loyaltyClient.release(bookingId, "Payment cancelled");

        mockServer.verify();
    }

    @Test
    @DisplayName("award: posts to award endpoint")
    void award_shouldPostToAwardEndpoint() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8085/api/internal/loyalty/award"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", SECRET))
                .andRespond(withStatus(HttpStatus.OK));

        loyaltyClient.award(userId, bookingId, new BigDecimal("100.00"), paymentId);

        mockServer.verify();
    }

    @Test
    @DisplayName("award: retries once when first attempt fails")
    void award_shouldRetryOnFailure() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8085/api/internal/loyalty/award"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        mockServer.expect(requestTo("http://localhost:8085/api/internal/loyalty/award"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK));

        loyaltyClient.award(userId, bookingId, new BigDecimal("100.00"), paymentId);

        mockServer.verify();
    }
}
