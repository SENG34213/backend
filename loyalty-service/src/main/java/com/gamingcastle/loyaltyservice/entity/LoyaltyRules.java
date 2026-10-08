package com.gamingcastle.loyaltyservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "loyalty_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyRules extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal amountPerPoint = new BigDecimal("100.00");

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal pointValueLkr = new BigDecimal("0.50");

    @Column(nullable = false)
    @Builder.Default
    private long minRedeemPoints = 100;

    @Column(nullable = false)
    @Builder.Default
    private long redeemStep = 10;

    @Column(nullable = false)
    @Builder.Default
    private long maxDiscountPercent = 50;

    @Column(nullable = false)
    @Builder.Default
    private long reservationTimeoutMinutes = 15;

    @Column(nullable = false)
    @Builder.Default
    private long silverThreshold = 1000;

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal silverMultiplier = new BigDecimal("1.25");

    @Column(nullable = false)
    @Builder.Default
    private long goldThreshold = 3000;

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal goldMultiplier = new BigDecimal("1.50");

    @Column(length = 100)
    private String updatedBy;
}
