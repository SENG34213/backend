package com.gamingcastle.bookingservice.entity;

/** FR-07–FR-13 lifecycle for a booking. */
public enum BookingStatus {
    PENDING,     // created, awaiting payment confirmation
    CONFIRMED,   // payment succeeded
    CANCELLED,
    COMPLETED
}
