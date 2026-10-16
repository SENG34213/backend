package com.gamingcastle.bookingservice.dto;

public record GameStatsResponse(
    long totalGames,
    long activeGames,
    long featuredGames,
    String topGenre,
    long multiplayerGamesCount
) {}
