package com.gamingcastle.bookingservice.repository;

import com.gamingcastle.bookingservice.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByUserId(UUID userId);

    // used by the availability check in Sprint 6 — finds bookings on a station
    // that overlap a candidate [startTime, endTime) window
    @Query("""
        SELECT b FROM Booking b
        WHERE b.station.id = :stationId
        AND b.status IN ('PENDING', 'CONFIRMED')
        AND b.startTime < :endTime
        AND b.endTime > :startTime
        """)
    List<Booking> findOverlapping(UUID stationId, Instant startTime, Instant endTime);
}
