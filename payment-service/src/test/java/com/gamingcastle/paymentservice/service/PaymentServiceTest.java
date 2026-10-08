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
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
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
