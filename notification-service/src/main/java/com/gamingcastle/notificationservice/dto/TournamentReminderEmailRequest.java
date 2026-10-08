package com.gamingcastle.notificationservice.dto;

public record TournamentReminderEmailRequest(
        String email,
        String customerName,
        String tournamentName,
        String gameTitle,
        String startDate,
        String opponentName,
        String roundInfo
) {}
