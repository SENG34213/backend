package com.gamingcastle.paymentservice.controller;

import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.PaymentResponse;
import com.gamingcastle.paymentservice.entity.PaymentMethod;
import com.gamingcastle.paymentservice.entity.PaymentStatus;
import com.gamingcastle.paymentservice.entity.ReferenceType;
import com.gamingcastle.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalPaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private InternalPaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalPaymentController(paymentService);
    }

    @Test
    @DisplayName("processInternalPayment: delegates to service and returns 201 Created")
    void processInternalPayment_shouldReturnCreated_whenCustomerUserIdPresent() {
        UUID customerId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest();
        request.setCustomerUserId(customerId);
        request.setAmount(new BigDecimal("50.00"));
        request.setMethod(PaymentMethod.ONLINE);
        request.setReferenceType(ReferenceType.BOOKING);

        PaymentResponse mockResponse = PaymentResponse.builder()
                .id(UUID.randomUUID())
                .userId(customerId)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentService.processPayment(request, customerId)).thenReturn(mockResponse);

        ResponseEntity<PaymentResponse> response = controller.processInternalPayment(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(mockResponse);
        verify(paymentService).processPayment(request, customerId);
    }

    @Test
    @DisplayName("processInternalPayment: throws IllegalArgumentException when customerUserId is null")
    void processInternalPayment_shouldThrowException_whenCustomerUserIdIsNull() {
        PaymentRequest request = new PaymentRequest();
        request.setCustomerUserId(null);

        assertThatThrownBy(() -> controller.processInternalPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customerUserId is required for internal payment processing");
    }
}
