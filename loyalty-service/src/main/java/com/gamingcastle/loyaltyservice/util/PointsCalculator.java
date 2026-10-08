package com.gamingcastle.loyaltyservice.util;

import com.gamingcastle.loyaltyservice.entity.LoyaltyTier;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PointsCalculator {

    private PointsCalculator() {
    }

    public static BigDecimal tierMultiplier(LoyaltyTier tier, BigDecimal silverMultiplier, BigDecimal goldMultiplier) {
        if (tier == null) {
            return BigDecimal.ONE;
        }
        return switch (tier) {
            case SILVER -> silverMultiplier;
            case GOLD -> goldMultiplier;
            case BRONZE -> BigDecimal.ONE;
        };
    }

    public static long calculateEarnedPoints(BigDecimal amountPaid, BigDecimal amountPerPoint, BigDecimal tierMultiplier) {
        if (amountPaid == null || amountPaid.signum() <= 0 || amountPerPoint == null || amountPerPoint.signum() <= 0) {
            return 0L;
        }
        BigDecimal multiplier = tierMultiplier == null ? BigDecimal.ONE : tierMultiplier;
        BigDecimal basePoints = amountPaid.divide(amountPerPoint, 0, RoundingMode.DOWN);
        return basePoints.multiply(multiplier).setScale(0, RoundingMode.DOWN).longValue();
    }

    public static BigDecimal calculateDiscount(long points, BigDecimal pointValueLkr) {
        if (points <= 0 || pointValueLkr == null || pointValueLkr.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(points).multiply(pointValueLkr).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculatePayableAmount(BigDecimal bookingTotal, BigDecimal discountAmount) {
        if (bookingTotal == null) {
            return BigDecimal.ZERO;
        }
        if (discountAmount == null || discountAmount.signum() <= 0) {
            return bookingTotal;
        }
        return bookingTotal.subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal maxDiscount(BigDecimal bookingTotal, long maxDiscountPercent) {
        if (bookingTotal == null || bookingTotal.signum() <= 0 || maxDiscountPercent <= 0) {
            return BigDecimal.ZERO;
        }
        return bookingTotal.multiply(BigDecimal.valueOf(maxDiscountPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
    }

    public static boolean isMultipleOfStep(long points, long redeemStep) {
        return points > 0 && redeemStep > 0 && points % redeemStep == 0;
    }
}
