package com.gamingcastle.loyaltyservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * One row per customer; the current points balance is cached here while the
 * transaction table remains the audit source of truth.
 */
@Entity
@Table(name = "loyalty_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyAccount extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Builder.Default
    @Column(nullable = false)
    private long pointsBalance = 0;

    @Builder.Default
    @Column(nullable = false)
    private long lifetimeEarned = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LoyaltyTier tier = LoyaltyTier.BRONZE;

    @Version
    @Builder.Default
    private Long version = 0L;
}
