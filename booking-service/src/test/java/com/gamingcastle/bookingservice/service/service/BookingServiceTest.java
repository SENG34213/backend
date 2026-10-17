package com.gamingcastle.bookingservice.service.service;

import com.gamingcastle.bookingservice.client.LoyaltyClient;
import com.gamingcastle.bookingservice.client.NotificationClient;
import com.gamingcastle.bookingservice.client.PaymentClient;
import com.gamingcastle.bookingservice.client.UserClient;
import com.gamingcastle.bookingservice.dto.BookingRequest;
import com.gamingcastle.bookingservice.dto.BookingResponse;
import com.gamingcastle.bookingservice.entity.Booking;
import com.gamingcastle.bookingservice.entity.BookingSource;
import com.gamingcastle.bookingservice.entity.BookingStatus;
import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.entity.GameTitle;
import com.gamingcastle.bookingservice.entity.StationType;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.repository.BookingRepository;
import com.gamingcastle.bookingservice.repository.GameRepository;
import com.gamingcastle.bookingservice.repository.GameStationRepository;
import com.gamingcastle.bookingservice.service.BookingService;
import com.gamingcastle.bookingservice.service.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private GameStationRepository gameStationRepository;
    @Mock private GameRepository gameRepository;
    @Mock private PaymentClient paymentClient;
    @Mock private NotificationClient notificationClient;
    @Mock private LoyaltyClient loyaltyClient;
    @Mock private UserClient userClient;

    private BookingService bookingService;

    private GameStation station;
    private GameTitle game;
    private UUID gameId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        // NOTE: GameRepository is now the 3rd constructor argument
        bookingService = new BookingServiceImpl(
                bookingRepository, gameStationRepository, gameRepository,
                loyaltyClient, paymentClient, notificationClient, userClient);

        station = GameStation.builder()
                .id(UUID.randomUUID())
                .stationCode("PC-01")
                .type(StationType.PC)
                .hourlyRate(BigDecimal.valueOf(300))
                .active(true)
                .build();

        gameId = UUID.randomUUID();
        game = GameTitle.builder()
                .id(gameId)
                .title("Tekken 8")
                .genre("FIGHTING")
                .platforms("PC")
                .releaseYear(2024)
                .ageRating("PEGI_16")
                .active(true)
                .build();

        userId = UUID.randomUUID();
    }

    /**
     * Stubs the "happy path" for game validation: the game exists, is active,
     * and is installed on the given station. Call this ONLY in tests that get
     * past the station checks (Mockito strict stubs fail on unused stubs).
     */
    private void stubGameOnStation(GameStation targetStation) {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(gameStationRepository.existsByIdAndGames_Id(targetStation.getId(), gameId)).thenReturn(true);
    }

    // ── createBooking: Basic & Validation ────────────────────────────────────

    @Test
    @DisplayName("createBooking: succeeds given free slot for walk-in booking")
    void createBooking_shouldSucceed_givenFreeSlot() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(UUID.randomUUID());
            b.setCreatedAt(Instant.now());
            return b;
        });

        BookingResponse response = bookingService.createBooking(userId, request, BookingSource.WALK_IN);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.source()).isEqualTo(BookingSource.WALK_IN);
        assertThat(response.stationCode()).isEqualTo("PC-01");
        verify(bookingRepository, atLeastOnce()).save(any(Booking.class));
    }

    @Test
    @DisplayName("createBooking: saves the booking with the selected game")
    void createBooking_shouldSaveBookingWithSelectedGame() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(UUID.randomUUID());
            b.setCreatedAt(Instant.now());
            return b;
        });

        bookingService.createBooking(userId, request, BookingSource.WALK_IN);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getGame()).isSameAs(game);
        assertThat(captor.getAllValues().get(0).getGame().getId()).isEqualTo(gameId);
    }

    @Test
    @DisplayName("createBooking: rejects overlapping booking")
    void createBooking_shouldReject_givenOverlappingConfirmedBooking() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        Booking existing = Booking.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .station(station)
                .startTime(start.minus(30, ChronoUnit.MINUTES))
                .endTime(start.plus(30, ChronoUnit.MINUTES))
                .status(BookingStatus.CONFIRMED)
                .source(BookingSource.ONLINE)
                .build();

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station); // game validation happens BEFORE the overlap check
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .hasMessageContaining("already booked");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBooking: succeeds given adjacent slot")
    void createBooking_shouldSucceed_givenAdjacentSlot() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(userId, request, BookingSource.WALK_IN);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("createBooking: ignores cancelled bookings")
    void createBooking_shouldIgnoreCancelledBookings_becauseRepositoryQueryFiltersThemOut() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(userId, request, BookingSource.WALK_IN);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        verify(bookingRepository).findOverlapping(station.getId(), start, end);
    }

    @Test
    @DisplayName("createBooking: throws 404 when station does not exist")
    void createBooking_shouldThrow404_givenNonExistentStation() {
        UUID unknownStationId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(unknownStationId, gameId, start, end);

        when(gameStationRepository.findById(unknownStationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .hasMessageContaining("No game station exists");
        verify(bookingRepository, never()).findOverlapping(any(), any(), any());
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(gameRepository);
    }

    @Test
    @DisplayName("createBooking: throws 409 CONFLICT when station is inactive")
    void createBooking_shouldThrowConflict_whenStationInactive() {
        station.setActive(false);
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("STATION_INACTIVE");
                });

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBooking: rejects end time before or equal to start time")
    void createBooking_shouldReject_givenEndTimeBeforeStartTime() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.minus(1, ChronoUnit.HOURS);

        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .hasMessageContaining("endTime must be after startTime");
        verifyNoInteractions(gameStationRepository);
        verifyNoInteractions(gameRepository);
    }

    // ── createBooking: Game validation ───────────────────────────────────────

    @Test
    @DisplayName("createBooking: throws 400 GAME_ID_REQUIRED when gameId is missing")
    void createBooking_shouldThrowBadRequest_whenGameIdMissing() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), null, start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorCode()).isEqualTo("GAME_ID_REQUIRED");
                });

        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(paymentClient);
    }

    @Test
    @DisplayName("createBooking: throws 404 GAME_NOT_FOUND when game does not exist")
    void createBooking_shouldThrowNotFound_whenGameDoesNotExist() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        when(gameRepository.findById(gameId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getErrorCode()).isEqualTo("GAME_NOT_FOUND");
                });

        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(paymentClient);
    }

    @Test
    @DisplayName("createBooking: throws 409 GAME_INACTIVE when game is inactive")
    void createBooking_shouldThrowConflict_whenGameInactive() {
        game.setActive(false);
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("GAME_INACTIVE");
                });

        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(paymentClient);
    }

    @Test
    @DisplayName("createBooking: throws 400 GAME_NOT_ON_STATION when game is not installed on the station")
    void createBooking_shouldThrowBadRequest_whenGameNotOnStation() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(gameStationRepository.existsByIdAndGames_Id(station.getId(), gameId)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorCode()).isEqualTo("GAME_NOT_ON_STATION");
                    assertThat(be.getMessage()).contains("PC-01");
                });

        // nothing should be created / charged when the game isn't on the station
        verify(bookingRepository, never()).findOverlapping(any(), any(), any());
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(paymentClient);
    }

    // ── createBooking: ONLINE Payment & Notifications ────────────────────────

    @Test
    @DisplayName("createBooking: ONLINE happy path charges fee, confirms booking, and dispatches confirmation email")
    void createBooking_shouldSucceedOnline_whenPaymentSucceeds() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS); // 2 hours * 300 = 600.00
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end, "CARD", 50);

        UUID paymentId = UUID.randomUUID();
        PaymentClient.PaymentResult paymentResult = new PaymentClient.PaymentResult(
                paymentId, BigDecimal.valueOf(600), BigDecimal.valueOf(50), BigDecimal.valueOf(550));

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            if (b.getId() == null) {
                b.setId(UUID.randomUUID());
            }
            return b;
        });
        when(paymentClient.chargeBookingFee(any(BigDecimal.class), eq(userId), any(), eq("CARD"), eq(50)))
                .thenReturn(paymentResult);

        UserClient.UserSummaryDto userDto = new UserClient.UserSummaryDto(
                userId, "player", "player@example.com", "Test Player", "CUSTOMER");
        when(userClient.getUserById(userId)).thenReturn(Optional.of(userDto));

        BookingResponse response = bookingService.createBooking(userId, request, BookingSource.ONLINE);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.paymentId()).isEqualTo(paymentId);
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(response.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(response.payableAmount()).isEqualByComparingTo(BigDecimal.valueOf(550));

        verify(notificationClient).sendBookingConfirmation(any(NotificationClient.BookingEmailRequest.class));
    }

    @Test
    @DisplayName("createBooking: ONLINE booking skips email if user has no email")
    void createBooking_shouldSkipEmail_whenUserEmailBlankOrMissing() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        UUID paymentId = UUID.randomUUID();
        PaymentClient.PaymentResult paymentResult = new PaymentClient.PaymentResult(
                paymentId, BigDecimal.valueOf(300), BigDecimal.ZERO, BigDecimal.valueOf(300));

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentClient.chargeBookingFee(any(), any(), any(), any(), any())).thenReturn(paymentResult);

        // Scenario: userClient returns empty
        when(userClient.getUserById(userId)).thenReturn(Optional.empty());

        BookingResponse response = bookingService.createBooking(userId, request, BookingSource.ONLINE);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        verifyNoInteractions(notificationClient);
    }

    @Test
    @DisplayName("createBooking: ONLINE booking succeeds even if notification client throws exception")
    void createBooking_shouldNotRollback_whenNotificationFails() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        UUID paymentId = UUID.randomUUID();
        PaymentClient.PaymentResult paymentResult = new PaymentClient.PaymentResult(
                paymentId, BigDecimal.valueOf(300), BigDecimal.ZERO, BigDecimal.valueOf(300));

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            if (b.getId() == null) {
                b.setId(UUID.randomUUID());
            }
            return b;
        });
        when(paymentClient.chargeBookingFee(any(), any(), any(), any(), any())).thenReturn(paymentResult);

        when(userClient.getUserById(userId)).thenReturn(Optional.of(
                new UserClient.UserSummaryDto(userId, "player", "player@example.com", "Test", "CUSTOMER")));
        doThrow(new RuntimeException("Notification service unreachable"))
                .when(notificationClient).sendBookingConfirmation(any());

        BookingResponse response = bookingService.createBooking(userId, request, BookingSource.ONLINE);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("createBooking: ONLINE marks booking CANCELLED and rethrows when PaymentClient throws BookingException")
    void createBooking_shouldMarkCancelled_whenPaymentClientThrowsBookingException() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(paymentClient.chargeBookingFee(any(), any(), any(), any(), any()))
                .thenThrow(new BookingException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED", "Card declined"));

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .hasMessageContaining("Card declined");

        // Verify status was updated to CANCELLED and saved
        verify(bookingRepository, atLeast(2)).save(argThat(b -> b.getStatus() == BookingStatus.CANCELLED));
    }

    @Test
    @DisplayName("createBooking: ONLINE marks booking CANCELLED when PaymentClient returns null result")
    void createBooking_shouldMarkCancelled_whenPaymentResultIsNull() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentClient.chargeBookingFee(any(), any(), any(), any(), any())).thenReturn(null);

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorCode()).isEqualTo("PAYMENT_FAILED");
                });

        verify(bookingRepository, atLeast(2)).save(argThat(b -> b.getStatus() == BookingStatus.CANCELLED));
    }

    // ── getBookingsForUser ────────────────────────────────────────────────────

    @Test
    @DisplayName("getBookingsForUser: returns list of user bookings")
    void getBookingsForUser_shouldReturnList() {
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .source(BookingSource.ONLINE)
                .build();

        when(bookingRepository.findByUserId(userId)).thenReturn(List.of(booking));

        List<BookingResponse> responses = bookingService.getBookingsForUser(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(booking.getId());
    }

    // ── cancelBooking ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelBooking: customer cancels own booking and triggers loyalty reversal")
    void cancelBooking_shouldReverseLoyaltyAfterCancellation() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .source(BookingSource.ONLINE)
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.cancelBooking(bookingId, userId, false);

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        verify(loyaltyClient).reverse(bookingId, "Booking cancelled");
    }

    @Test
    @DisplayName("cancelBooking: admin can cancel another user's booking")
    void cancelBooking_shouldSucceedForAdmin_onOtherUserBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(otherUserId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .source(BookingSource.ONLINE)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.cancelBooking(bookingId, userId, true);

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        verify(loyaltyClient).reverse(bookingId, "Booking cancelled");
    }

    @Test
    @DisplayName("cancelBooking: throws 404 when booking does not exist")
    void cancelBooking_shouldThrowNotFound_whenBookingDoesNotExist() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, userId, false))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("cancelBooking: throws 403 when non-admin tries to cancel someone else's booking")
    void cancelBooking_shouldThrowForbidden_whenNotOwnBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(otherUserId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, userId, false))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(be.getErrorCode()).isEqualTo("NOT_YOUR_BOOKING");
                });
    }

    @Test
    @DisplayName("cancelBooking: throws 409 CONFLICT when booking is already cancelled")
    void cancelBooking_shouldThrowConflict_whenAlreadyCancelled() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CANCELLED)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, userId, false))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("ALREADY_CANCELLED");
                });
    }

    @Test
    @DisplayName("cancelBooking: throws 400 BAD_REQUEST when within 2 hours of scheduled start")
    void cancelBooking_shouldThrowBadRequest_whenTooLate() {
        UUID bookingId = UUID.randomUUID();
        // start time is in 1 hour (less than the 2-hour window)
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, userId, false))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorCode()).isEqualTo("TOO_LATE");
                });
    }

    // ── rescheduleBooking ─────────────────────────────────────────────────────

    @Test
    @DisplayName("rescheduleBooking: succeeds for confirmed booking with valid new slot")
    void rescheduleBooking_shouldSucceed_whenSlotAvailable() {
        UUID bookingId = UUID.randomUUID();
        Instant oldStart = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant oldEnd = oldStart.plus(1, ChronoUnit.HOURS);
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(oldStart)
                .endTime(oldEnd)
                .status(BookingStatus.CONFIRMED)
                .build();

        Instant newStart = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(2, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, newStart, newEnd);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), newStart, newEnd)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.rescheduleBooking(bookingId, userId, false, request);

        assertThat(response.startTime()).isEqualTo(newStart);
        assertThat(response.endTime()).isEqualTo(newEnd);
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("rescheduleBooking: falls back to the booking's existing game when request has no gameId")
    void rescheduleBooking_shouldUseExistingGame_whenRequestGameIdIsNull() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game) // existing game on the booking
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .build();

        Instant newStart = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), null, newStart, newEnd);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station); // validated using booking.getGame().getId()
        when(bookingRepository.findOverlapping(station.getId(), newStart, newEnd)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookingService.rescheduleBooking(bookingId, userId, false, request);

        assertThat(booking.getGame()).isSameAs(game);
        verify(gameRepository).findById(gameId);
    }

    @Test
    @DisplayName("rescheduleBooking: throws 400 GAME_NOT_ON_STATION when game is not on the new station")
    void rescheduleBooking_shouldThrowBadRequest_whenGameNotOnTargetStation() {
        UUID bookingId = UUID.randomUUID();
        GameStation otherStation = GameStation.builder()
                .id(UUID.randomUUID())
                .stationCode("CONSOLE-01")
                .type(StationType.CONSOLE)
                .hourlyRate(BigDecimal.valueOf(400))
                .active(true)
                .build();

        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .build();

        Instant newStart = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(otherStation.getId(), gameId, newStart, newEnd);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(gameStationRepository.findById(otherStation.getId())).thenReturn(Optional.of(otherStation));
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(gameStationRepository.existsByIdAndGames_Id(otherStation.getId(), gameId)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.rescheduleBooking(bookingId, userId, false, request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorCode()).isEqualTo("GAME_NOT_ON_STATION");
                });

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("rescheduleBooking: admin can reschedule another user's booking")
    void rescheduleBooking_shouldSucceedForAdmin_onOtherUserBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Instant oldStart = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant oldEnd = oldStart.plus(1, ChronoUnit.HOURS);
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(otherUserId)
                .station(station)
                .game(game)
                .startTime(oldStart)
                .endTime(oldEnd)
                .status(BookingStatus.CONFIRMED)
                .build();

        Instant newStart = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(2, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, newStart, newEnd);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), newStart, newEnd)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.rescheduleBooking(bookingId, userId, true, request);

        assertThat(response.startTime()).isEqualTo(newStart);
    }

    @Test
    @DisplayName("rescheduleBooking: throws 404 when booking does not exist")
    void rescheduleBooking_shouldThrowNotFound_whenBookingDoesNotExist() {
        UUID bookingId = UUID.randomUUID();
        BookingRequest request = new BookingRequest(station.getId(), gameId,
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.rescheduleBooking(bookingId, userId, false, request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("rescheduleBooking: throws 403 when non-admin reschedules another user's booking")
    void rescheduleBooking_shouldThrowForbidden_whenNotOwnBooking() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(UUID.randomUUID())
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .build();

        BookingRequest request = new BookingRequest(station.getId(), gameId,
                Instant.now().plus(3, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.rescheduleBooking(bookingId, userId, false, request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("rescheduleBooking: throws 409 CONFLICT when booking status is not CONFIRMED")
    void rescheduleBooking_shouldThrowConflict_whenNotConfirmed() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.PENDING)
                .build();

        BookingRequest request = new BookingRequest(station.getId(), gameId,
                Instant.now().plus(3, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.rescheduleBooking(bookingId, userId, false, request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("INVALID_STATUS");
                });
    }

    @Test
    @DisplayName("rescheduleBooking: throws 400 BAD_REQUEST when within 2 hours of current start time")
    void rescheduleBooking_shouldThrowBadRequest_whenTooLate() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .build();

        BookingRequest request = new BookingRequest(station.getId(), gameId,
                Instant.now().plus(3, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.rescheduleBooking(bookingId, userId, false, request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorCode()).isEqualTo("TOO_LATE");
                });
    }

    @Test
    @DisplayName("rescheduleBooking: throws 400 BAD_REQUEST when new endTime is before or equal to startTime")
    void rescheduleBooking_shouldThrowBadRequest_whenEndTimeBeforeStartTime() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .build();

        Instant newStart = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant newEnd = newStart.minus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, newStart, newEnd);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.rescheduleBooking(bookingId, userId, false, request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorCode()).isEqualTo("INVALID_TIME_RANGE");
                });
    }

    @Test
    @DisplayName("rescheduleBooking: throws 404 when target station does not exist")
    void rescheduleBooking_shouldThrowNotFound_whenTargetStationNotFound() {
        UUID bookingId = UUID.randomUUID();
        UUID targetStationId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .build();

        Instant newStart = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(targetStationId, gameId, newStart, newEnd);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(gameStationRepository.findById(targetStationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.rescheduleBooking(bookingId, userId, false, request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("rescheduleBooking: throws 409 CONFLICT when target station is inactive")
    void rescheduleBooking_shouldThrowConflict_whenTargetStationInactive() {
        UUID bookingId = UUID.randomUUID();
        GameStation inactiveStation = GameStation.builder()
                .id(UUID.randomUUID())
                .stationCode("PC-OFF")
                .type(StationType.PC)
                .hourlyRate(BigDecimal.valueOf(300))
                .active(false)
                .build();

        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .build();

        Instant newStart = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(inactiveStation.getId(), gameId, newStart, newEnd);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(gameStationRepository.findById(inactiveStation.getId())).thenReturn(Optional.of(inactiveStation));

        assertThatThrownBy(() -> bookingService.rescheduleBooking(bookingId, userId, false, request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("rescheduleBooking: throws 409 CONFLICT when overlapping with another booking")
    void rescheduleBooking_shouldThrowConflict_whenOverlappingAnotherBooking() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .build();

        Instant newStart = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, newStart, newEnd);

        Booking otherBooking = Booking.builder()
                .id(UUID.randomUUID()) // different ID
                .station(station)
                .startTime(newStart)
                .endTime(newEnd)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station); // game validation happens BEFORE the overlap check
        when(bookingRepository.findOverlapping(station.getId(), newStart, newEnd)).thenReturn(List.of(otherBooking));

        assertThatThrownBy(() -> bookingService.rescheduleBooking(bookingId, userId, false, request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("rescheduleBooking: succeeds without conflict when overlap is with self")
    void rescheduleBooking_shouldSucceed_whenOverlapIsSelf() {
        UUID bookingId = UUID.randomUUID();
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(start)
                .endTime(end)
                .status(BookingStatus.CONFIRMED)
                .build();

        // Reschedule slightly extending the slot on same station
        Instant newEnd = end.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), gameId, start, newEnd);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        stubGameOnStation(station);
        when(bookingRepository.findOverlapping(station.getId(), start, newEnd)).thenReturn(List.of(booking)); // only self in conflict
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.rescheduleBooking(bookingId, userId, false, request);

        assertThat(response.endTime()).isEqualTo(newEnd);
        verify(bookingRepository).save(booking);
    }

    // ── confirmBooking ────────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmBooking: rejects null paymentId")
    void confirmBooking_shouldRejectNullPaymentId() {
        UUID bookingId = UUID.randomUUID();

        assertThatThrownBy(() -> bookingService.confirmBooking(bookingId, null))
                .isInstanceOf(BookingException.class)
                .hasMessageContaining("paymentId is required");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmBooking: throws 404 when booking does not exist")
    void confirmBooking_shouldThrowNotFound_whenBookingDoesNotExist() {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.confirmBooking(bookingId, paymentId))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("confirmBooking: throws 409 CONFLICT when booking status is not PENDING")
    void confirmBooking_shouldThrowConflict_whenStatusNotPending() {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED) // already confirmed
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirmBooking(bookingId, paymentId))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("BOOKING_NOT_PENDING");
                });
    }

    @Test
    @DisplayName("confirmBooking: successfully confirms pending booking and sets paymentId")
    void confirmBooking_shouldSucceed_whenPending() {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.PENDING)
                .source(BookingSource.WALK_IN)
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.confirmBooking(bookingId, paymentId);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.paymentId()).isEqualTo(paymentId);
        verify(bookingRepository).save(booking);
    }

    // ── getAllBookings ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllBookings: delegates to findByStatus when status provided")
    void getAllBookings_shouldFilterByStatus_whenStatusNotNull() {
        Pageable pageable = PageRequest.of(0, 10);
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(Instant.now())
                .endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .status(BookingStatus.CONFIRMED)
                .build();
        Page<Booking> page = new PageImpl<>(List.of(booking));

        when(bookingRepository.findByStatus(BookingStatus.CONFIRMED, pageable)).thenReturn(page);

        Page<BookingResponse> responses = bookingService.getAllBookings(BookingStatus.CONFIRMED, pageable);

        assertThat(responses.getContent()).hasSize(1);
        verify(bookingRepository).findByStatus(BookingStatus.CONFIRMED, pageable);
        verify(bookingRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("getAllBookings: delegates to findAll when status is null")
    void getAllBookings_shouldFindAll_whenStatusNull() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Booking> page = new PageImpl<>(Collections.emptyList());

        when(bookingRepository.findAll(pageable)).thenReturn(page);

        Page<BookingResponse> responses = bookingService.getAllBookings(null, pageable);

        assertThat(responses.getContent()).isEmpty();
        verify(bookingRepository).findAll(pageable);
        verify(bookingRepository, never()).findByStatus(any(), any());
    }
}