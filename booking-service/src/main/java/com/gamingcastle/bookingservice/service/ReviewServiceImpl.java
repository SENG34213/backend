package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.ReviewRequest;
import com.gamingcastle.bookingservice.dto.ReviewResponse;
import com.gamingcastle.bookingservice.entity.Booking;
import com.gamingcastle.bookingservice.entity.BookingStatus;
import com.gamingcastle.bookingservice.entity.Review;
import com.gamingcastle.bookingservice.repository.BookingRepository;
import com.gamingcastle.bookingservice.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewServiceImpl.class);

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;

    public ReviewServiceImpl(ReviewRepository reviewRepository, BookingRepository bookingRepository) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    @Transactional
    public ReviewResponse addReview(UUID bookingId, UUID userId, ReviewRequest request) {
        log.info("Submitting review for bookingId={} by userId={}", bookingId, userId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!booking.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only review your own bookings");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot review a cancelled or pending booking");
        }

        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A review has already been submitted for this booking");
        }

        Review review = Review.builder()
                .booking(booking)
                .userId(userId)
                .rating(request.rating())
                .comment(request.comment() != null ? request.comment().trim() : null)
                .build();

        Review saved = reviewRepository.save(review);
        log.info("Review saved successfully with ID={}", saved.getId());

        return mapToResponse(saved);
    }

    @Override
    public ReviewResponse getReviewByBooking(UUID bookingId) {
        return reviewRepository.findByBookingId(bookingId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No review found for this booking"));
    }

    @Override
    public List<ReviewResponse> getUserReviews(UUID userId) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<ReviewResponse> getStationReviews(UUID stationId) {
        return reviewRepository.findByBooking_Station_IdOrderByCreatedAtDesc(stationId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<ReviewResponse> getRecentReviews() {
        return reviewRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public Double getAverageRatingForStation(UUID stationId) {
        Double avg = reviewRepository.findAverageRatingByStationId(stationId);
        return avg != null ? Math.round(avg * 10.0) / 10.0 : 5.0;
    }

    private ReviewResponse mapToResponse(Review r) {
        Booking b = r.getBooking();
        String stationCode = b != null && b.getStation() != null ? b.getStation().getStationCode() : "Console Station";
        UUID stationId = b != null && b.getStation() != null ? b.getStation().getId() : null;
        String gameTitle = b != null && b.getGame() != null ? b.getGame().getTitle() : "PS5 Gaming Session";

        return new ReviewResponse(
                r.getId(),
                b != null ? b.getId() : null,
                r.getUserId(),
                stationId,
                stationCode,
                gameTitle,
                r.getRating(),
                r.getComment(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
