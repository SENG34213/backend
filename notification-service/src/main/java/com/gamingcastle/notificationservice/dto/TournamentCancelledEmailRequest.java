package com.gamingcastle.notificationservice.dto;

public record TournamentCancelledEmailRequest(
        String email,
        String customerName,
        String tournamentName,
        String reason,
        String userId
) {
    public TournamentCancelledEmailRequest(String email, String customerName, String tournamentName, String reason) {
        this(email, customerName, tournamentName, reason, null);
    }
}
