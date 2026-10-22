package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.AdminBookingRow;
import com.gamingcastle.bookingservice.dto.AdminBookingStatsResponse;
import com.gamingcastle.bookingservice.dto.BookingRequest;
import com.gamingcastle.bookingservice.dto.BookingResponse;
import com.gamingcastle.bookingservice.client.LoyaltyClient;
import com.gamingcastle.bookingservice.entity.*;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.repository.BookingRepository;
import com.gamingcastle.bookingservice.repository.GameRepository;
import com.gamingcastle.bookingservice.repository.GameStationRepository;
import com.gamingcastle.bookingservice.client.NotificationClient;
import com.gamingcastle.bookingservice.client.PaymentClient;
import com.gamingcastle.bookingservice.client.UserClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

/**
 * FR-07–FR-13: slot booking, availability, cancellation.
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);
    private static final ZoneId SRI_LANKA_ZONE = ZoneId.of("Asia/Colombo");

    private final BookingRepository bookingRepository;
    private final GameStationRepository gameStationRepository;
    private final GameRepository gameRepository;
    private final LoyaltyClient loyaltyClient;
    private final PaymentClient paymentClient;
    private final NotificationClient notificationClient;
    private final UserClient userClient;
    private final SagaCompensationService sagaCompensationService;

    @Value("${booking.cancel-window-hours:2}")
    private int cancelWindowHours = 2;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            GameStationRepository gameStationRepository,
            GameRepository gameRepository,
            LoyaltyClient loyaltyClient,
            PaymentClient paymentClient,
            NotificationClient notificationClient,
            UserClient userClient) {
        this(bookingRepository, gameStationRepository, gameRepository, loyaltyClient,
                paymentClient, notificationClient, userClient, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BookingServiceImpl(
            BookingRepository bookingRepository,
            GameStationRepository gameStationRepository,
            GameRepository gameRepository,
            LoyaltyClient loyaltyClient,
            PaymentClient paymentClient,
            NotificationClient notificationClient,
            UserClient userClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            SagaCompensationService sagaCompensationService) {
        this.bookingRepository = bookingRepository;
        this.gameStationRepository = gameStationRepository;
        this.gameRepository = gameRepository;
        this.loyaltyClient = loyaltyClient;
        this.paymentClient = paymentClient;
        this.notificationClient = notificationClient;
        this.userClient = userClient;
        this.sagaCompensationService = sagaCompensationService;
    }

    private String formatSlotDate(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(SRI_LANKA_ZONE).format(instant);
    }

    private String formatSlotTime(Instant instant) {
        return DateTimeFormatter.ofPattern("HH:mm").withZone(SRI_LANKA_ZONE).format(instant);
    }

    private String safeBookingId(Booking booking) {
        return (booking != null && booking.getId() != null) ? booking.getId().toString() : "";
    }

    @Override
    @Transactional
    public BookingResponse createBooking(UUID userId, BookingRequest request, BookingSource source) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE",
                    "endTime must be after startTime");
        }

        GameStation station = gameStationRepository.findById(request.stationId())
                .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND",
                        "No game station exists with id " + request.stationId()));

        if (!station.isActive()) {
            throw new BookingException(HttpStatus.CONFLICT, "STATION_INACTIVE",
                    "Station " + station.getStationCode() + " is not currently active");
        }

        GameTitle game = validateGameOnStation(station, request.gameId());

        // AC2/AC3: reject overlapping PENDING/CONFIRMED bookings, but allow
        // adjacent slots and ignore CANCELLED bookings — both handled by the
        // existing findOverlapping() query, which already filters by status.
        List<Booking> conflicts = bookingRepository.findOverlapping(
                station.getId(), request.startTime(), request.endTime());
        if (!conflicts.isEmpty()) {
            throw new BookingException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE",
                    "Station " + station.getStationCode() + " is already booked for part of that time range");
        }

        Booking booking = Booking.builder()
                .userId(userId)
                .station(station)
                .game(game)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(BookingStatus.PENDING)
                .source(source)
                .build();

        booking = bookingRepository.save(booking);

        long minutes = Duration.between(request.startTime(), request.endTime()).toMinutes();
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal totalAmount = station.getHourlyRate().multiply(hours).setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal payableAmount = totalAmount;

        // FR-14: Charge booking fee = station.hourlyRate × duration in hours
        // On payment failure → booking is set to CANCELLED (no orphaned PENDING records).
        // Walk-in (cash) bookings skip card payment — admin handles cash directly.
        String slotDate = formatSlotDate(booking.getStartTime());
        String slotTime = formatSlotTime(booking.getStartTime());

        if (source == BookingSource.ONLINE) {
            PaymentClient.PaymentResult paymentResult;
            try {
                paymentResult = paymentClient.chargeBookingFee(
                        totalAmount,
                        userId,
                        booking.getId(),
                        request.paymentMethod(),
                        request.loyaltyPointsToRedeem()
                );
            } catch (BookingException e) {
                booking.setStatus(BookingStatus.CANCELLED);
                bookingRepository.save(booking);
                if (sagaCompensationService != null) {
                    sagaCompensationService.executeCompensation(booking.getId(), "LOYALTY_REVERSE", "Online payment failed: " + e.getMessage());
                }
                throw e;
            }

            // Null check
            if (paymentResult == null) {
                booking.setStatus(BookingStatus.CANCELLED);
                bookingRepository.save(booking);
                if (sagaCompensationService != null) {
                    sagaCompensationService.executeCompensation(booking.getId(), "LOYALTY_REVERSE", "Payment result was null");
                }
                throw new BookingException(HttpStatus.BAD_REQUEST, "PAYMENT_FAILED",
                        "Payment processing returned no result");
            }

            // Payment succeeded — confirm immediately
            booking.setPaymentId(paymentResult.paymentId());
            booking.setStatus(BookingStatus.CONFIRMED);
            booking = bookingRepository.save(booking);

            discountAmount = paymentResult.discountAmount();
            payableAmount = paymentResult.payableAmount();

            if (sagaCompensationService != null && booking.getId() != null) {
                sagaCompensationService.recordAction(booking.getId(), "BOOKING_CONFIRMED", "PaymentId=" + paymentResult.paymentId());
            }

            // Real-time socket broadcast for slot and notifications
            notificationClient.broadcastSlotEvent(station.getId(), slotDate, slotTime, "BOOKED");
            notificationClient.broadcastSocketNotification(new NotificationClient.SocketNotificationPayload(
                    "BOOKING_CREATED", "BOOKING", "New Booking Confirmed",
                    "Booking for station " + station.getStationCode() + " on " + slotDate + " at " + slotTime + " confirmed.",
                    safeBookingId(booking), "/customer/my-bookings", userId != null ? userId.toString() : null, true
            ));

            // Send confirmation email after successful payment
            sendBookingConfirmedEmail(booking, payableAmount);
        } else {
            // WALK_IN: admin records cash manually via POST /api/payments/cash later
            // Booking remains PENDING until admin calls PATCH /{id}/confirm with paymentId
            booking = bookingRepository.save(booking);

            if (sagaCompensationService != null && booking.getId() != null) {
                sagaCompensationService.recordAction(booking.getId(), "WALK_IN_CREATED", "Station=" + station.getStationCode());
            }

            // Live slot broadcast so customer browsing screens immediately see station as occupied
            notificationClient.broadcastSlotEvent(station.getId(), slotDate, slotTime, "BOOKED");
            notificationClient.broadcastSocketNotification(new NotificationClient.SocketNotificationPayload(
                    "WALK_IN_CREATED", "BOOKING", "Walk-In Booking Created",
                    "Admin booked station " + station.getStationCode() + " on " + slotDate + " at " + slotTime,
                    safeBookingId(booking), "/admin/bookings", null, true
            ));
        }

        return BookingResponse.from(booking, totalAmount, discountAmount, payableAmount);
    }

    private GameTitle validateGameOnStation(GameStation station, UUID gameId) {
        if (gameId == null) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "GAME_ID_REQUIRED",
                    "gameId is required to create a booking");
        }
        GameTitle game = gameRepository.findById(gameId)
                .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "GAME_NOT_FOUND",
                        "No game exists with id " + gameId));
        if (!game.isActive()) {
            throw new BookingException(HttpStatus.CONFLICT, "GAME_INACTIVE",
                    "Game '" + game.getTitle() + "' is not currently available");
        }
        if (!gameStationRepository.existsByIdAndGames_Id(station.getId(), gameId)) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "GAME_NOT_ON_STATION",
                    "Game '" + game.getTitle() + "' is not available on station " + station.getStationCode());
        }
        return game;
    }

    /** Builds and dispatches the booking-confirmed email. */
    private void sendBookingConfirmedEmail(Booking booking, BigDecimal amount) {
        // Non-critical — failure is logged but does not roll back the booking
        try {
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(SRI_LANKA_ZONE);
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(SRI_LANKA_ZONE);

            String email = null;
            String fullName = null;
            var user = userClient.getUserById(booking.getUserId());
            if (user.isPresent()) {
                email = user.get().email();
                fullName = user.get().fullName();
            }

            if (email == null || email.isBlank()) {
                log.warn("Skipping booking confirmation email for bookingId={}: no user email found", booking.getId());
                return;
            }

            notificationClient.sendBookingConfirmation(new NotificationClient.BookingEmailRequest(
                    email,
                    fullName,
                    booking.getId().toString(),
                    booking.getStation().getStationCode(),
                    dateFmt.format(booking.getStartTime()),
                    timeFmt.format(booking.getStartTime()),
                    "LKR " + amount.toPlainString()
            ));
        } catch (Exception e) {
            // notification failure must NOT roll back the payment
            log.error("Confirmation email failed for bookingId={}: {}", booking.getId(), e.getMessage());
        }
    }

    @Override
    public List<BookingResponse> getBookingsForUser(UUID userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(UUID bookingId, UUID callerId, boolean callerIsAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND",
                        "No booking exists with id " + bookingId));

        if (!callerIsAdmin && !booking.getUserId().equals(callerId)) {
            throw new BookingException(HttpStatus.FORBIDDEN, "NOT_YOUR_BOOKING",
                    "You can only cancel your own bookings");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingException(HttpStatus.CONFLICT, "ALREADY_CANCELLED",
                    "This booking is already cancelled");
        }

        if (Instant.now().plus(cancelWindowHours, ChronoUnit.HOURS).isAfter(booking.getStartTime())) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "TOO_LATE",
                    "Bookings cannot be cancelled within " + cancelWindowHours + " hours of the scheduled start time");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking = bookingRepository.save(booking);

        if (sagaCompensationService != null) {
            sagaCompensationService.executeCompensation(bookingId, "LOYALTY_REVERSE", "Booking cancelled by " + (callerIsAdmin ? "admin" : "customer"));
        } else {
            loyaltyClient.reverse(bookingId, "Booking cancelled");
        }

        String slotDate = formatSlotDate(booking.getStartTime());
        String slotTime = formatSlotTime(booking.getStartTime());
        notificationClient.broadcastSlotEvent(booking.getStation().getId(), slotDate, slotTime, "RELEASED");
        notificationClient.broadcastSocketNotification(new NotificationClient.SocketNotificationPayload(
                "BOOKING_CANCELLED", "BOOKING", "Booking Cancelled",
                "Booking on station " + booking.getStation().getStationCode() + " was cancelled.",
                safeBookingId(booking), "/customer/my-bookings", booking.getUserId() != null ? booking.getUserId().toString() : null, true
        ));

        return BookingResponse.from(booking);
    }

    @Override
    @Transactional
    public BookingResponse rescheduleBooking(UUID bookingId, UUID callerId, boolean callerIsAdmin, BookingRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND",
                        "No booking exists with id " + bookingId));

        if (!callerIsAdmin && !booking.getUserId().equals(callerId)) {
            throw new BookingException(HttpStatus.FORBIDDEN, "NOT_YOUR_BOOKING",
                    "You can only reschedule your own bookings");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "Only CONFIRMED bookings can be rescheduled");
        }

        if (Instant.now().plus(cancelWindowHours, ChronoUnit.HOURS).isAfter(booking.getStartTime())) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "TOO_LATE",
                    "Bookings cannot be rescheduled within " + cancelWindowHours + " hours of the scheduled start time");
        }

        if (!request.endTime().isAfter(request.startTime())) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE",
                    "endTime must be after startTime");
        }

        GameStation station = gameStationRepository.findById(request.stationId())
                .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND",
                        "No game station exists with id " + request.stationId()));

        if (!station.isActive()) {
            throw new BookingException(HttpStatus.CONFLICT, "STATION_INACTIVE",
                    "Station " + station.getStationCode() + " is not currently active");
        }
        UUID gameId = request.gameId() != null
                ? request.gameId()
                : (booking.getGame() != null ? booking.getGame().getId() : null);

        GameTitle game = gameId != null ? validateGameOnStation(station, gameId) : null;
        List<Booking> conflicts = bookingRepository.findOverlapping(
                station.getId(), request.startTime(), request.endTime());
        
        boolean hasConflict = conflicts.stream()
                .anyMatch(b -> !b.getId().equals(bookingId));

        if (hasConflict) {
            throw new BookingException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE",
                    "Station " + station.getStationCode() + " is already booked for part of that time range");
        }

        Instant oldStart = booking.getStartTime();
        UUID oldStationId = booking.getStation().getId();

        booking.setStation(station);
        booking.setGame(game);
        booking.setStartTime(request.startTime());
        booking.setEndTime(request.endTime());
        
        booking = bookingRepository.save(booking);

        String oldDate = formatSlotDate(oldStart);
        String oldTime = formatSlotTime(oldStart);
        String newDate = formatSlotDate(booking.getStartTime());
        String newTime = formatSlotTime(booking.getStartTime());
        notificationClient.broadcastSlotEvent(oldStationId, oldDate, oldTime, "RELEASED");
        notificationClient.broadcastSlotEvent(station.getId(), newDate, newTime, "BOOKED");

        if (sagaCompensationService != null && booking.getId() != null) {
            sagaCompensationService.recordAction(booking.getId(), "BOOKING_RESCHEDULED", "New slot: " + newDate + " " + newTime);
        }

        notificationClient.broadcastSocketNotification(new NotificationClient.SocketNotificationPayload(
                "BOOKING_RESCHEDULED", "BOOKING", "Booking Rescheduled",
                "Booking moved to " + newDate + " at " + newTime,
                safeBookingId(booking), "/customer/my-bookings", booking.getUserId() != null ? booking.getUserId().toString() : null, true
        ));

        return BookingResponse.from(booking);
    }

    @Override
    @Transactional
    public BookingResponse confirmBooking(UUID bookingId, UUID paymentId) {
        if (paymentId == null) {
            throw new BookingException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_ID_REQUIRED",
                    "paymentId is required to confirm a booking"
            );
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingException(
                        HttpStatus.NOT_FOUND,
                        "BOOKING_NOT_FOUND",
                        "No booking exists with id " + bookingId
                ));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingException(
                    HttpStatus.CONFLICT,
                    "BOOKING_NOT_PENDING",
                    "Only pending bookings can be confirmed"
            );
        }

        booking.setPaymentId(paymentId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking = bookingRepository.save(booking);

        if (sagaCompensationService != null && booking.getId() != null) {
            sagaCompensationService.recordAction(booking.getId(), "WALK_IN_CONFIRMED", "PaymentId: " + paymentId);
        }

        String slotDate = formatSlotDate(booking.getStartTime());
        String slotTime = formatSlotTime(booking.getStartTime());
        notificationClient.broadcastSlotEvent(booking.getStation().getId(), slotDate, slotTime, "BOOKED");
        String shortId = booking.getId() != null ? booking.getId().toString().substring(0, Math.min(8, booking.getId().toString().length())) : "";
        notificationClient.broadcastSocketNotification(new NotificationClient.SocketNotificationPayload(
                "BOOKING_CONFIRMED", "BOOKING", "Walk-In Payment Confirmed",
                "Booking #" + shortId + " payment confirmed.",
                safeBookingId(booking), "/admin/bookings", booking.getUserId() != null ? booking.getUserId().toString() : null, true
        ));

        // Send confirmation email once WALK_IN booking is confirmed via admin cash payment
        sendBookingConfirmedEmail(booking, BigDecimal.ZERO);

        return BookingResponse.from(booking);
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Override
    public Page<BookingResponse> getAllBookings(BookingStatus status, Pageable pageable) {
        if (status != null) {
            return bookingRepository.findByStatus(status, pageable)
                    .map(BookingResponse::from);
        }
        return bookingRepository.findAll(pageable)
                .map(BookingResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminBookingRow> getAdminBookings(
            BookingStatus status,
            BookingSource source,
            UUID userId,
            UUID stationId,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        Specification<Booking> spec = buildFilterSpec(status, source, userId, stationId, from, to);
        return bookingRepository.findAll(spec, pageable)
                .map(this::toAdminRow);
    }

    @Override
    public AdminBookingStatsResponse getBookingStats() {
        java.time.LocalDate today = java.time.LocalDate.now(SRI_LANKA_ZONE);
        Instant startOfToday = today.atStartOfDay(SRI_LANKA_ZONE).toInstant();
        Instant endOfToday = today.plusDays(1).atStartOfDay(SRI_LANKA_ZONE).toInstant();

        java.time.LocalDate firstDayOfMonth = today.withDayOfMonth(1);
        Instant startOfMonth = firstDayOfMonth.atStartOfDay(SRI_LANKA_ZONE).toInstant();

        long todayTotal = bookingRepository.countByCreatedAtBetween(startOfToday, endOfToday);
        long todayConfirmed = bookingRepository.countByStatusAndCreatedAtBetween(BookingStatus.CONFIRMED, startOfToday, endOfToday);
        long todayCancelled = bookingRepository.countByStatusAndCreatedAtBetween(BookingStatus.CANCELLED, startOfToday, endOfToday);
        long activeSessionsNow = bookingRepository.countActiveSessionsAt(Instant.now());
        long monthTotal = bookingRepository.countByCreatedAtBetween(startOfMonth, endOfToday);
        long allTimeTotal = bookingRepository.count();
        long allTimeCancelled = bookingRepository.countByStatus(BookingStatus.CANCELLED);

        return new AdminBookingStatsResponse(
                todayTotal,
                todayConfirmed,
                todayCancelled,
                activeSessionsNow,
                monthTotal,
                allTimeTotal,
                allTimeCancelled
        );
    }

    @Override
    public List<AdminBookingRow> getBookingsForExport(
            BookingStatus status,
            BookingSource source,
            UUID userId,
            UUID stationId,
            Instant from,
            Instant to
    ) {
        Specification<Booking> spec = buildFilterSpec(status, source, userId, stationId, from, to);
        Pageable limit = PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "createdAt"));
        return bookingRepository.findAll(spec, limit)
                .map(this::toAdminRow)
                .getContent();
    }

    private Specification<Booking> buildFilterSpec(
            BookingStatus status,
            BookingSource source,
            UUID userId,
            UUID stationId,
            Instant from,
            Instant to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (stationId != null) {
                predicates.add(cb.equal(root.get("station").get("id"), stationId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startTime"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AdminBookingRow toAdminRow(Booking b) {
        long durationMinutes = java.time.Duration.between(b.getStartTime(), b.getEndTime()).toMinutes();
        BigDecimal hours = BigDecimal.valueOf(durationMinutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal totalAmount = (b.getStation() != null && b.getStation().getHourlyRate() != null)
                ? b.getStation().getHourlyRate().multiply(hours).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal payableAmount = totalAmount;

        String stationType = (b.getStation() != null && b.getStation().getType() != null)
                ? b.getStation().getType().name()
                : null;
        String stationCode = b.getStation() != null ? b.getStation().getStationCode() : null;
        UUID stationId = b.getStation() != null ? b.getStation().getId() : null;
        BigDecimal hourlyRate = b.getStation() != null ? b.getStation().getHourlyRate() : null;

        return new AdminBookingRow(
                b.getId(),
                b.getUserId(),
                stationId,
                stationCode,
                stationType,
                hourlyRate,
                b.getStartTime(),
                b.getEndTime(),
                durationMinutes,
                b.getStatus(),
                b.getSource(),
                b.getPaymentId(),
                totalAmount,
                discountAmount,
                payableAmount,
                b.getCreatedAt()
        );
    }
}
