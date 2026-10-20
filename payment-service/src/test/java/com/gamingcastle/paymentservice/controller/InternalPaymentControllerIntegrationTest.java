package com.gamingcastle.paymentservice.controller;

import com.gamingcastle.paymentservice.client.LoyaltyClient;
import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.PaymentResponse;
import com.gamingcastle.paymentservice.entity.Payment;
import com.gamingcastle.paymentservice.entity.PaymentMethod;
import com.gamingcastle.paymentservice.entity.PaymentStatus;
import com.gamingcastle.paymentservice.entity.ReferenceType;
import com.gamingcastle.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InternalPaymentControllerIntegrationTest {

    private static final String GATEWAY_SECRET = "test-gateway-internal-secret";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private LoyaltyClient loyaltyClient;

    private UUID customerUserId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        customerUserId = UUID.randomUUID();
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", GATEWAY_SECRET);
        return headers;
    }

    @Test
    @DisplayName("POST /api/internal/payments: service successfully processes payment for customer")
    void processInternalPayment_shouldProcessAndSavePayment() {
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest();
        request.setCustomerUserId(customerUserId);
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(bookingId);
        request.setAmount(new BigDecimal("150.00"));
        request.setMethod(PaymentMethod.ONLINE);
        request.setIdempotencyKey("it-internal-150");

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, internalHeaders());

        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                "/api/internal/payments", entity, PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getUserId()).isEqualTo(customerUserId);
        assertThat(body.getAmount()).isEqualByComparingTo("150.00");
        assertThat(body.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        Payment persisted = paymentRepository.findById(body.getId()).orElse(null);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getUserId()).isEqualTo(customerUserId);
        verify(loyaltyClient).award(eq(customerUserId), eq(bookingId), eq(new BigDecimal("150.00")), eq(body.getId()));
    }

    @Test
    @DisplayName("POST /api/internal/payments: returns 400 Bad Request when customerUserId is missing")
    void processInternalPayment_shouldReturn400_whenCustomerUserIdMissing() {
        PaymentRequest request = new PaymentRequest();
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(UUID.randomUUID());
        request.setAmount(new BigDecimal("50.00"));
        request.setMethod(PaymentMethod.ONLINE);
        request.setIdempotencyKey("it-internal-no-user");
        // customerUserId omitted

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, internalHeaders());

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/internal/payments", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("customerUserId is required for internal payment processing");
    }

    @Test
    @DisplayName("POST /api/internal/payments: returns 401 Unauthorized when X-Gateway-Secret is missing")
    void processInternalPayment_shouldReturn401_whenSecretMissing() {
        PaymentRequest request = new PaymentRequest();
        request.setCustomerUserId(customerUserId);
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(UUID.randomUUID());
        request.setAmount(new BigDecimal("50.00"));
        request.setMethod(PaymentMethod.ONLINE);
        request.setIdempotencyKey("it-internal-no-secret");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/internal/payments", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
