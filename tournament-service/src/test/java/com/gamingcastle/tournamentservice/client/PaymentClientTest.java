package com.gamingcastle.tournamentservice.client;

import com.gamingcastle.tournamentservice.exception.PaymentFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PaymentClientTest {

    private static final String BASE_URL = "http://localhost:8083";
    private static final String SECRET = "test-secret";

    private PaymentClient paymentClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        paymentClient = new PaymentClient();
        RestTemplate restTemplate = new RestTemplate();
        ReflectionTestUtils.setField(paymentClient, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(paymentClient, "paymentBaseUrl", BASE_URL);
        ReflectionTestUtils.setField(paymentClient, "gatewaySecret", SECRET);

        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    @DisplayName("chargeEntryFee should return paymentId when payment-service returns SUCCESS")
    void chargeEntryFee_shouldReturnPaymentId_whenSuccess() {
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        String jsonResponse = """
                {
                    "id": "%s",
                    "status": "SUCCESS",
                    "receiptNumber": "REC-12345"
                }
                """.formatted(paymentId);

        mockServer.expect(requestTo("http://localhost:8083/api/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", SECRET))
                .andExpect(header("X-User-Id", userId.toString()))
                .andExpect(header("X-User-Role", "CUSTOMER"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        UUID result = paymentClient.chargeEntryFee(new BigDecimal("1500.00"), userId, registrationId);

        assertThat(result).isEqualTo(paymentId);
        mockServer.verify();
    }

    @Test
    @DisplayName("chargeEntryFee should throw PaymentFailedException when payment-service returns FAILED status")
    void chargeEntryFee_shouldThrowPaymentFailed_whenStatusFailed() {
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        String jsonResponse = """
                {
                    "id": "%s",
                    "status": "FAILED",
                    "receiptNumber": null
                }
                """.formatted(paymentId);

        mockServer.expect(requestTo("http://localhost:8083/api/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> paymentClient.chargeEntryFee(new BigDecimal("1500.00"), userId, registrationId))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("Entry fee payment failed (status=FAILED)");

        mockServer.verify();
    }

    @Test
    @DisplayName("chargeEntryFee should throw PaymentFailedException when payment-service returns 400 Bad Request")
    void chargeEntryFee_shouldThrowPaymentFailed_when400BadRequest() {
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8083/api/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().body("{\"error\":\"INVALID_CARD\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> paymentClient.chargeEntryFee(new BigDecimal("1500.00"), userId, registrationId))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("Payment service error (400 BAD_REQUEST)");

        mockServer.verify();
    }

    @Test
    @DisplayName("chargeEntryFee should throw PaymentFailedException when payment-service returns 500 Server Error")
    void chargeEntryFee_shouldThrowPaymentFailed_when500ServerError() {
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8083/api/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> paymentClient.chargeEntryFee(new BigDecimal("1500.00"), userId, registrationId))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("Could not reach payment service");

        mockServer.verify();
    }

    @Test
    @DisplayName("chargeEntryFee should work without gateway secret header when secret is blank")
    void chargeEntryFee_shouldWorkWithoutSecret_whenBlank() {
        ReflectionTestUtils.setField(paymentClient, "gatewaySecret", "");

        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        String jsonResponse = """
                {
                    "id": "%s",
                    "status": "SUCCESS",
                    "receiptNumber": "REC-999"
                }
                """.formatted(paymentId);

        mockServer.expect(requestTo("http://localhost:8083/api/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        UUID result = paymentClient.chargeEntryFee(new BigDecimal("500.00"), userId, registrationId);

        assertThat(result).isEqualTo(paymentId);
        mockServer.verify();
    }
}
