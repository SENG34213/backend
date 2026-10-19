package com.gamingcastle.paymentservice.controller;

import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.AdminPaymentStatsResponse;
import com.gamingcastle.paymentservice.dto.response.PaymentResponse;
import com.gamingcastle.paymentservice.entity.PaymentMethod;
import com.gamingcastle.paymentservice.entity.PaymentStatus;
import com.gamingcastle.paymentservice.entity.ReferenceType;
import com.gamingcastle.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private PaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymentController(paymentService);
    }

    @Test
    @DisplayName("processPayment: extracts userId from authentication and returns 201 Created")
    void processPayment_shouldReturnCreated() {
        UUID userId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("100.00"));

        PaymentResponse mockResponse = PaymentResponse.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentService.processPayment(request, userId)).thenReturn(mockResponse);

        ResponseEntity<PaymentResponse> response = controller.processPayment(request, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(mockResponse);
        verify(paymentService).processPayment(request, userId);
    }

    @Test
    @DisplayName("processCashPayment: delegates to service and returns 201 Created")
    void processCashPayment_shouldReturnCreated() {
        UUID adminId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("75.00"));

        PaymentResponse mockResponse = PaymentResponse.builder()
                .id(UUID.randomUUID())
                .userId(adminId)
                .amount(new BigDecimal("75.00"))
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentService.processCashPayment(request, adminId)).thenReturn(mockResponse);

        ResponseEntity<PaymentResponse> response = controller.processCashPayment(adminId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(mockResponse);
        verify(paymentService).processCashPayment(request, adminId);
    }

    @Test
    @DisplayName("getPaymentReceipt: extracts userId and role and returns 200 OK")
    void getPaymentReceipt_shouldReturnReceipt() {
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        PaymentResponse mockResponse = PaymentResponse.builder()
                .id(paymentId)
                .userId(userId)
                .status(PaymentStatus.SUCCESS)
                .receiptNumber("GC-REC-001")
                .build();

        when(paymentService.getPaymentReceipt(paymentId, userId, "CUSTOMER")).thenReturn(mockResponse);

        ResponseEntity<PaymentResponse> response = controller.getPaymentReceipt(paymentId, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mockResponse);
        verify(paymentService).getPaymentReceipt(paymentId, userId, "CUSTOMER");
    }

    @Test
    @DisplayName("getMyPayments: delegates to service and returns list of user payments")
    void getMyPayments_shouldReturnPaymentsList() {
        UUID userId = UUID.randomUUID();
        PaymentResponse p1 = PaymentResponse.builder().id(UUID.randomUUID()).build();
        when(paymentService.getMyPayments(userId)).thenReturn(List.of(p1));

        ResponseEntity<List<PaymentResponse>> response = controller.getMyPayments(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(paymentService).getMyPayments(userId);
    }

    @Test
    @DisplayName("getAdminPayments: parses pagination, sorting, filters, and caps size at 100")
    void getAdminPayments_shouldParseParametersAndReturnPagedResults() {
        UUID targetUserId = UUID.randomUUID();
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-18T23:59:59Z");

        Page<PaymentResponse> mockPage = new PageImpl<>(List.of());
        when(paymentService.getAdminPayments(
                eq(PaymentStatus.SUCCESS),
                eq(PaymentMethod.CARD),
                eq(ReferenceType.BOOKING),
                eq(targetUserId),
                eq(from),
                eq(to),
                any(Pageable.class)
        )).thenReturn(mockPage);

        ResponseEntity<Page<PaymentResponse>> response = controller.getAdminPayments(
                PaymentStatus.SUCCESS,
                PaymentMethod.CARD,
                ReferenceType.BOOKING,
                targetUserId,
                from,
                to,
                0,
                150, // exceeds max 100
                "amount,asc"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentService).getAdminPayments(
                eq(PaymentStatus.SUCCESS),
                eq(PaymentMethod.CARD),
                eq(ReferenceType.BOOKING),
                eq(targetUserId),
                eq(from),
                eq(to),
                pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(100); // capped at 100
        assertThat(pageable.getSort().getOrderFor("amount").isAscending()).isTrue();
    }

    @Test
    @DisplayName("getPaymentStats: clamps trendDays between 1 and 365 and returns 200 OK")
    void getPaymentStats_shouldClampTrendDays() {
        AdminPaymentStatsResponse mockStats = new AdminPaymentStatsResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, List.of());

        when(paymentService.getPaymentStats(365)).thenReturn(mockStats);

        ResponseEntity<AdminPaymentStatsResponse> response = controller.getPaymentStats(500);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(paymentService).getPaymentStats(365);
    }

    @Test
    @DisplayName("exportPaymentsCsv: produces CSV formatted content with headers and values")
    void exportPaymentsCsv_shouldReturnCsvContent() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID refId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-18T10:30:00Z");

        PaymentResponse p = PaymentResponse.builder()
                .id(id)
                .userId(userId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(refId)
                .amount(new BigDecimal("95.00"))
                .originalAmount(new BigDecimal("100.00"))
                .loyaltyPointsUsed(20)
                .loyaltyDiscount(new BigDecimal("5.00"))
                .method(PaymentMethod.ONLINE)
                .status(PaymentStatus.SUCCESS)
                .receiptNumber("GC-CSV-1")
                .createdAt(now)
                .build();

        when(paymentService.getAdminPayments(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        ResponseEntity<String> response = controller.exportPaymentsCsv(
                null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("text/csv"));
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("attachment; filename=\"payments-export.csv\"");

        String csvBody = response.getBody();
        assertThat(csvBody).contains("id,userId,referenceType,referenceId,amount,originalAmount,loyaltyPointsUsed,loyaltyDiscount,method,status,receiptNumber,createdAt");
        assertThat(csvBody).contains(id.toString());
        assertThat(csvBody).contains("95.00");
        assertThat(csvBody).contains("GC-CSV-1");
    }
}
