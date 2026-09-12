package com.gamingcastle.bookingservice.entity;

/** FR-08/FR-09: distinguishes a customer self-service booking from an admin walk-in entry. */
public enum BookingSource {
    ONLINE,
    WALK_IN
}
