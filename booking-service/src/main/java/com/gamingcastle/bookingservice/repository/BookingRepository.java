package com.gamingcastle.bookingservice.repository;

import com.gamingcastle.bookingservice.entity.Booking;
import com.gamingcastle.bookingservice.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    List<Booking> findByUserId(UUID userId);

    Page<Booking> findAll(Pageable pageable);

    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    @Query("""
        SELECT b FROM Booking b
        WHERE b.station.id = :stationId
        AND b.status IN ('PENDING', 'CONFIRMED')
        AND b.startTime < :endTime
        AND b.endTime > :startTime
        """)
    List<Booking> findOverlapping(UUID stationId, Instant startTime, Instant endTime);

    // ── G11: Dashboard stats ──────────────────────────────────────────────────

    long countByCreatedAtBetween(Instant from, Instant to);

    long countByStatusAndCreatedAtBetween(BookingStatus status, Instant from, Instant to);

    long countByStatus(BookingStatus status);

    @Query("""
        SELECT COUNT(b)
        FROM Booking b
        WHERE b.status = com.gamingcastle.bookingservice.entity.BookingStatus.CONFIRMED
          AND b.startTime <= :now
          AND b.endTime   >  :now
        """)
    long countActiveSessionsAt(@Param("now") Instant now);
}
