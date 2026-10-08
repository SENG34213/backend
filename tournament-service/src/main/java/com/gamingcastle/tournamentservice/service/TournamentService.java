package com.gamingcastle.tournamentservice.service;

import com.gamingcastle.tournamentservice.dto.TournamentRequest;
import com.gamingcastle.tournamentservice.dto.TournamentResponse;
import com.gamingcastle.tournamentservice.dto.TournamentStatusUpdateRequest;
import java.util.List;
import java.util.UUID;

public interface TournamentService {
    TournamentResponse createAndPublishTournament(TournamentRequest request, UUID adminId);
    List<TournamentResponse> getAllTournaments();
    TournamentResponse getTournamentById(UUID id);
    TournamentResponse updateTournamentStatus(UUID id, TournamentStatusUpdateRequest request);
    TournamentResponse cancelTournament(UUID id);
    boolean isRegistrationOpen(UUID id);
    void sendPreTournamentReminders(UUID tournamentId);
    void processUpcomingPreTournamentReminders();
}
