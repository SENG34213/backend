package com.gamingcastle.paymentservice.controller;

import com.gamingcastle.paymentservice.client.LoyaltyClient;
import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.AdminPaymentStatsResponse;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentControllerIntegrationTest {

    private static final String GATEWAY_SECRET = "test-gateway-internal-secret";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private LoyaltyClient loyaltyClient;

    private UUID customerUserId;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        customerUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
    }

    private HttpHeaders customerHeaders(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", GATEWAY_SECRET);
        headers.set("X-User-Id", userId.toString());
        headers.set("X-User-Role", "CUSTOMER");
        return headers;
    }

    private HttpHeaders adminHeaders(UUID adminId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", GATEWAY_SECRET);
        headers.set("X-User-Id", adminId.toString());
        headers.set("X-User-Role", "ADMIN");
        return headers;
    }

    // ── 1. POST /api/payments ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/payments: successfully processes customer payment and saves to database")
    void processPayment_shouldCreatePaymentAndPersistToDb() {
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest();
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(bookingId);
        request.setAmount(new BigDecimal("100.00"));
        request.setMethod(PaymentMethod.ONLINE);
        request.setIdempotencyKey("it-online-100");

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, customerHeaders(customerUserId));

        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                "/api/payments", entity, PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getUserId()).isEqualTo(customerUserId);
        assertThat(body.getAmount()).isEqualByComparingTo("100.00");
        assertThat(body.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(body.getReceiptNumber()).startsWith("GC-");

        // Verify DB state
        Payment persisted = paymentRepository.findById(body.getId()).orElse(null);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(persisted.getIdempotencyKey()).isEqualTo("it-online-100");
        verify(loyaltyClient).award(eq(customerUserId), eq(bookingId), eq(new BigDecimal("100.00")), eq(body.getId()));
    }

    @Test
    @DisplayName("POST /api/payments: processes payment with loyalty points redemption")
    void processPayment_shouldRedeemLoyaltyPointsAndRecordDiscount() {
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest();
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(bookingId);
        request.setAmount(new BigDecimal("100.00"));
        request.setMethod(PaymentMethod.CARD);
        request.setIdempotencyKey("it-loyalty-pts");
        request.setLoyaltyPointsToRedeem(20);

        when(loyaltyClient.reserve(customerUserId, bookingId, new BigDecimal("100.00"), 20))
                .thenReturn(new LoyaltyClient.ReserveResponse(UUID.randomUUID(), 20, new BigDecimal("5.00"), new BigDecimal("95.00"), 200L));

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, customerHeaders(customerUserId));

        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                "/api/payments", entity, PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAmount()).isEqualByComparingTo("95.00");
        assertThat(body.getOriginalAmount()).isEqualByComparingTo("100.00");
        assertThat(body.getLoyaltyPointsUsed()).isEqualTo(20);
        assertThat(body.getLoyaltyDiscount()).isEqualByComparingTo("5.00");
        assertThat(body.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        verify(loyaltyClient).reserve(customerUserId, bookingId, new BigDecimal("100.00"), 20);
        verify(loyaltyClient).confirm(bookingId);
        verify(loyaltyClient).award(eq(customerUserId), eq(bookingId), eq(new BigDecimal("95.00")), eq(body.getId()));
    }

    @Test
    @DisplayName("POST /api/payments: records FAILED status when gateway rejects amount ending in .13")
    void processPayment_shouldRecordFailed_whenGatewayRejects() {
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest();
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(bookingId);
        request.setAmount(new BigDecimal("50.13")); // MockPaymentGateway rejects .13
        request.setMethod(PaymentMethod.ONLINE);
        request.setIdempotencyKey("it-fail-13");

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, customerHeaders(customerUserId));

        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                "/api/payments", entity, PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(body.getReceiptNumber()).isNull();

        Payment persisted = paymentRepository.findById(body.getId()).orElse(null);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("POST /api/payments: enforces idempotency by returning existing payment on duplicate request")
    void processPayment_shouldBeIdempotent() {
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest();
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(bookingId);
        request.setAmount(new BigDecimal("80.00"));
        request.setMethod(PaymentMethod.ONLINE);
        request.setIdempotencyKey("it-idempotency-key");

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, customerHeaders(customerUserId));

        ResponseEntity<PaymentResponse> firstResponse = restTemplate.postForEntity(
                "/api/payments", entity, PaymentResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<PaymentResponse> secondResponse = restTemplate.postForEntity(
                "/api/payments", entity, PaymentResponse.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(secondResponse.getBody().getId()).isEqualTo(firstResponse.getBody().getId());
        assertThat(secondResponse.getBody().getReceiptNumber()).isEqualTo(firstResponse.getBody().getReceiptNumber());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/payments: returns 400 Bad Request on validation failure (amount <= 0)")
    void processPayment_shouldReturn400_whenAmountIsZeroOrNegative() {
        PaymentRequest request = new PaymentRequest();
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(UUID.randomUUID());
        request.setAmount(new BigDecimal("0.00"));
        request.setMethod(PaymentMethod.ONLINE);
        request.setIdempotencyKey("it-zero-amount");

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, customerHeaders(customerUserId));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/payments", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Amount must be greater than zero");
    }

    @Test
    @DisplayName("POST /api/payments: returns 401 Unauthorized when gateway secret is missing")
    void processPayment_shouldReturn401_whenGatewaySecretMissing() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", customerUserId.toString());
        headers.set("X-User-Role", "CUSTOMER");

        PaymentRequest request = new PaymentRequest();
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(UUID.randomUUID());
        request.setAmount(new BigDecimal("25.00"));
        request.setMethod(PaymentMethod.CARD);
        request.setIdempotencyKey("it-no-secret");

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/payments", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── 2. POST /api/payments/cash ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/payments/cash: Admin successfully records cash payment for booking")
    void processCashPayment_shouldCreateCashPayment_whenAdmin() {
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest();
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(bookingId);
        request.setAmount(new BigDecimal("45.00"));
        request.setMethod(PaymentMethod.CASH);
        request.setIdempotencyKey("it-cash-booking");
        request.setCustomerUserId(customerUserId);

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, adminHeaders(adminUserId));

        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                "/api/payments/cash", entity, PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getUserId()).isEqualTo(customerUserId);
        assertThat(body.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(body.getMethod()).isEqualTo(PaymentMethod.CASH);

        Payment persisted = paymentRepository.findById(body.getId()).orElse(null);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getRecordedBy()).isEqualTo(adminUserId);
        verify(loyaltyClient).award(eq(customerUserId), eq(bookingId), eq(new BigDecimal("45.00")), eq(body.getId()));
    }

    @Test
    @DisplayName("POST /api/payments/cash: returns 400 Bad Request when customerUserId is missing for booking")
    void processCashPayment_shouldReturn400_whenCustomerUserIdMissingForBooking() {
        PaymentRequest request = new PaymentRequest();
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(UUID.randomUUID());
        request.setAmount(new BigDecimal("45.00"));
        request.setMethod(PaymentMethod.CASH);
        request.setIdempotencyKey("it-cash-no-customer");
        // customerUserId omitted

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, adminHeaders(adminUserId));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/payments/cash", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("customerUserId is required for booking cash payments");
    }

    @Test
    @DisplayName("POST /api/payments/cash: returns 403 Forbidden when called by CUSTOMER")
    void processCashPayment_shouldReturn403_whenCalledByCustomer() {
        PaymentRequest request = new PaymentRequest();
        request.setReferenceType(ReferenceType.BOOKING);
        request.setReferenceId(UUID.randomUUID());
        request.setAmount(new BigDecimal("45.00"));
        request.setMethod(PaymentMethod.CASH);
        request.setIdempotencyKey("it-cash-customer-role");

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, customerHeaders(customerUserId));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/payments/cash", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 3. GET /api/payments/{id}/receipt ───────────────────────────────────────

    @Test
    @DisplayName("GET /api/payments/{id}/receipt: customer successfully retrieves their own receipt")
    void getPaymentReceipt_shouldReturnReceipt_forOwner() {
        Payment payment = paymentRepository.save(Payment.builder()
                .userId(customerUserId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .amount(new BigDecimal("60.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("it-receipt-owner")
                .receiptNumber("GC-REC-TEST-1")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));

        ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                "/api/payments/" + payment.getId() + "/receipt",
                HttpMethod.GET,
                entity,
                PaymentResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(payment.getId());
        assertThat(response.getBody().getReceiptNumber()).isEqualTo("GC-REC-TEST-1");
    }

    @Test
    @DisplayName("GET /api/payments/{id}/receipt: admin can retrieve any customer receipt")
    void getPaymentReceipt_shouldReturnReceipt_forAdmin() {
        Payment payment = paymentRepository.save(Payment.builder()
                .userId(customerUserId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .amount(new BigDecimal("60.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("it-receipt-admin")
                .receiptNumber("GC-REC-ADMIN-1")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(adminHeaders(adminUserId));

        ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                "/api/payments/" + payment.getId() + "/receipt",
                HttpMethod.GET,
                entity,
                PaymentResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getReceiptNumber()).isEqualTo("GC-REC-ADMIN-1");
    }

    @Test
    @DisplayName("GET /api/payments/{id}/receipt: returns 400 when customer tries to access another user's receipt")
    void getPaymentReceipt_shouldReturn400_whenCustomerAccessesOtherUserReceipt() {
        UUID otherUserId = UUID.randomUUID();
        Payment payment = paymentRepository.save(Payment.builder()
                .userId(otherUserId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .amount(new BigDecimal("60.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("it-receipt-other")
                .receiptNumber("GC-REC-OTHER-1")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/payments/" + payment.getId() + "/receipt",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("You are not authorized to access this payment");
    }

    // ── 4. GET /api/payments/mine ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/payments/mine: returns list of payments belonging to the user")
    void getMyPayments_shouldReturnOnlyUserPayments() {
        paymentRepository.save(Payment.builder()
                .userId(customerUserId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .amount(new BigDecimal("30.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("mine-1")
                .build());

        paymentRepository.save(Payment.builder()
                .userId(customerUserId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .amount(new BigDecimal("40.00"))
                .method(PaymentMethod.ONLINE)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("mine-2")
                .build());

        // Payment belonging to different user
        paymentRepository.save(Payment.builder()
                .userId(UUID.randomUUID())
                .referenceType(ReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .method(PaymentMethod.ONLINE)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("mine-other")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));

        ResponseEntity<List<PaymentResponse>> response = restTemplate.exchange(
                "/api/payments/mine",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).allMatch(p -> p.getUserId().equals(customerUserId));
    }

    // ── 5. GET /api/payments/admin/all ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/payments/admin/all: Admin retrieves filtered paged payments")
    void getAdminPayments_shouldReturnPagedResults_whenAdmin() {
        paymentRepository.save(Payment.builder()
                .userId(customerUserId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .amount(new BigDecimal("70.00"))
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("admin-paged-1")
                .build());

        paymentRepository.save(Payment.builder()
                .userId(customerUserId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .amount(new BigDecimal("35.00"))
                .method(PaymentMethod.ONLINE)
                .status(PaymentStatus.FAILED)
                .idempotencyKey("admin-paged-2")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(adminHeaders(adminUserId));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/payments/admin/all?status=SUCCESS&method=CASH",
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).hasSize(1);
    }

    @Test
    @DisplayName("GET /api/payments/admin/all: returns 403 Forbidden when called by CUSTOMER")
    void getAdminPayments_shouldReturn403_whenCalledByCustomer() {
        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/payments/admin/all",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 6. GET /api/payments/admin/stats ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/payments/admin/stats: Admin retrieves dashboard stats")
    void getPaymentStats_shouldReturnStats_whenAdmin() {
        paymentRepository.save(Payment.builder()
                .userId(customerUserId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("stats-1")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(adminHeaders(adminUserId));

        ResponseEntity<AdminPaymentStatsResponse> response = restTemplate.exchange(
                "/api/payments/admin/stats?trendDays=14",
                HttpMethod.GET,
                entity,
                AdminPaymentStatsResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalPaymentsSuccess()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("GET /api/payments/admin/stats: returns 403 Forbidden when called by CUSTOMER")
    void getPaymentStats_shouldReturn403_whenCalledByCustomer() {
        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/payments/admin/stats",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 7. GET /api/payments/admin/export.csv ───────────────────────────────────

    @Test
    @DisplayName("GET /api/payments/admin/export.csv: Admin exports payment log to CSV")
    void exportPaymentsCsv_shouldReturnCsvFile_whenAdmin() {
        paymentRepository.save(Payment.builder()
                .userId(customerUserId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(UUID.randomUUID())
                .amount(new BigDecimal("120.00"))
                .originalAmount(new BigDecimal("120.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("export-1")
                .receiptNumber("GC-EXP-1")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(adminHeaders(adminUserId));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/payments/admin/export.csv",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("attachment; filename=\"payments-export.csv\"");

        String csv = response.getBody();
        assertThat(csv).isNotNull();
        assertThat(csv).startsWith("id,userId,referenceType,referenceId,amount,originalAmount");
        assertThat(csv).contains("GC-EXP-1");
        assertThat(csv).contains("120.00");
    }

    @Test
    @DisplayName("GET /api/payments/admin/export.csv: returns 403 Forbidden when called by CUSTOMER")
    void exportPaymentsCsv_shouldReturn403_whenCalledByCustomer() {
        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/payments/admin/export.csv",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
