package com.gamingcastle.tournamentservice.service;

import com.gamingcastle.tournamentservice.dto.BracketResponse;
import com.gamingcastle.tournamentservice.dto.MatchResponse;
import com.gamingcastle.tournamentservice.dto.MatchResultRequest;
import com.gamingcastle.tournamentservice.entity.Bracket;
import com.gamingcastle.tournamentservice.entity.Match;
import com.gamingcastle.tournamentservice.entity.MatchStatus;
import com.gamingcastle.tournamentservice.entity.RegistrationStatus;
import com.gamingcastle.tournamentservice.entity.TournamentRegistration;
import com.gamingcastle.tournamentservice.exception.TournamentNotFoundException;
import com.gamingcastle.tournamentservice.repository.BracketRepository;
import com.gamingcastle.tournamentservice.repository.MatchRepository;
import com.gamingcastle.tournamentservice.repository.TournamentRegistrationRepository;
import com.gamingcastle.tournamentservice.repository.TournamentRepository;
import com.gamingcastle.tournamentservice.client.NotificationClient;
import com.gamingcastle.tournamentservice.client.UserClient;
import com.gamingcastle.tournamentservice.entity.TournamentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BracketServiceImpl implements BracketService {

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final BracketRepository bracketRepository;
    private final MatchRepository matchRepository;
    private final NotificationClient notificationClient;
    private final UserClient userClient;

    public BracketServiceImpl(
            TournamentRepository tournamentRepository,
            TournamentRegistrationRepository registrationRepository,
            BracketRepository bracketRepository,
            MatchRepository matchRepository,
            NotificationClient notificationClient,
            UserClient userClient
    ) {
        this.tournamentRepository = tournamentRepository;
        this.registrationRepository = registrationRepository;
        this.bracketRepository = bracketRepository;
        this.matchRepository = matchRepository;
        this.notificationClient = notificationClient;
        this.userClient = userClient;
    }

    /**
     * FR-18: Rule-based single-elimination bracket generation.
     * Algorithm:
     *  1. Collect all CONFIRMED participant IDs.
     *  2. Shuffle randomly (fair, no seeding — satisfies "lightweight" guidance).
     *  3. Pair sequentially: [0,1], [2,3], ...
     *  4. If odd number of participants, the last one gets a bye (participant2 = null, auto-advances).
     */
    @Override
    @Transactional
    public BracketResponse generateBracket(UUID tournamentId) {
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new TournamentNotFoundException("Tournament not found: " + tournamentId);
        }

        if (bracketRepository.findByTournamentId(tournamentId).isPresent()) {
            throw new IllegalStateException("Bracket already generated for tournament: " + tournamentId);
        }

        // Collect confirmed participant IDs
        List<UUID> participantIds = registrationRepository.findByTournamentId(tournamentId).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED)
                .map(TournamentRegistration::getUserId)
                .collect(Collectors.toCollection(ArrayList::new));

        if (participantIds.size() < 2) {
            throw new IllegalStateException(
                    "Need at least 2 confirmed participants to generate a bracket.");
        }

        // Shuffle for fairness (random pairing — rule-based, not ML)
        Collections.shuffle(participantIds);

        // Create the bracket
        Bracket bracket = bracketRepository.save(
                Bracket.builder()
                        .tournamentId(tournamentId)
                        .build()
        );

        // Pair into Round 1 matches (odd participant gets bye)
        List<Match> matches = buildRoundMatches(bracket.getId(), 1, participantIds);
        matchRepository.saveAll(matches);

        return buildBracketResponse(bracket, matchRepository.findByBracketIdOrderByRoundAsc(bracket.getId()));
    }

    /**
     * FR-15/FR-18: Record the winner of a match.
     * After saving, if the current round is fully COMPLETED,
     * automatically generates the next round's matches from winners.
     */
    @Override
    @Transactional
    public MatchResponse recordMatchResult(UUID tournamentId, UUID matchId, MatchResultRequest request) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        UUID winnerId = request.winnerId();

        // Validate winner is one of the participants (or participant1 in a bye)
        boolean isByeMatch = match.getParticipant2Id() == null;
        boolean validWinner = winnerId.equals(match.getParticipant1Id())
                || (!isByeMatch && winnerId.equals(match.getParticipant2Id()));

        if (!validWinner) {
            throw new IllegalArgumentException(
                    "Winner must be one of the match participants.");
        }

        match.setWinnerId(winnerId);
        match.setStatus(MatchStatus.COMPLETED);
        matchRepository.save(match);

        // Check if entire current round is complete → auto-generate next round
        List<Match> roundMatches = matchRepository.findByBracketIdAndRound(
                match.getBracketId(), match.getRound());

        boolean roundComplete = roundMatches.stream()
                .allMatch(m -> m.getStatus() == MatchStatus.COMPLETED);

        if (roundComplete) {
            List<UUID> winners = roundMatches.stream()
                    .map(Match::getWinnerId)
                    .collect(Collectors.toList());

            if (winners.size() == 1) {
                // Tournament champion determined — no more rounds to generate
                UUID championId = winners.get(0);
                tournamentRepository.findById(tournamentId).ifPresent(t -> {
                    t.setStatus(TournamentStatus.COMPLETED);
                    tournamentRepository.save(t);
                    fanOutResultsNotification(t, championId);
                });
            } else {
                // Generate next round
                int nextRound = match.getRound() + 1;
                List<Match> nextRoundMatches = buildRoundMatches(match.getBracketId(), nextRound, winners);
                matchRepository.saveAll(nextRoundMatches);
            }
        }

        return mapToMatchResponse(match);
    }

    private void fanOutResultsNotification(com.gamingcastle.tournamentservice.entity.Tournament tournament, UUID championId) {
        String championName = userClient.getUserById(championId)
                .map(UserClient.UserSummaryDto::fullName)
                .orElse("Winner (" + championId + ")");

        var registrations = registrationRepository.findByTournamentId(tournament.getId());
        for (var reg : registrations) {
            if (reg.getStatus() == RegistrationStatus.CONFIRMED) {
                String recipientEmail = reg.getUserEmail();
                String recipientName = reg.getUserName();

                if (recipientEmail == null || recipientEmail.isBlank()) {
                    var userOpt = userClient.getUserById(reg.getUserId());
                    recipientEmail = userOpt.map(UserClient.UserSummaryDto::email).orElse(null);
                    if (recipientName == null || recipientName.isBlank()) {
                        recipientName = userOpt.map(UserClient.UserSummaryDto::fullName).orElse("Gamer");
                    }
                }

                if (recipientEmail != null && !recipientEmail.isBlank()) {
                    notificationClient.sendTournamentResultsPublished(
                            new NotificationClient.TournamentResultsEmailRequest(
                                    recipientEmail,
                                    recipientName != null ? recipientName : "Gamer",
                                    tournament.getName(),
                                    tournament.getGameTitle(),
                                    championName,
                                    "Congratulations to " + championName + " for winning the tournament!"
                            )
                    );
                } else {
                    System.err.println("Skipping results email: no email found for participant " + reg.getUserId());
                }
            }
        }
    }

    /** FR-15/FR-16: View entire bracket with all rounds and matches. */
    @Override
    public BracketResponse getBracket(UUID tournamentId) {
        Bracket bracket = bracketRepository.findByTournamentId(tournamentId)
                .orElseThrow(() -> new IllegalStateException(
                        "No bracket generated yet for tournament: " + tournamentId));

        List<Match> matches = matchRepository.findByBracketIdOrderByRoundAsc(bracket.getId());
        return buildBracketResponse(bracket, matches);
    }

    /**
     * Pairs a list of participant IDs into Match entities for the given round.
     * Odd participant at the end gets a bye (participant2 = null) and auto-advances
     * — winner is set immediately so next-round generation picks them up correctly.
     */
    private List<Match> buildRoundMatches(UUID bracketId, int round, List<UUID> participants) {
        List<Match> matches = new ArrayList<>();

        for (int i = 0; i < participants.size(); i += 2) {
            UUID p1 = participants.get(i);
            UUID p2 = (i + 1 < participants.size()) ? participants.get(i + 1) : null;

            Match match = Match.builder()
                    .bracketId(bracketId)
                    .round(round)
                    .participant1Id(p1)
                    .participant2Id(p2)
                    .status(p2 == null ? MatchStatus.COMPLETED : MatchStatus.SCHEDULED)
                    .winnerId(p2 == null ? p1 : null) // auto-advance bye participant
                    .build();

            matches.add(match);
        }

        return matches;
    }

    private BracketResponse buildBracketResponse(Bracket bracket, List<Match> matches) {
        List<MatchResponse> matchResponses = matches.stream()
                .map(this::mapToMatchResponse)
                .collect(Collectors.toList());

        return new BracketResponse(
                bracket.getId(),
                bracket.getTournamentId(),
                bracket.getGeneratedAt(),
                matchResponses
        );
    }

    private MatchResponse mapToMatchResponse(Match m) {
        return new MatchResponse(
                m.getId(),
                m.getBracketId(),
                m.getRound(),
                m.getParticipant1Id(),
                m.getParticipant2Id(),
                m.getWinnerId(),
                m.getStatus(),
                m.getScheduledTime()
        );
    }
}
