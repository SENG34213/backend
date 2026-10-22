package com.gamingcastle.bookingservice.controller;

import com.gamingcastle.bookingservice.config.GatewayHeaderAuthFilter;
import com.gamingcastle.bookingservice.dto.ReviewRequest;
import com.gamingcastle.bookingservice.dto.ReviewResponse;
import com.gamingcastle.bookingservice.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "Reviews", description = "Customer rating and reviews for completed gaming sessions")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Submit a rating and review for a completed booking")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @PostMapping("/api/bookings/{id}/review")
    public ResponseEntity<ReviewResponse> addReview(
            @PathVariable("id") UUID bookingId,
            @RequestHeader(GatewayHeaderAuthFilter.USER_ID_HEADER) UUID userId,
            @Valid @RequestBody ReviewRequest request) {

        ReviewResponse response = reviewService.addReview(bookingId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get review for a specific booking")
    @GetMapping("/api/bookings/{id}/review")
    public ResponseEntity<ReviewResponse> getReviewByBooking(@PathVariable("id") UUID bookingId) {
        return ResponseEntity.ok(reviewService.getReviewByBooking(bookingId));
    }

    @Operation(summary = "Get reviews submitted by currently logged-in user")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @GetMapping({"/api/reviews/mine", "/api/bookings/reviews/mine"})
    public ResponseEntity<List<ReviewResponse>> getMyReviews(
            @RequestHeader(GatewayHeaderAuthFilter.USER_ID_HEADER) UUID userId) {
        return ResponseEntity.ok(reviewService.getUserReviews(userId));
    }

    @Operation(summary = "Get recent public reviews across gaming centre")
    @GetMapping({"/api/reviews/recent", "/api/bookings/reviews/recent"})
    public ResponseEntity<List<ReviewResponse>> getRecentReviews() {
        return ResponseEntity.ok(reviewService.getRecentReviews());
    }

    @Operation(summary = "Get all reviews for a specific station/console")
    @GetMapping({"/api/reviews/station/{stationId}", "/api/stations/{stationId}/reviews"})
    public ResponseEntity<List<ReviewResponse>> getStationReviews(@PathVariable UUID stationId) {
        return ResponseEntity.ok(reviewService.getStationReviews(stationId));
    }

    @Operation(summary = "Get average rating for a specific station/console")
    @GetMapping({"/api/reviews/station/{stationId}/rating", "/api/stations/{stationId}/rating"})
    public ResponseEntity<Map<String, Object>> getStationRating(@PathVariable UUID stationId) {
        Double avg = reviewService.getAverageRatingForStation(stationId);
        return ResponseEntity.ok(Map.of("stationId", stationId, "averageRating", avg));
    }
}
