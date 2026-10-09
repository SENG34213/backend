package com.gamingcastle.loyaltyservice.util;

import com.gamingcastle.loyaltyservice.entity.LoyaltyTier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PointsCalculatorTest {

    private static final BigDecimal PER_POINT = new BigDecimal("100.00");
    private static final BigDecimal SILVER = new BigDecimal("1.25");
    private static final BigDecimal GOLD = new BigDecimal("1.50");

    @Test
    void tierMultiplier_shouldReadValuesFromRules() {
        assertThat(PointsCalculator.tierMultiplier(LoyaltyTier.BRONZE, SILVER, GOLD)).isEqualByComparingTo("1.00");
        assertThat(PointsCalculator.tierMultiplier(LoyaltyTier.SILVER, SILVER, GOLD)).isEqualByComparingTo("1.25");
        assertThat(PointsCalculator.tierMultiplier(LoyaltyTier.GOLD, SILVER, new BigDecimal("2.00")))
                .isEqualByComparingTo("2.00");
    }

    @Test
    void calculateEarnedPoints_shouldFloorForBronze() {
        long points = PointsCalculator.calculateEarnedPoints(new BigDecimal("1250.00"), PER_POINT, BigDecimal.ONE);
        assertThat(points).isEqualTo(12);
    }

    @Test
    void calculateEarnedPoints_shouldApplySilverMultiplier() {
        long points = PointsCalculator.calculateEarnedPoints(new BigDecimal("2000.00"), PER_POINT, SILVER);
        assertThat(points).isEqualTo(25);
    }

    @Test
    void calculateEarnedPoints_shouldApplyGoldMultiplierAndFloorAgain() {
        long points = PointsCalculator.calculateEarnedPoints(new BigDecimal("1500.00"), PER_POINT, GOLD);
        assertThat(points).isEqualTo(22);
    }

    @Test
    void calculateEarnedPoints_shouldReturnZero_whenBelowOnePoint() {
        assertThat(PointsCalculator.calculateEarnedPoints(new BigDecimal("99.99"), PER_POINT, BigDecimal.ONE)).isZero();
    }

    @Test
    void calculateEarnedPoints_shouldReturnZero_whenAmountIsZeroOrNull() {
        assertThat(PointsCalculator.calculateEarnedPoints(BigDecimal.ZERO, PER_POINT, BigDecimal.ONE)).isZero();
        assertThat(PointsCalculator.calculateEarnedPoints(null, PER_POINT, BigDecimal.ONE)).isZero();
    }

    @Test
    void calculateDiscount_shouldMultiplyPointsByPointValue() {
        assertThat(PointsCalculator.calculateDiscount(400, new BigDecimal("0.50"))).isEqualByComparingTo("200.00");
    }

    @Test
    void calculatePayableAmount_shouldSubtractDiscountAndNeverGoBelowZero() {
        assertThat(PointsCalculator.calculatePayableAmount(new BigDecimal("1200.00"), new BigDecimal("200.00")))
                .isEqualByComparingTo("1000.00");
        assertThat(PointsCalculator.calculatePayableAmount(new BigDecimal("100.00"), new BigDecimal("500.00")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void maxDiscount_shouldBeTheConfiguredShareOfTheTotal() {
        assertThat(PointsCalculator.maxDiscount(new BigDecimal("1200.00"), 50)).isEqualByComparingTo("600.00");
        assertThat(PointsCalculator.maxDiscount(new BigDecimal("500.00"), 50)).isEqualByComparingTo("250.00");
    }

    @Test
    void isMultipleOfStep_shouldAcceptOnlyMultiples() {
        assertThat(PointsCalculator.isMultipleOfStep(110, 10)).isTrue();
        assertThat(PointsCalculator.isMultipleOfStep(105, 10)).isFalse();
        assertThat(PointsCalculator.isMultipleOfStep(0, 10)).isFalse();
    }
}
