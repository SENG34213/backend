package com.gamingcastle.tournamentservice.service;

import com.gamingcastle.tournamentservice.dto.BracketResponse;
import com.gamingcastle.tournamentservice.dto.MatchResultRequest;
import com.gamingcastle.tournamentservice.dto.MatchResponse;
import java.util.UUID;

public interface BracketService {

    /**
     * FR-18: Generate a single-elimination bracket for a tournament.
     * Takes all CONFIRMED registrations, shuffles them randomly,
     * pairs them into Round 1 matches. Odd participant gets a bye (auto-advances).
     */
    BracketResponse generateBracket(UUID tournamentId);

    /**
     * FR-15/FR-18: Admin records the result of a match.
     * Marks the match COMPLETED, sets the winner.
     * When all matches in a round are COMPLETED, generates the next round's matches
     * from the winners automatically.
     */
    MatchResponse recordMatchResult(UUID tournamentId, UUID matchId, MatchResultRequest request);

    /**
     * FR-15/FR-16: View the full bracket (all rounds, all matches) for a tournament.
     */
    BracketResponse getBracket(UUID tournamentId);
}
