package com.gamingcastle.notificationservice.dto;

public record TournamentResultsEmailRequest(
        String email,
        String customerName,
        String tournamentName,
        String gameTitle,
        String winnerName,
        String message
) {}
