package com.gamingcastle.paymentservice.controller;

import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.PaymentResponse;
import com.gamingcastle.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();

        PaymentResponse response =
                paymentService.processPayment(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/cash")
    public ResponseEntity<PaymentResponse> processCashPayment(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse response =
                paymentService.processCashPayment(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<PaymentResponse> getPaymentReceipt(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();

        String role = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .orElse("");

        PaymentResponse response =
                paymentService.getPaymentReceipt(id, userId, role);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<PaymentResponse>> getMyPayments(
            @RequestHeader("X-User-Id") UUID userId) {

        List<PaymentResponse> payments =
                paymentService.getMyPayments(userId);

        return ResponseEntity.ok(payments);
    }
}