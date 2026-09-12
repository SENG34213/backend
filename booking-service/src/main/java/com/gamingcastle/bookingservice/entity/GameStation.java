package com.gamingcastle.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A physical, bookable unit at the gaming centre (a PC, console booth, or
 * VR rig). Bookings reference a station by id; availability is derived by
 * checking for overlapping Booking rows against a given station + time range
 * (see Booking's unique constraint / overlap-check logic added in Sprint 6).
 */
@Entity
@Table(name = "game_stations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameStation {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String stationCode; // e.g. "PC-01", "VR-02"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StationType type;

    @Column(nullable = false)
    private BigDecimal hourlyRate;

    @Builder.Default
    private boolean active = true;
}
