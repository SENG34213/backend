package com.gamingcastle.bookingservice.controller;

import com.gamingcastle.bookingservice.config.GatewayHeaderAuthFilter;
import com.gamingcastle.bookingservice.dto.BookingRequest;
import com.gamingcastle.bookingservice.dto.BookingResponse;
import com.gamingcastle.bookingservice.dto.WalkInBookingRequest;
import com.gamingcastle.bookingservice.entity.BookingSource;
import com.gamingcastle.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * FR-08–FR-10/FR-13. Caller identity always comes from the Gateway-forwarded
 * X-User-Id/X-User-Role headers (see GatewayHeaderAuthFilter) — never from
 * the request body, so a customer can never book or cancel "as" someone else.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** FR-08: customer self-service booking. */
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader(GatewayHeaderAuthFilter.USER_ID_HEADER) UUID userId) {
        BookingResponse response = bookingService.createBooking(userId, request, BookingSource.ONLINE);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** FR-09: admin walk-in booking, on behalf of targetUserId. ADMIN only. */
    @PostMapping("/walk-in")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BookingResponse> createWalkInBooking(@Valid @RequestBody WalkInBookingRequest request) {
        BookingRequest bookingRequest = new BookingRequest(request.stationId(), request.startTime(), request.endTime());
        BookingResponse response = bookingService.createBooking(request.targetUserId(), bookingRequest, BookingSource.WALK_IN);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** FR-10: the caller's own booking history — never another user's. */
    @GetMapping("/mine")
    public ResponseEntity<List<BookingResponse>> getMyBookings(
            @RequestHeader(GatewayHeaderAuthFilter.USER_ID_HEADER) UUID userId) {
        return ResponseEntity.ok(bookingService.getBookingsForUser(userId));
    }

    /** FR-13: cancel — own booking, or any booking if the caller is ADMIN. */
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable("id") UUID bookingId,
            @RequestHeader(GatewayHeaderAuthFilter.USER_ID_HEADER) UUID userId,
            @RequestHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER) String role) {
        boolean isAdmin = "ADMIN".equals(role);
        BookingResponse response = bookingService.cancelBooking(bookingId, userId, isAdmin);
        return ResponseEntity.ok(response);
    }
}
