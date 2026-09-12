package com.gamingcastle.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * FR-14, FR-19–FR-21: a single payment/receipt record. Deliberately generic
 * over what it's paying for (referenceType + referenceId) so both Booking
 * and Tournament flows reuse this one service instead of each implementing
 * their own payment logic — this is the microservices-boundary test
 * described in the architecture doc §3.4.
 *
 * Idempotency: idempotencyKey prevents a retried request (e.g. after a
 * client-side network timeout) from creating a duplicate charge — see the
 * course's example commit "fix(payment): prevent double-charge on network
 * timeout" for the exact scenario this guards against.
 */
@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceType referenceType;

    @Column(nullable = false)
    private UUID referenceId; // bookingId or tournamentRegistrationId

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    private String receiptNumber;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
