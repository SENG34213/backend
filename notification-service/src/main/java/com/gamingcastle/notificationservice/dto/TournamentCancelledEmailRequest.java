package com.gamingcastle.notificationservice.dto;

public record TournamentCancelledEmailRequest(
        String email,
        String customerName,
        String tournamentName,
        String reason
) {}
