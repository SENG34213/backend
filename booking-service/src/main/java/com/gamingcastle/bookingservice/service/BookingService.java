package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.BookingRequest;
import com.gamingcastle.bookingservice.dto.BookingResponse;
import com.gamingcastle.bookingservice.entity.BookingSource;

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
}