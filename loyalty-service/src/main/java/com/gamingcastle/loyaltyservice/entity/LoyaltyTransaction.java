package com.gamingcastle.loyaltyservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit trail for every point movement, booking reservation and
 * cancellation adjustment.
 */
@Entity
@Table(name = "loyalty_transactions",
        indexes = {
                @Index(name = "idx_loyalty_tx_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_loyalty_tx_booking_type", columnList = "booking_id, type")
        })
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

    @Column
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoyaltyTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LoyaltyTransactionStatus status = LoyaltyTransactionStatus.CONFIRMED;

    @Column(nullable = false)
    private long points;

    @Column
    private Long balanceAfter;

    @Column(precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(length = 255)
    private String description;

    private UUID referencePaymentId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
