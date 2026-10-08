package com.gamingcastle.tournamentservice.service;

import com.gamingcastle.tournamentservice.dto.RegistrationResponse;
import java.util.List;
import java.util.UUID;

public interface RegistrationService {
    RegistrationResponse registerForTournament(UUID tournamentId, UUID userId);
    RegistrationResponse registerForTournament(UUID tournamentId, UUID userId, String email, String fullName);
    List<RegistrationResponse> getRegistrationsForTournament(UUID tournamentId);
}
