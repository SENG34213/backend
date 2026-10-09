package com.gamingcastle.paymentservice.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/** FR-14/FR-19-21: how the payment was made. */
public enum PaymentMethod {
    CARD,    // card-based checkout used by the booking/tournament flows
    ONLINE,  // via the mock/sandbox gateway integration (Sprint 6)
    CASH;    // recorded by an admin at the front desk

    @JsonCreator
    public static PaymentMethod fromValue(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CARD" -> CARD;
            case "ONLINE" -> ONLINE;
            case "CASH" -> CASH;
            default -> throw new IllegalArgumentException(
                    "Unsupported payment method: " + value
            );
        };
    }
}
