package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.BookingRequest;
import com.gamingcastle.bookingservice.dto.BookingResponse;
import com.gamingcastle.bookingservice.entity.Booking;
import com.gamingcastle.bookingservice.entity.BookingSource;
import com.gamingcastle.bookingservice.entity.BookingStatus;
import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.repository.BookingRepository;
import com.gamingcastle.bookingservice.repository.GameStationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * FR-07–FR-13: slot booking, availability, cancellation.
 */
@Service
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final GameStationRepository gameStationRepository;

    public BookingServiceImpl(BookingRepository bookingRepository, GameStationRepository gameStationRepository) {
        this.bookingRepository = bookingRepository;
        this.gameStationRepository = gameStationRepository;
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
        return BookingResponse.from(booking);
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

        booking.setStatus(BookingStatus.CANCELLED);
        booking = bookingRepository.save(booking);
        return BookingResponse.from(booking);
    }
}