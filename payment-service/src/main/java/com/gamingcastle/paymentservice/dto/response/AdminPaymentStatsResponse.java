package com.gamingcastle.paymentservice.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * G11 — payload returned by GET /api/payments/admin/stats.
 * Covers the payment/revenue slice of the admin dashboard widget.
 */
public record AdminPaymentStatsResponse(

        /** Total revenue (SUCCESS payments) for today (SLST). */
        BigDecimal todayRevenue,

        /** Total revenue for the current calendar month (SLST). */
        BigDecimal monthRevenue,

        /** Total number of payments ever recorded (SUCCESS). */
        long totalPaymentsSuccess,

        /** Total number of payments that FAILED. */
        long totalPaymentsFailed,

        /** Revenue per day for the last {@code trendDays} days (for the chart). */
        List<DailyRevenue> revenueTrend

) {
    public record DailyRevenue(String date, BigDecimal revenue) {}
}
