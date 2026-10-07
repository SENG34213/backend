package com.gamingcastle.paymentservice.service;

import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.PaymentResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    PaymentResponse processPayment(PaymentRequest request, UUID userId);

    PaymentResponse processCashPayment(PaymentRequest request, UUID userId);

    PaymentResponse getPaymentReceipt(UUID paymentId, UUID userId, String role);

    List<PaymentResponse> getMyPayments(UUID userId);


}