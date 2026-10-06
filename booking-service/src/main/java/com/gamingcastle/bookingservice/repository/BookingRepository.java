package com.gamingcastle.bookingservice.repository;

import com.gamingcastle.bookingservice.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.gamingcastle.bookingservice.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByUserId(UUID userId);

    /** Admin: all bookings with optional status filter, paginated. */
    Page<Booking> findAll(Pageable pageable);

    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

    Page<Booking> findByUserId(UUID userId, Pageable pageable);

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
