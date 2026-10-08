package com.gamingcastle.notificationservice.dto;

public record TournamentRegistrationEmailRequest(
        String email,
        String customerName,
        String tournamentName,
        String gameTitle,
        String startDate,
        String entryFee
) {}
