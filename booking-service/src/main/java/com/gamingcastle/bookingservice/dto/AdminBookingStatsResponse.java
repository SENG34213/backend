package com.gamingcastle.bookingservice.dto;

/**
 * G11 — Admin dashboard stats from the booking service.
 * Covers: today's booking counts, active sessions, and month totals.
 */
public record AdminBookingStatsResponse(

        /** Bookings created today (SLST), any status. */
        long todayTotalBookings,

        /** Bookings confirmed today. */
        long todayConfirmedBookings,

        /** Bookings cancelled today. */
        long todayCancelledBookings,

        /** Sessions currently active right now (CONFIRMED + in progress). */
        long activeSessionsNow,

        /** Total bookings this calendar month. */
        long monthTotalBookings,

        /** All-time total bookings ever recorded. */
        long allTimeTotalBookings,

        /** All-time total cancellations. */
        long allTimeCancelled
) {}
