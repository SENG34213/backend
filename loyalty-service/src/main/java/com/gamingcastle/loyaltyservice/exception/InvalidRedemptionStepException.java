package com.gamingcastle.loyaltyservice.exception;

/** Thrown when the points are not a multiple of the configured redemption step. */
public class InvalidRedemptionStepException extends RedemptionRejectedException {

    public InvalidRedemptionStepException(long step) {
        super(ErrorCodes.INVALID_REDEMPTION_STEP, "Points must be redeemed in multiples of " + step);
    }
}
