package com.gamingcastle.tournamentservice.repository;

import com.gamingcastle.tournamentservice.entity.TournamentRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TournamentRegistrationRepository extends JpaRepository<TournamentRegistration, UUID> {
    List<TournamentRegistration> findByTournamentId(UUID tournamentId);
    List<TournamentRegistration> findByUserId(UUID userId);
}
