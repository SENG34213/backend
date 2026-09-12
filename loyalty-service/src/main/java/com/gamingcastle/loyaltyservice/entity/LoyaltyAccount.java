package com.gamingcastle.loyaltyservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** FR-22/FR-23: one row per user, tracking their current point balance. */
@Entity
@Table(name = "loyalty_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyAccount {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Builder.Default
    @Column(nullable = false)
    private long pointsBalance = 0;

    @Column(nullable = false)
    private Instant updatedAt;

    @PreUpdate
    @PrePersist
    void onSave() {
        updatedAt = Instant.now();
    }
}
