package com.gamingcastle.loyaltyservice.dto.response;

import java.util.Map;

/**
 * G11 — Admin dashboard loyalty stats.
 */
public record AdminLoyaltyStatsResponse(
        long totalAccounts,
        long totalPointsInCirculation,
        long totalPointsAwardedAllTime,
        long totalPointsRedeemedAllTime,
        Map<String, Long> accountsByTier
) {}
