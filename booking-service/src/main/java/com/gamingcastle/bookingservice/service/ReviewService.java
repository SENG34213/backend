package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.ReviewRequest;
import com.gamingcastle.bookingservice.dto.ReviewResponse;

import java.util.List;
import java.util.UUID;

/**
 * SDS Class Diagram Table C.5:
 * Core business operations for session ratings and customer reviews.
 */
public interface ReviewService {

    ReviewResponse addReview(UUID bookingId, UUID userId, ReviewRequest request);

    ReviewResponse getReviewByBooking(UUID bookingId);

    List<ReviewResponse> getUserReviews(UUID userId);

    List<ReviewResponse> getStationReviews(UUID stationId);

    List<ReviewResponse> getRecentReviews();

    Double getAverageRatingForStation(UUID stationId);
}
