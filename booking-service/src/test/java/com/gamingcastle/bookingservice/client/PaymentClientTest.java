package com.gamingcastle.bookingservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamingcastle.bookingservice.exception.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentClientTest {

    private static final String BASE_URL = "http://localhost:8083";
    private static final String GATEWAY_SECRET = "test-gateway-secret-123";

    private PaymentClient paymentClient;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID bookingId;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        paymentClient = new PaymentClient();
        ReflectionTestUtils.setField(paymentClient, "paymentBaseUrl", BASE_URL);
        ReflectionTestUtils.setField(paymentClient, "gatewaySecret", GATEWAY_SECRET);

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(paymentClient, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
        objectMapper = new ObjectMapper();

        userId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("chargeBookingFee: successful charge with explicit method and loyalty points")
    void chargeBookingFee_shouldSucceed_whenPaymentSuccessful() throws Exception {
        BigDecimal amount = BigDecimal.valueOf(600);
        BigDecimal discount = BigDecimal.valueOf(50);
        BigDecimal payable = BigDecimal.valueOf(550);

        PaymentClient.PaymentResponse responseBody = new PaymentClient.PaymentResponse(
                paymentId, "SUCCESS", "RCPT-123", payable, amount, discount);

        mockServer.expect(requestTo(BASE_URL + "/api/internal/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(jsonPath("$.referenceType").value("BOOKING"))
                .andExpect(jsonPath("$.referenceId").value(bookingId.toString()))
                .andExpect(jsonPath("$.amount").value(600))
                .andExpect(jsonPath("$.method").value("ONLINE"))
                .andExpect(jsonPath("$.loyaltyPointsToRedeem").value(50))
                .andExpect(jsonPath("$.customerUserId").value(userId.toString()))
                .andRespond(withSuccess(objectMapper.writeValueAsString(responseBody), MediaType.APPLICATION_JSON));

        PaymentClient.PaymentResult result = paymentClient.chargeBookingFee(amount, userId, bookingId, "online", 50);

        assertThat(result).isNotNull();
        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.totalAmount()).isEqualByComparingTo(amount);
        assertThat(result.discountAmount()).isEqualByComparingTo(discount);
        assertThat(result.payableAmount()).isEqualByComparingTo(payable);

        mockServer.verify();
    }

    @Test
    @DisplayName("chargeBookingFee: defaults to CARD method when method is null or blank, handles null secret")
    void chargeBookingFee_shouldDefaultToCard_whenMethodNull() throws Exception {
        ReflectionTestUtils.setField(paymentClient, "gatewaySecret", ""); // blank secret

        PaymentClient.PaymentResponse responseBody = new PaymentClient.PaymentResponse(
                paymentId, "SUCCESS", "RCPT-123", BigDecimal.valueOf(300), null, null);

        mockServer.expect(requestTo(BASE_URL + "/api/internal/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(responseBody), MediaType.APPLICATION_JSON));

        PaymentClient.PaymentResult result = paymentClient.chargeBookingFee(BigDecimal.valueOf(300), userId, bookingId, null, null);

        assertThat(result.paymentId()).isEqualTo(paymentId);
        // Fallbacks when originalAmount and discount are null in response
        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(result.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        mockServer.verify();
    }

    @Test
    @DisplayName("chargeBookingFee: throws 402 PAYMENT_REQUIRED when response body is null")
    void chargeBookingFee_shouldThrow402_whenResponseBodyNull() {
        mockServer.expect(requestTo(BASE_URL + "/api/internal/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> paymentClient.chargeBookingFee(BigDecimal.valueOf(300), userId, bookingId, "CARD", null))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                    assertThat(be.getMessage()).contains("empty response");
                });
    }

    @Test
    @DisplayName("chargeBookingFee: throws 402 PAYMENT_REQUIRED when payment status is not SUCCESS")
    void chargeBookingFee_shouldThrow402_whenStatusFailed() throws Exception {
        PaymentClient.PaymentResponse responseBody = new PaymentClient.PaymentResponse(
                paymentId, "FAILED", null, BigDecimal.valueOf(300), BigDecimal.valueOf(300), BigDecimal.ZERO);

        mockServer.expect(requestTo(BASE_URL + "/api/internal/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(responseBody), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> paymentClient.chargeBookingFee(BigDecimal.valueOf(300), userId, bookingId, "CARD", null))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                    assertThat(be.getMessage()).contains("status=FAILED");
                });
    }

    @Test
    @DisplayName("chargeBookingFee: throws 402 PAYMENT_REQUIRED on HttpClientErrorException (4xx)")
    void chargeBookingFee_shouldThrow402_onHttpError() {
        mockServer.expect(requestTo(BASE_URL + "/api/internal/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"INVALID_CARD\"}"));

        assertThatThrownBy(() -> paymentClient.chargeBookingFee(BigDecimal.valueOf(300), userId, bookingId, "CARD", null))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                    assertThat(be.getMessage()).contains("400");
                });
    }

    @Test
    @DisplayName("chargeBookingFee: throws 402 PAYMENT_REQUIRED on 5xx or connection error")
    void chargeBookingFee_shouldThrow402_onServerError() {
        mockServer.expect(requestTo(BASE_URL + "/api/internal/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> paymentClient.chargeBookingFee(BigDecimal.valueOf(300), userId, bookingId, "CARD", null))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                    assertThat(be.getMessage()).contains("Could not reach payment service");
                });
    }
}
