package com.gamingcastle.tournamentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "brackets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bracket {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID tournamentId;

    @Column(nullable = false, updatable = false)
    private Instant generatedAt;

    @PrePersist
    void onCreate() {
        generatedAt = Instant.now();
    }
}
