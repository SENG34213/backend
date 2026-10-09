package com.gamingcastle.loyaltyservice.exception;

/**
 * Base class for every business-rule rejection of a redemption (UC-11). Each
 * subclass carries its own error code so the payment-service and the frontend
 * can show a precise, customer-readable reason.
 */
public abstract class RedemptionRejectedException extends RuntimeException {

    private final String errorCode;

    protected RedemptionRejectedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
