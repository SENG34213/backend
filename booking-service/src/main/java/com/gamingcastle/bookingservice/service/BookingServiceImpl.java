package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.BookingRequest;
import com.gamingcastle.bookingservice.dto.BookingResponse;
import com.gamingcastle.bookingservice.client.LoyaltyClient;
import com.gamingcastle.bookingservice.entity.Booking;
import com.gamingcastle.bookingservice.entity.BookingSource;
import com.gamingcastle.bookingservice.entity.BookingStatus;
import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.repository.BookingRepository;
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
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * FR-07–FR-13: slot booking, availability, cancellation.
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    private final BookingRepository bookingRepository;
    private final GameStationRepository gameStationRepository;
    private final LoyaltyClient loyaltyClient;
    private final PaymentClient paymentClient;
    private final NotificationClient notificationClient;
    private final UserClient userClient;

    @Value("${booking.cancel-window-hours:2}")
    private int cancelWindowHours = 2;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            GameStationRepository gameStationRepository,
            LoyaltyClient loyaltyClient,
            PaymentClient paymentClient,
            NotificationClient notificationClient,
            UserClient userClient) {
        this.bookingRepository = bookingRepository;
        this.gameStationRepository = gameStationRepository;
        this.loyaltyClient = loyaltyClient;
        this.paymentClient = paymentClient;
        this.notificationClient = notificationClient;
        this.userClient = userClient;
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
                throw e;
            }

            // ADD THIS NULL CHECK
            if (paymentResult == null) {
                booking.setStatus(BookingStatus.CANCELLED);
                bookingRepository.save(booking);
                throw new BookingException(HttpStatus.BAD_REQUEST, "PAYMENT_FAILED",
                        "Payment processing returned no result");
            }

            // Payment succeeded — confirm immediately
            booking.setPaymentId(paymentResult.paymentId());
            booking.setStatus(BookingStatus.CONFIRMED);
            booking = bookingRepository.save(booking);

            discountAmount = paymentResult.discountAmount();
            payableAmount = paymentResult.payableAmount();

            // Send confirmation email after successful payment
            sendBookingConfirmedEmail(booking, payableAmount);
        } else {
            // WALK_IN: admin records cash manually via POST /api/payments/cash later
            // Booking remains PENDING until admin calls PATCH /{id}/confirm with paymentId
            booking = bookingRepository.save(booking);
        }

        return BookingResponse.from(booking, totalAmount, discountAmount, payableAmount);
    }

    /** Builds and dispatches the booking-confirmed email. */
    private void sendBookingConfirmedEmail(Booking booking, BigDecimal amount) {
        // Non-critical — failure is logged but does not roll back the booking
        try {
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

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
        loyaltyClient.reverse(bookingId, "Booking cancelled");
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

        List<Booking> conflicts = bookingRepository.findOverlapping(
                station.getId(), request.startTime(), request.endTime());
        
        boolean hasConflict = conflicts.stream()
                .anyMatch(b -> !b.getId().equals(bookingId));

        if (hasConflict) {
            throw new BookingException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE",
                    "Station " + station.getStationCode() + " is already booked for part of that time range");
        }

        booking.setStation(station);
        booking.setStartTime(request.startTime());
        booking.setEndTime(request.endTime());
        
        booking = bookingRepository.save(booking);
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
}
