package com.gamingcastle.paymentservice.controller;

import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.PaymentResponse;
import com.gamingcastle.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<PaymentResponse> processInternalPayment(
            @Valid @RequestBody PaymentRequest request) {

        UUID userId = request.getCustomerUserId();
        if (userId == null) {
            throw new IllegalArgumentException("customerUserId is required for internal payment processing");
        }

        PaymentResponse response = paymentService.processPayment(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
