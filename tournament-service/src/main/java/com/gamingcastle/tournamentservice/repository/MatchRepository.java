package com.gamingcastle.tournamentservice.repository;

import com.gamingcastle.tournamentservice.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    List<Match> findByBracketIdOrderByRoundAsc(UUID bracketId);
    List<Match> findByBracketIdAndRound(UUID bracketId, int round);
}
