package com.gamingcastle.userservice.dto.response;

/**
 * G11 — Admin dashboard user stats widget.
 */
public record AdminUserStatsResponse(
        long totalUsers,
        long activeUsers,
        long deactivatedUsers,
        long customerCount,
        long adminCount,
        long newUsersToday,
        long newUsersThisMonth
) {}
