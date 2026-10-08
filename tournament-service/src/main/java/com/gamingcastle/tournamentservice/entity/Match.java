package com.gamingcastle.tournamentservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "bracket_id", nullable = false)
    private UUID bracketId;

    @Column(nullable = false)
    private int round;

    @Column(name = "participant1_id", nullable = false)
    private UUID participant1Id;

    @Column(name = "participant2_id")
    private UUID participant2Id;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    @Column(name = "scheduled_time")
    private Instant scheduledTime;
}