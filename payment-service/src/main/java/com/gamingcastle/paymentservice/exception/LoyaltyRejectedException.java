package com.gamingcastle.paymentservice.exception;

public class LoyaltyRejectedException extends RuntimeException {

    public LoyaltyRejectedException(String message) {
        super(message);
    }
}
