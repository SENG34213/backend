package com.gamingcastle.paymentservice.service;

import com.gamingcastle.paymentservice.client.LoyaltyClient;
import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.PaymentResponse;
import com.gamingcastle.paymentservice.entity.Payment;
import com.gamingcastle.paymentservice.entity.PaymentMethod;
import com.gamingcastle.paymentservice.entity.PaymentStatus;
import com.gamingcastle.paymentservice.entity.ReferenceType;
import com.gamingcastle.paymentservice.exception.LoyaltyRejectedException;
import com.gamingcastle.paymentservice.gateway.PaymentGateway;
import com.gamingcastle.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private LoyaltyClient loyaltyClient;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        paymentService = new PaymentServiceImpl(paymentRepository, paymentGateway, loyaltyClient);
    }

    @Test
    void processPayment_shouldAwardOnce_withNetAmount_whenSuccessfulWithoutPoints() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("100.00"), PaymentMethod.ONLINE, ReferenceType.BOOKING, "online-no-points");

        when(paymentRepository.findByIdempotencyKey("online-no-points")).thenReturn(Optional.empty());
        when(paymentGateway.processPayment(new BigDecimal("100.00"))).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            payment.setReceiptNumber("GC-123");
            return payment;
        });

        PaymentResponse response = paymentService.processPayment(request, userId);

        assertThat(response.getAmount()).isEqualByComparingTo("100.00");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(loyaltyClient, never()).reserve(any(), any(), any(), anyLong());
        verify(loyaltyClient).award(eq(userId), eq(bookingId), eq(new BigDecimal("100.00")), any(UUID.class));
    }

    @Test
    void processPayment_shouldReserveConfirmAndAward_whenSuccessfulWithPoints() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("100.00"), PaymentMethod.ONLINE, ReferenceType.BOOKING, "online-with-points");
        request.setLoyaltyPointsToRedeem(20);

        when(paymentRepository.findByIdempotencyKey("online-with-points")).thenReturn(Optional.empty());
        when(loyaltyClient.reserve(userId, bookingId, new BigDecimal("100.00"), 20))
                .thenReturn(new LoyaltyClient.ReserveResponse(UUID.randomUUID(), 20, new BigDecimal("5.00"), new BigDecimal("95.00"), 1000L));
        when(paymentGateway.processPayment(new BigDecimal("95.00"))).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            payment.setReceiptNumber("GC-456");
            return payment;
        });

        PaymentResponse response = paymentService.processPayment(request, userId);

        assertThat(response.getAmount()).isEqualByComparingTo("95.00");
        assertThat(response.getLoyaltyPointsUsed()).isEqualTo(20);
        assertThat(response.getLoyaltyDiscount()).isEqualByComparingTo("5.00");
        verify(loyaltyClient).reserve(userId, bookingId, new BigDecimal("100.00"), 20);
        verify(loyaltyClient).confirm(bookingId);
        verify(loyaltyClient).award(eq(userId), eq(bookingId), eq(new BigDecimal("95.00")), any(UUID.class));
    }

    @Test
    void processPayment_shouldSaveFailedAndRelease_whenGatewayReturnsFalse() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("100.00"), PaymentMethod.ONLINE, ReferenceType.BOOKING, "gateway-failed");
        request.setLoyaltyPointsToRedeem(20);

        when(paymentRepository.findByIdempotencyKey("gateway-failed")).thenReturn(Optional.empty());
        when(loyaltyClient.reserve(userId, bookingId, new BigDecimal("100.00"), 20))
                .thenReturn(new LoyaltyClient.ReserveResponse(UUID.randomUUID(), 20, new BigDecimal("5.00"), new BigDecimal("95.00"), 1000L));
        when(paymentGateway.processPayment(new BigDecimal("95.00"))).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            payment.setReceiptNumber("GC-789");
            return payment;
        });

        PaymentResponse response = paymentService.processPayment(request, userId);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(loyaltyClient).release(bookingId, "Payment failed");
        verify(loyaltyClient, never()).award(any(), any(), any(), any());
    }

    @Test
    void processPayment_shouldRelease_whenGatewayThrows() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("100.00"), PaymentMethod.ONLINE, ReferenceType.BOOKING, "gateway-throws");
        request.setLoyaltyPointsToRedeem(20);

        when(paymentRepository.findByIdempotencyKey("gateway-throws")).thenReturn(Optional.empty());
        when(loyaltyClient.reserve(userId, bookingId, new BigDecimal("100.00"), 20))
                .thenReturn(new LoyaltyClient.ReserveResponse(UUID.randomUUID(), 20, new BigDecimal("5.00"), new BigDecimal("95.00"), 1000L));
        when(paymentGateway.processPayment(new BigDecimal("95.00"))).thenThrow(new RuntimeException("gateway down"));

        assertThatThrownBy(() -> paymentService.processPayment(request, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("gateway down");

        verify(loyaltyClient).release(bookingId, "Gateway failure during payment processing");
    }

    @Test
    void processPayment_shouldReject_whenLoyaltyReservationRejected() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("100.00"), PaymentMethod.ONLINE, ReferenceType.BOOKING, "reserve-rejected");
        request.setLoyaltyPointsToRedeem(20);

        when(paymentRepository.findByIdempotencyKey("reserve-rejected")).thenReturn(Optional.empty());
        when(loyaltyClient.reserve(userId, bookingId, new BigDecimal("100.00"), 20))
                .thenThrow(new LoyaltyRejectedException("You do not have enough points"));

        assertThatThrownBy(() -> paymentService.processPayment(request, userId))
                .isInstanceOf(LoyaltyRejectedException.class)
                .hasMessageContaining("You do not have enough points");

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(paymentGateway, never()).processPayment(any());
    }

    @Test
    void processPayment_shouldHonorExistingIdempotencyKey_withoutLoyaltyCalls() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment existing = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(bookingId)
                .amount(new BigDecimal("90.00"))
                .method(PaymentMethod.ONLINE)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("existing-key")
                .receiptNumber("GC-001")
                .build();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("100.00"), PaymentMethod.ONLINE, ReferenceType.BOOKING, "existing-key");

        when(paymentRepository.findByIdempotencyKey("existing-key")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.processPayment(request, userId);

        assertThat(response.getId()).isEqualTo(existing.getId());
        verifyNoInteractions(loyaltyClient);
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void processCashPayment_shouldRequireCustomerUserId_forBooking() {
        UUID adminId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(UUID.randomUUID(), new BigDecimal("60.00"), PaymentMethod.CASH, ReferenceType.BOOKING, "cash-booking");

        assertThatThrownBy(() -> paymentService.processCashPayment(request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customerUserId is required");
    }

    @Test
    void processCashPayment_shouldAwardCustomerNotAdmin_whenBookingSuccess() {
        UUID adminId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("60.00"), PaymentMethod.CASH, ReferenceType.BOOKING, "cash-booking-customer");
        request.setCustomerUserId(customerId);

        when(paymentRepository.findByIdempotencyKey("cash-booking-customer")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            payment.setReceiptNumber("GC-CASH");
            return payment;
        });

        paymentService.processCashPayment(request, adminId);

        verify(loyaltyClient).award(eq(customerId), eq(bookingId), eq(new BigDecimal("60.00")), any(UUID.class));
        verify(loyaltyClient, never()).award(eq(adminId), any(), any(), any());
    }

    @Test
    void processPayment_shouldSkipLoyalty_forTournamentEntry() {
        UUID userId = UUID.randomUUID();
        UUID tournamentId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(tournamentId, new BigDecimal("80.00"), PaymentMethod.ONLINE, ReferenceType.TOURNAMENT_ENTRY, "tournament");

        when(paymentRepository.findByIdempotencyKey("tournament")).thenReturn(Optional.empty());
        when(paymentGateway.processPayment(new BigDecimal("80.00"))).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            payment.setReceiptNumber("GC-TOUR");
            return payment;
        });

        paymentService.processPayment(request, userId);

        verify(loyaltyClient, never()).reserve(any(), any(), any(), anyLong());
        verify(loyaltyClient, never()).confirm(any());
        verify(loyaltyClient, never()).award(any(), any(), any(), any());
    }

    @Test
    @DisplayName("processPayment: throws IllegalArgumentException when amount is zero or negative")
    void processPayment_shouldThrowException_whenAmountIsZeroOrNegative() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, BigDecimal.ZERO, PaymentMethod.ONLINE, ReferenceType.BOOKING, "zero-amount");

        when(paymentRepository.findByIdempotencyKey("zero-amount")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment amount must be greater than zero");
    }

    @Test
    @DisplayName("processPayment: throws IllegalArgumentException when loyaltyPointsToRedeem is negative")
    void processPayment_shouldThrowException_whenPointsToRedeemIsNegative() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("50.00"), PaymentMethod.ONLINE, ReferenceType.BOOKING, "neg-points");
        request.setLoyaltyPointsToRedeem(-5);

        when(paymentRepository.findByIdempotencyKey("neg-points")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Loyalty points to redeem cannot be negative");
    }

    @Test
    @DisplayName("processPayment: releases loyalty points and throws when payableAmount is zero or negative")
    void processPayment_shouldReleaseAndThrow_whenPayableAmountBecomesZeroOrNegative() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("50.00"), PaymentMethod.ONLINE, ReferenceType.BOOKING, "zero-payable");
        request.setLoyaltyPointsToRedeem(100);

        when(paymentRepository.findByIdempotencyKey("zero-payable")).thenReturn(Optional.empty());
        when(loyaltyClient.reserve(userId, bookingId, new BigDecimal("50.00"), 100))
                .thenReturn(new LoyaltyClient.ReserveResponse(UUID.randomUUID(), 100, new BigDecimal("50.00"), BigDecimal.ZERO, 0L));

        assertThatThrownBy(() -> paymentService.processPayment(request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment amount must be greater than zero");

        verify(loyaltyClient).release(bookingId, "Invalid payable amount returned by loyalty service");
    }

    @Test
    @DisplayName("processPayment: handles concurrent DataIntegrityViolationException by returning existing payment")
    void processPayment_shouldRecoverFromDataIntegrityViolationException_whenExistingFound() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("50.00"), PaymentMethod.ONLINE, ReferenceType.BOOKING, "dup-key");

        Payment existing = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .referenceType(ReferenceType.BOOKING)
                .referenceId(bookingId)
                .amount(new BigDecimal("50.00"))
                .method(PaymentMethod.ONLINE)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("dup-key")
                .receiptNumber("GC-DUP")
                .build();

        when(paymentRepository.findByIdempotencyKey("dup-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(paymentGateway.processPayment(new BigDecimal("50.00"))).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        PaymentResponse response = paymentService.processPayment(request, userId);

        assertThat(response.getId()).isEqualTo(existing.getId());
        assertThat(response.getReceiptNumber()).isEqualTo("GC-DUP");
    }

    @Test
    @DisplayName("processPayment: rethrows DataIntegrityViolationException if idempotencyKey lookup is empty")
    void processPayment_shouldRethrowDataIntegrityViolationException_whenExistingNotFound() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("50.00"), PaymentMethod.ONLINE, ReferenceType.BOOKING, "err-key");

        when(paymentRepository.findByIdempotencyKey("err-key")).thenReturn(Optional.empty());
        when(paymentGateway.processPayment(new BigDecimal("50.00"))).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException("database error"));

        assertThatThrownBy(() -> paymentService.processPayment(request, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("processCashPayment: returns existing payment if idempotencyKey is already used")
    void processCashPayment_shouldReturnExisting_whenIdempotencyKeyExists() {
        UUID adminId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment existing = Payment.builder()
                .id(UUID.randomUUID())
                .userId(customerId)
                .amount(new BigDecimal("100.00"))
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("existing-cash")
                .receiptNumber("GC-CASH-EXISTING")
                .build();

        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("100.00"), PaymentMethod.CASH, ReferenceType.BOOKING, "existing-cash");
        request.setCustomerUserId(customerId);

        when(paymentRepository.findByIdempotencyKey("existing-cash")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.processCashPayment(request, adminId);

        assertThat(response.getId()).isEqualTo(existing.getId());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(loyaltyClient);
    }

    @Test
    @DisplayName("processCashPayment: throws IllegalArgumentException when amount is zero or negative")
    void processCashPayment_shouldThrowException_whenAmountIsZeroOrNegative() {
        UUID adminId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(UUID.randomUUID(), BigDecimal.ZERO, PaymentMethod.CASH, ReferenceType.BOOKING, "cash-zero");
        request.setCustomerUserId(customerId);

        when(paymentRepository.findByIdempotencyKey("cash-zero")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processCashPayment(request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment amount must be greater than zero");
    }

    @Test
    @DisplayName("processCashPayment: allows tournament cash payment without customerUserId")
    void processCashPayment_shouldAllowTournamentPayment_withoutCustomerUserId() {
        UUID adminId = UUID.randomUUID();
        UUID tournamentId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(tournamentId, new BigDecimal("25.00"), PaymentMethod.CASH, ReferenceType.TOURNAMENT_ENTRY, "tour-cash");

        when(paymentRepository.findByIdempotencyKey("tour-cash")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PaymentResponse response = paymentService.processCashPayment(request, adminId);

        assertThat(response.getUserId()).isEqualTo(adminId);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verifyNoInteractions(loyaltyClient);
    }

    @Test
    @DisplayName("processCashPayment: recovers from DataIntegrityViolationException when existing found")
    void processCashPayment_shouldRecoverFromDataIntegrityViolationException_whenExistingFound() {
        UUID adminId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = paymentRequest(bookingId, new BigDecimal("40.00"), PaymentMethod.CASH, ReferenceType.BOOKING, "cash-dup");
        request.setCustomerUserId(customerId);

        Payment existing = Payment.builder()
                .id(UUID.randomUUID())
                .userId(customerId)
                .amount(new BigDecimal("40.00"))
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("cash-dup")
                .receiptNumber("GC-CASH-DUP")
                .build();

        when(paymentRepository.findByIdempotencyKey("cash-dup"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        PaymentResponse response = paymentService.processCashPayment(request, adminId);

        assertThat(response.getId()).isEqualTo(existing.getId());
    }

    @Test
    @DisplayName("getPaymentReceipt: throws IllegalArgumentException when payment does not exist")
    void getPaymentReceipt_shouldThrowException_whenPaymentNotFound() {
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentReceipt(paymentId, userId, "CUSTOMER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment not found");
    }

    @Test
    @DisplayName("getPaymentReceipt: returns receipt when role is ADMIN")
    void getPaymentReceipt_shouldReturnReceipt_whenRoleIsAdmin() {
        UUID paymentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .id(paymentId)
                .userId(customerId)
                .amount(new BigDecimal("50.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCESS)
                .receiptNumber("GC-REC-ADMIN")
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentReceipt(paymentId, adminId, "ADMIN");

        assertThat(response.getId()).isEqualTo(paymentId);
        assertThat(response.getReceiptNumber()).isEqualTo("GC-REC-ADMIN");
    }

    @Test
    @DisplayName("getPaymentReceipt: returns receipt when role is CUSTOMER and user owns payment")
    void getPaymentReceipt_shouldReturnReceipt_whenRoleIsCustomerAndOwnsPayment() {
        UUID paymentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .id(paymentId)
                .userId(customerId)
                .amount(new BigDecimal("50.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCESS)
                .receiptNumber("GC-REC-CUSTOMER")
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentReceipt(paymentId, customerId, "CUSTOMER");

        assertThat(response.getId()).isEqualTo(paymentId);
        assertThat(response.getReceiptNumber()).isEqualTo("GC-REC-CUSTOMER");
    }

    @Test
    @DisplayName("getPaymentReceipt: throws IllegalArgumentException when role is CUSTOMER and user does not own payment")
    void getPaymentReceipt_shouldThrowException_whenRoleIsCustomerAndDoesNotOwnPayment() {
        UUID paymentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .id(paymentId)
                .userId(customerId)
                .amount(new BigDecimal("50.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCESS)
                .receiptNumber("GC-REC-OTHER")
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getPaymentReceipt(paymentId, otherUserId, "CUSTOMER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("You are not authorized to access this payment");
    }

    @Test
    @DisplayName("getPaymentReceipt: throws IllegalArgumentException when role is unauthorized")
    void getPaymentReceipt_shouldThrowException_whenRoleIsUnauthorized() {
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .id(paymentId)
                .userId(userId)
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getPaymentReceipt(paymentId, userId, "UNKNOWN_ROLE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("You are not authorized to access this payment");
    }

    @Test
    @DisplayName("getMyPayments: returns list of user payments")
    void getMyPayments_shouldReturnListOfPaymentsForUser() {
        UUID userId = UUID.randomUUID();
        Payment p1 = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(new BigDecimal("20.00"))
                .status(PaymentStatus.SUCCESS)
                .build();
        Payment p2 = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(new BigDecimal("35.00"))
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentRepository.findByUserId(userId)).thenReturn(List.of(p1, p2));

        List<PaymentResponse> results = paymentService.getMyPayments(userId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(p1.getId());
        assertThat(results.get(1).getId()).isEqualTo(p2.getId());
    }

    @Test
    @DisplayName("getAdminPayments: queries repository with specification and returns paged results")
    void getAdminPayments_shouldQueryWithSpecificationAndReturnPagedResults() {
        UUID userId = UUID.randomUUID();
        Payment p = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(PaymentStatus.SUCCESS)
                .method(PaymentMethod.CARD)
                .referenceType(ReferenceType.BOOKING)
                .amount(new BigDecimal("50.00"))
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(paymentRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(p), pageable, 1));

        Page<PaymentResponse> result = paymentService.getAdminPayments(
                PaymentStatus.SUCCESS, PaymentMethod.CARD, ReferenceType.BOOKING,
                userId, Instant.now().minusSeconds(3600), Instant.now(), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(p.getId());
    }

    @Test
    @DisplayName("getPaymentStats: computes stats and revenue trend")
    void getPaymentStats_shouldCalculateAndReturnStats() {
        when(paymentRepository.sumRevenueInRange(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("500.00"))
                .thenReturn(new BigDecimal("3500.00"));
        when(paymentRepository.countByStatusInRange(eq(PaymentStatus.SUCCESS), any(Instant.class), any(Instant.class)))
                .thenReturn(42L);
        when(paymentRepository.countByStatusInRange(eq(PaymentStatus.FAILED), any(Instant.class), any(Instant.class)))
                .thenReturn(3L);

        Object[] dayRow = new Object[]{"2026-10-18", "500.00"};
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(dayRow);
        when(paymentRepository.dailyRevenueSince(any(Instant.class))).thenReturn(rows);

        var stats = paymentService.getPaymentStats(30);

        assertThat(stats.todayRevenue()).isEqualByComparingTo("500.00");
        assertThat(stats.monthRevenue()).isEqualByComparingTo("3500.00");
        assertThat(stats.totalPaymentsSuccess()).isEqualTo(42L);
        assertThat(stats.totalPaymentsFailed()).isEqualTo(3L);
        assertThat(stats.revenueTrend()).hasSize(1);
        assertThat(stats.revenueTrend().get(0).date()).isEqualTo("2026-10-18");
        assertThat(stats.revenueTrend().get(0).revenue()).isEqualByComparingTo("500.00");
    }

    private PaymentRequest paymentRequest(UUID referenceId, BigDecimal amount, PaymentMethod paymentMethod, ReferenceType referenceType, String idempotencyKey) {
        PaymentRequest request = new PaymentRequest();
        request.setReferenceType(referenceType);
        request.setReferenceId(referenceId);
        request.setAmount(amount);
        request.setMethod(paymentMethod);
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }
}
