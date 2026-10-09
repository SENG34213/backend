package com.gamingcastle.loyaltyservice.exception;

/** Thrown when fewer points than the configured minimum are redeemed. */
public class BelowMinimumRedemptionException extends RedemptionRejectedException {

    public BelowMinimumRedemptionException(long minimumPoints) {
        super(ErrorCodes.BELOW_MINIMUM_REDEMPTION, "Minimum redemption is " + minimumPoints + " points");
    }
}
