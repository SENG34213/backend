package com.gamingcastle.paymentservice.repository;

import com.gamingcastle.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByUserId(UUID userId);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
