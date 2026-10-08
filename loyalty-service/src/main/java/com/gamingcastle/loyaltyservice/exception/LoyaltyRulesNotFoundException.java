package com.gamingcastle.loyaltyservice.exception;

public class LoyaltyRulesNotFoundException extends RuntimeException {

    public LoyaltyRulesNotFoundException() {
        super("Loyalty rules are not configured yet");
    }
}
