package com.gamingcastle.loyaltyservice.exception;

import java.math.BigDecimal;

/** Thrown when the discount would exceed the maximum share of the booking total. */
public class RedemptionLimitExceededException extends RedemptionRejectedException {

    public RedemptionLimitExceededException(BigDecimal maxDiscount, long maxPercent) {
        super(ErrorCodes.REDEMPTION_LIMIT_EXCEEDED,
                "Maximum discount for this booking is LKR " + maxDiscount.toPlainString()
                        + " (" + maxPercent + "% of the booking total)");
    }
}
