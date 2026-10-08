package com.gamingcastle.loyaltyservice.exception;

import java.util.UUID;

public class LoyaltyAccountNotFoundException extends RuntimeException {

    public LoyaltyAccountNotFoundException(UUID userId) {
        super("No loyalty account found for userId " + userId);
    }
}
