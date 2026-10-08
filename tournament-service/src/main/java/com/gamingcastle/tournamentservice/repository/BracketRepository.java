package com.gamingcastle.tournamentservice.repository;

import com.gamingcastle.tournamentservice.entity.Bracket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface BracketRepository extends JpaRepository<Bracket, UUID> {
    Optional<Bracket> findByTournamentId(UUID tournamentId);
}
