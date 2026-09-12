package com.gamingcastle.loyaltyservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-22/FR-24: an immutable audit trail of every point movement — earned on
 * a successful payment (referencePaymentId links back to Payment Service),
 * or redeemed against a future booking. The current balance in
 * LoyaltyAccount is a derived/cached total; this table is the source of truth.
 */
@Entity
@Table(name = "loyalty_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyTransaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoyaltyTransactionType type;

    @Column(nullable = false)
    private long points;

    private UUID referencePaymentId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
