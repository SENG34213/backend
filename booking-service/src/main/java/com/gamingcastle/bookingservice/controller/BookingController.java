package com.gamingcastle.bookingservice.controller;

import com.gamingcastle.bookingservice.config.GatewayHeaderAuthFilter;
import com.gamingcastle.bookingservice.dto.BookingRequest;
import com.gamingcastle.bookingservice.dto.BookingResponse;
import com.gamingcastle.bookingservice.dto.ConfirmBookingRequest;
import com.gamingcastle.bookingservice.dto.WalkInBookingRequest;
import com.gamingcastle.bookingservice.entity.BookingSource;
import com.gamingcastle.bookingservice.entity.BookingStatus;
import com.gamingcastle.bookingservice.service.BookingService;
import com.gamingcastle.bookingservice.client.NotificationClient;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    private final NotificationClient notificationClient;

    public BookingController(BookingService bookingService, NotificationClient notificationClient) {
        this.bookingService = bookingService;
        this.notificationClient = notificationClient;
    }

    /** FR-08: customer self-service booking — payment is charged automatically. */
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader(GatewayHeaderAuthFilter.USER_ID_HEADER) UUID userId,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-FullName", required = false) String fullName) {
        // BookingServiceImpl.createBooking() handles payment + confirmation email internally.
        // ONLINE bookings: PENDING → payment-service charged → CONFIRMED + email sent.
        // WALK_IN bookings: remains PENDING until admin calls PATCH /{id}/confirm.
        BookingResponse response = bookingService.createBooking(userId, request, BookingSource.ONLINE);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** FR-09: admin walk-in booking, on behalf of targetUserId. ADMIN only. */
    @PostMapping("/walk-in")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingResponse> createWalkInBooking(
            @Valid @RequestBody WalkInBookingRequest request,
            @RequestHeader("X-User-Id") UUID callerId,
            @RequestHeader("X-User-Role") String callerRole) {

        // defensive check if you want an explicit guard in case method security isn't active
        if (!"ADMIN".equalsIgnoreCase(callerRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can create walk-in bookings");
        }

        BookingResponse response = bookingService.createBooking(
                request.targetUserId(),
                new BookingRequest(request.stationId(), request.startTime(), request.endTime()),
                BookingSource.WALK_IN
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** FR-10: the caller's own booking history — never another user's. */
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
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
            @RequestHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER) String role,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-FullName", required = false) String fullName) {
        boolean isAdmin = "ADMIN".equals(role);
        BookingResponse response = bookingService.cancelBooking(bookingId, userId, isAdmin);

        if (email != null && !email.isBlank()) {
            String timeSlot = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(response.startTime());
            String dateStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(java.time.ZoneId.systemDefault())
                .format(response.startTime());

            notificationClient.sendBookingCancelled(new NotificationClient.BookingEmailRequest(
                email, fullName, response.id().toString(), response.stationCode(),
                dateStr, timeSlot, "LKR. 1,000.00"));
        }

        return ResponseEntity.ok(response);
    }

    /** Reschedule an existing CONFIRMED booking */
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<BookingResponse> rescheduleBooking(
            @PathVariable("id") UUID bookingId,
            @Valid @RequestBody BookingRequest request,
            @RequestHeader(GatewayHeaderAuthFilter.USER_ID_HEADER) UUID userId,
            @RequestHeader(GatewayHeaderAuthFilter.USER_ROLE_HEADER) String role,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-FullName", required = false) String fullName) {
        boolean isAdmin = "ADMIN".equals(role);
        BookingResponse response = bookingService.rescheduleBooking(bookingId, userId, isAdmin, request);

        if (email != null && !email.isBlank()) {
            String timeSlot = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(response.startTime());
            String dateStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(java.time.ZoneId.systemDefault())
                .format(response.startTime());

            notificationClient.sendBookingRescheduled(new NotificationClient.BookingEmailRequest(
                email, fullName, response.id().toString(), response.stationCode(),
                dateStr, timeSlot, "LKR. 1,000.00"));
        }

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ConfirmBookingRequest request
    ) {
        BookingResponse response = bookingService.confirmBooking(
                id,
                request.paymentId()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Admin: paginated list of ALL bookings across all users.
     *
     * GET /api/bookings/admin/all?page=0&size=20&sort=createdAt,desc&status=CONFIRMED
     *
     * @param status optional filter (PENDING, CONFIRMED, CANCELLED)
     * @param page   zero-based page number (default 0)
     * @param size   page size (default 20, max 100)
     * @param sort   sort field + direction, e.g. "createdAt,desc"
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/all")
    public ResponseEntity<Page<BookingResponse>> getAllBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        // cap page size at 100 to prevent runaway queries
        size = Math.min(size, 100);

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParts[0]));

        Page<BookingResponse> result = bookingService.getAllBookings(status, pageable);
        return ResponseEntity.ok(result);
    }
}
