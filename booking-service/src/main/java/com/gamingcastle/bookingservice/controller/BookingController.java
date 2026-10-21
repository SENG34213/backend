package com.gamingcastle.bookingservice.controller;

import org.springframework.data.web.PagedModel;
import com.gamingcastle.bookingservice.config.GatewayHeaderAuthFilter;
import com.gamingcastle.bookingservice.dto.AdminBookingRow;
import com.gamingcastle.bookingservice.dto.AdminBookingStatsResponse;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

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

    private static final java.time.ZoneId SRI_LANKA_ZONE = java.time.ZoneId.of("Asia/Colombo");

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
                new BookingRequest(request.stationId(), request.gameId(), request.startTime(), request.endTime()),
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
                .withZone(SRI_LANKA_ZONE)
                .format(response.startTime());
            String dateStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(SRI_LANKA_ZONE)
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
                .withZone(SRI_LANKA_ZONE)
                .format(response.startTime());
            String dateStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(SRI_LANKA_ZONE)
                .format(response.startTime());

            notificationClient.sendBookingRescheduled(new NotificationClient.BookingEmailRequest(
                    email, fullName, response.id().toString(), response.stationCode(),
                    dateStr, timeSlot,
                    "LKR. " + String.format("%,.2f", response.totalAmount())));
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
    /**
     * Admin: enriched, paginated list of ALL bookings across all users with multi-parameter filtering.
     *
     * GET /api/bookings/admin/all
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) BookingSource source,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID stationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        // enforce maximum page size
        size = Math.min(size, 100);

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParts[0]));

        // If no advanced filters are provided, use the simple paginated API (Page<BookingResponse>)
        if (source == null && userId == null && stationId == null && from == null && to == null) {
            Page<BookingResponse> result = bookingService.getAllBookings(status, pageable);
            return ResponseEntity.ok(result);
        }

        // Otherwise, use the enriched admin bookings API (Page<AdminBookingRow>)
        Page<AdminBookingRow> result = bookingService.getAdminBookings(
                status, source, userId, stationId, from, to, pageable
        );
        return ResponseEntity.ok(result);
    }

    // ── G11: Admin dashboard booking stats ────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/stats")
    public ResponseEntity<AdminBookingStatsResponse> getBookingStats() {
        return ResponseEntity.ok(bookingService.getBookingStats());
    }

    // ── G13: CSV export of bookings ───────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/admin/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportBookingsCsv(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) BookingSource source,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID stationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        List<AdminBookingRow> bookings = bookingService.getBookingsForExport(
                status, source, userId, stationId, from, to);

        StringBuilder sb = new StringBuilder();
        sb.append("Booking ID,User ID,Station ID,Station Code,Station Type,Hourly Rate,Start Time,End Time,Duration (Mins),Status,Source,Payment ID,Total Amount,Discount Amount,Payable Amount,Created At\n");
        for (AdminBookingRow b : bookings) {
            sb.append(b.id()).append(",")
              .append(b.userId()).append(",")
              .append(b.stationId()).append(",")
              .append(b.stationCode() != null ? b.stationCode() : "").append(",")
              .append(b.stationType() != null ? b.stationType() : "").append(",")
              .append(b.hourlyRate() != null ? b.hourlyRate() : "").append(",")
              .append(b.startTime()).append(",")
              .append(b.endTime()).append(",")
              .append(b.durationMinutes()).append(",")
              .append(b.status()).append(",")
              .append(b.source()).append(",")
              .append(b.paymentId() != null ? b.paymentId() : "").append(",")
              .append(b.totalAmount() != null ? b.totalAmount() : "").append(",")
              .append(b.discountAmount() != null ? b.discountAmount() : "").append(",")
              .append(b.payableAmount() != null ? b.payableAmount() : "").append(",")
              .append(b.createdAt()).append("\n");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bookings_export.csv\"");
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        return ResponseEntity.ok().headers(headers).body(sb.toString());
    }
}
