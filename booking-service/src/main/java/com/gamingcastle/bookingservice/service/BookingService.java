package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.BookingRequest;
import com.gamingcastle.bookingservice.dto.BookingResponse;
import com.gamingcastle.bookingservice.entity.BookingSource;
import com.gamingcastle.bookingservice.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * FR-07–FR-13. The controller (BK-2) and tests depend on this interface,
 * not on BookingServiceImpl directly — same convention as user-service's
 * AuthService/AuthServiceImpl split.
 */
public interface BookingService {

    /**
     * @param userId the customer the booking is FOR (the caller for an
     *               ONLINE booking; the target customer for a WALK_IN
     *               booking created by an admin)
     * @param source ONLINE (customer self-service) or WALK_IN (admin-entered)
     */
    BookingResponse createBooking(UUID userId, BookingRequest request, BookingSource source);

    List<BookingResponse> getBookingsForUser(UUID userId);

    /**
     * @param callerId the authenticated caller
     * @param callerIsAdmin true if the caller's role is ADMIN — admins may
     *                      cancel any booking, customers only their own
     */
    BookingResponse cancelBooking(UUID bookingId, UUID callerId, boolean callerIsAdmin);
    /**
     * Reschedule an existing CONFIRMED booking
     */
    BookingResponse rescheduleBooking(UUID bookingId, UUID callerId, boolean callerIsAdmin, BookingRequest request);

    /**
     * Confirms a pending booking after successful payment.
     *
     * @param bookingId the booking being confirmed
     * @param paymentId the payment associated with the successful payment
     */
    BookingResponse confirmBooking(UUID bookingId, UUID paymentId);

    /**
     * Admin: paginated view of all bookings, with optional status filter.
     *
     * @param status   optional filter — pass null to return all statuses
     * @param pageable page + sort info (e.g. page=0&size=20&sort=createdAt,desc)
     */
    Page<BookingResponse> getAllBookings(BookingStatus status, Pageable pageable);
}