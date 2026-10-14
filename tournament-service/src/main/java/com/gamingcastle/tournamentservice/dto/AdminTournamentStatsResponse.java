package com.gamingcastle.tournamentservice.dto;

/**
 * G11 — Admin dashboard tournament stats widget.
 */
public record AdminTournamentStatsResponse(
        long totalTournaments,
        long upcomingTournaments,
        long ongoingTournaments,
        long completedTournaments,
        long cancelledTournaments,
        long totalParticipants
) {}
