package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.ReviewRequest;
import com.gamingcastle.bookingservice.dto.ReviewResponse;
import com.gamingcastle.bookingservice.entity.Booking;
import com.gamingcastle.bookingservice.entity.BookingStatus;
import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.entity.GameTitle;
import com.gamingcastle.bookingservice.entity.Review;
import com.gamingcastle.bookingservice.repository.BookingRepository;
import com.gamingcastle.bookingservice.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private UUID userId;
    private UUID bookingId;
    private Booking completedBooking;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        GameStation station = GameStation.builder()
                .id(UUID.randomUUID())
                .stationCode("PS5-01")
                .type(com.gamingcastle.bookingservice.entity.StationType.CONSOLE)
                .hourlyRate(java.math.BigDecimal.valueOf(500))
                .build();

        GameTitle game = GameTitle.builder()
                .id(UUID.randomUUID())
                .title("EA Sports FC 24")
                .build();

        completedBooking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .status(BookingStatus.CONFIRMED)
                .startTime(Instant.now().minusSeconds(7200))
                .endTime(Instant.now().minusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("addReview - successful review creation for confirmed booking")
    void addReview_Success() {
        ReviewRequest request = new ReviewRequest(5, "Amazing experience with PS5 DualSense!");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking));
        when(reviewRepository.existsByBookingId(bookingId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });

        ReviewResponse response = reviewService.addReview(bookingId, userId, request);

        assertThat(response).isNotNull();
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Amazing experience with PS5 DualSense!");
        assertThat(response.stationCode()).isEqualTo("PS5-01");
        assertThat(response.gameTitle()).isEqualTo("EA Sports FC 24");
    }

    @Test
    @DisplayName("addReview - throws 403 Forbidden when user is not booking owner")
    void addReview_ForbiddenUser() {
        UUID otherUser = UUID.randomUUID();
        ReviewRequest request = new ReviewRequest(4, "Nice!");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking));

        assertThatThrownBy(() -> reviewService.addReview(bookingId, otherUser, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("You can only review your own bookings");
    }

    @Test
    @DisplayName("addReview - throws 409 Conflict when review already exists")
    void addReview_DuplicateReview() {
        ReviewRequest request = new ReviewRequest(4, "Nice!");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking));
        when(reviewRepository.existsByBookingId(bookingId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.addReview(bookingId, userId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("A review has already been submitted");
    }
}
