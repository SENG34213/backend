package com.gamingcastle.bookingservice.repository;

import com.gamingcastle.bookingservice.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByBookingId(UUID bookingId);

    boolean existsByBookingId(UUID bookingId);

    List<Review> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Review> findByBooking_Station_IdOrderByCreatedAtDesc(UUID stationId);

    List<Review> findTop20ByOrderByCreatedAtDesc();

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.booking.station.id = :stationId")
    Double findAverageRatingByStationId(@Param("stationId") UUID stationId);
}
