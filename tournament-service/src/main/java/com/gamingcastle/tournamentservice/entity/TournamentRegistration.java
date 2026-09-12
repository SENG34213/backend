package com.gamingcastle.tournamentservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** FR-16: a customer's entry into a tournament, gated on entry-fee payment. */
@Entity
@Table(name = "tournament_registrations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tournament_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentRegistration {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationStatus status;

    // set once Payment Service confirms the entry-fee charge
    private UUID paymentId;

    @Column(nullable = false, updatable = false)
    private Instant registeredAt;

    @PrePersist
    void onCreate() {
        registeredAt = Instant.now();
    }
}
