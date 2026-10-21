package com.gamingcastle.tournamentservice.service;

import com.gamingcastle.tournamentservice.client.NotificationClient;
import com.gamingcastle.tournamentservice.client.UserClient;
import com.gamingcastle.tournamentservice.dto.BracketResponse;
import com.gamingcastle.tournamentservice.dto.MatchResponse;
import com.gamingcastle.tournamentservice.dto.MatchResultRequest;
import com.gamingcastle.tournamentservice.entity.*;
import com.gamingcastle.tournamentservice.exception.TournamentNotFoundException;
import com.gamingcastle.tournamentservice.repository.BracketRepository;
import com.gamingcastle.tournamentservice.repository.MatchRepository;
import com.gamingcastle.tournamentservice.repository.TournamentRegistrationRepository;
import com.gamingcastle.tournamentservice.repository.TournamentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BracketServiceImplTest {

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private TournamentRegistrationRepository registrationRepository;

    @Mock
    private BracketRepository bracketRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private BracketServiceImpl bracketService;

    private UUID tournamentId;
    private Tournament tournament;

    @BeforeEach
    void setUp() {
        tournamentId = UUID.randomUUID();
        tournament = Tournament.builder()
                .id(tournamentId)
                .name("Championship 2026")
                .gameTitle("FIFA 26")
                .startDate(Instant.now().plus(2, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(3, ChronoUnit.DAYS))
                .entryFee(new BigDecimal("1000.00"))
                .maxParticipants(8)
                .participantCount(4)
                .registrationDeadline(Instant.now().plus(1, ChronoUnit.DAYS))
                .status(TournamentStatus.UPCOMING)
                .createdByAdminId(UUID.randomUUID())
                .createdAt(Instant.now())
                .reminderSent(false)
                .build();
    }

    // ── generateBracket ─────────────────────────────────────────────────────

    @Test
    @DisplayName("generateBracket should throw TournamentNotFoundException when tournament does not exist")
    void generateBracket_shouldThrowNotFound_whenTournamentMissing() {
        when(tournamentRepository.existsById(tournamentId)).thenReturn(false);

        assertThatThrownBy(() -> bracketService.generateBracket(tournamentId))
                .isInstanceOf(TournamentNotFoundException.class)
                .hasMessageContaining(tournamentId.toString());
    }

    @Test
    @DisplayName("generateBracket should throw IllegalStateException when bracket already exists")
    void generateBracket_shouldThrowIllegalState_whenBracketAlreadyExists() {
        when(tournamentRepository.existsById(tournamentId)).thenReturn(true);
        when(bracketRepository.findByTournamentId(tournamentId)).thenReturn(Optional.of(
                Bracket.builder().id(UUID.randomUUID()).tournamentId(tournamentId).build()
        ));

        assertThatThrownBy(() -> bracketService.generateBracket(tournamentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bracket already generated");
    }

    @Test
    @DisplayName("generateBracket should throw IllegalStateException when fewer than 2 confirmed participants")
    void generateBracket_shouldThrowIllegalState_whenLessThanTwoConfirmedParticipants() {
        when(tournamentRepository.existsById(tournamentId)).thenReturn(true);
        when(bracketRepository.findByTournamentId(tournamentId)).thenReturn(Optional.empty());

        TournamentRegistration reg = TournamentRegistration.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .status(RegistrationStatus.CONFIRMED)
                .build();
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(reg));

        assertThatThrownBy(() -> bracketService.generateBracket(tournamentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Need at least 2 confirmed participants");
    }

    @Test
    @DisplayName("generateBracket should create paired matches for even number of participants")
    void generateBracket_shouldCreateMatches_forEvenParticipants() {
        when(tournamentRepository.existsById(tournamentId)).thenReturn(true);
        when(bracketRepository.findByTournamentId(tournamentId)).thenReturn(Optional.empty());

        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();

        List<TournamentRegistration> regs = List.of(
                TournamentRegistration.builder().id(UUID.randomUUID()).userId(u1).status(RegistrationStatus.CONFIRMED).build(),
                TournamentRegistration.builder().id(UUID.randomUUID()).userId(u2).status(RegistrationStatus.CONFIRMED).build(),
                TournamentRegistration.builder().id(UUID.randomUUID()).userId(u3).status(RegistrationStatus.CONFIRMED).build(),
                TournamentRegistration.builder().id(UUID.randomUUID()).userId(u4).status(RegistrationStatus.CONFIRMED).build()
        );
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(regs);

        UUID bracketId = UUID.randomUUID();
        Bracket bracket = Bracket.builder().id(bracketId).tournamentId(tournamentId).generatedAt(Instant.now()).build();
        when(bracketRepository.save(any(Bracket.class))).thenReturn(bracket);

        List<Match> savedMatchesList1 = new java.util.ArrayList<>();
        when(matchRepository.saveAll(any())).thenAnswer(inv -> {
            List<Match> list = inv.getArgument(0);
            savedMatchesList1.addAll(list);
            return list;
        });
        when(matchRepository.findByBracketIdOrderByRoundAsc(bracketId)).thenAnswer(inv -> savedMatchesList1);

        BracketResponse response = bracketService.generateBracket(tournamentId);

        assertThat(savedMatchesList1).hasSize(2);
        assertThat(savedMatchesList1).allMatch(m -> m.getRound() == 1);
        assertThat(savedMatchesList1).allMatch(m -> m.getStatus() == MatchStatus.SCHEDULED);
        assertThat(savedMatchesList1).allMatch(m -> m.getParticipant2Id() != null);
        assertThat(response.matches()).hasSize(2);
    }

    @Test
    @DisplayName("generateBracket should create a bye match for odd number of participants")
    void generateBracket_shouldCreateByeMatch_forOddParticipants() {
        when(tournamentRepository.existsById(tournamentId)).thenReturn(true);
        when(bracketRepository.findByTournamentId(tournamentId)).thenReturn(Optional.empty());

        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();

        List<TournamentRegistration> regs = List.of(
                TournamentRegistration.builder().id(UUID.randomUUID()).userId(u1).status(RegistrationStatus.CONFIRMED).build(),
                TournamentRegistration.builder().id(UUID.randomUUID()).userId(u2).status(RegistrationStatus.CONFIRMED).build(),
                TournamentRegistration.builder().id(UUID.randomUUID()).userId(u3).status(RegistrationStatus.CONFIRMED).build()
        );
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(regs);

        UUID bracketId = UUID.randomUUID();
        Bracket bracket = Bracket.builder().id(bracketId).tournamentId(tournamentId).generatedAt(Instant.now()).build();
        when(bracketRepository.save(any(Bracket.class))).thenReturn(bracket);

        List<Match> savedMatchesList2 = new java.util.ArrayList<>();
        when(matchRepository.saveAll(any())).thenAnswer(inv -> {
            List<Match> list = inv.getArgument(0);
            savedMatchesList2.addAll(list);
            return list;
        });
        when(matchRepository.findByBracketIdOrderByRoundAsc(bracketId)).thenAnswer(inv -> savedMatchesList2);

        BracketResponse response = bracketService.generateBracket(tournamentId);

        assertThat(savedMatchesList2).hasSize(2);
        // Match 2 has null participant2Id, status COMPLETED, and winnerId set to participant1Id (auto-advance bye)
        Match byeMatch = savedMatchesList2.stream().filter(m -> m.getParticipant2Id() == null).findFirst().orElseThrow();
        assertThat(byeMatch.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(byeMatch.getWinnerId()).isEqualTo(byeMatch.getParticipant1Id());
    }

    // ── recordMatchResult ───────────────────────────────────────────────────

    @Test
    @DisplayName("recordMatchResult should throw IllegalArgumentException when match does not exist")
    void recordMatchResult_shouldThrowException_whenMatchNotFound() {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.findById(matchId)).thenReturn(Optional.empty());

        MatchResultRequest request = new MatchResultRequest(UUID.randomUUID());

        assertThatThrownBy(() -> bracketService.recordMatchResult(tournamentId, matchId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Match not found");
    }

    @Test
    @DisplayName("recordMatchResult should throw IllegalArgumentException when winner is neither participant")
    void recordMatchResult_shouldThrowException_whenWinnerInvalid() {
        UUID matchId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID invalidWinner = UUID.randomUUID();

        Match match = Match.builder()
                .id(matchId).bracketId(UUID.randomUUID()).round(1)
                .participant1Id(p1).participant2Id(p2).status(MatchStatus.SCHEDULED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchResultRequest request = new MatchResultRequest(invalidWinner);

        assertThatThrownBy(() -> bracketService.recordMatchResult(tournamentId, matchId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Winner must be one of the match participants");
    }

    @Test
    @DisplayName("recordMatchResult should update match when current round is not yet fully completed")
    void recordMatchResult_shouldSaveWinner_whenRoundNotComplete() {
        UUID bracketId = UUID.randomUUID();
        UUID matchId1 = UUID.randomUUID();
        UUID matchId2 = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        Match m1 = Match.builder()
                .id(matchId1).bracketId(bracketId).round(1)
                .participant1Id(p1).participant2Id(p2).status(MatchStatus.SCHEDULED).build();

        Match m2 = Match.builder()
                .id(matchId2).bracketId(bracketId).round(1)
                .participant1Id(UUID.randomUUID()).participant2Id(UUID.randomUUID())
                .status(MatchStatus.SCHEDULED).build(); // still scheduled

        when(matchRepository.findById(matchId1)).thenReturn(Optional.of(m1));
        when(matchRepository.findByBracketIdAndRound(bracketId, 1)).thenReturn(List.of(m1, m2));

        MatchResultRequest request = new MatchResultRequest(p1);
        MatchResponse response = bracketService.recordMatchResult(tournamentId, matchId1, request);

        assertThat(m1.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(m1.getWinnerId()).isEqualTo(p1);
        assertThat(response.winnerId()).isEqualTo(p1);
        verify(matchRepository).save(m1);
        verify(matchRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("recordMatchResult should auto-generate next round matches when current round completes with multiple winners")
    void recordMatchResult_shouldGenerateNextRound_whenRoundCompletesWithMultipleWinners() {
        UUID bracketId = UUID.randomUUID();
        UUID matchId1 = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        UUID p4 = UUID.randomUUID();

        Match m1 = Match.builder()
                .id(matchId1).bracketId(bracketId).round(1)
                .participant1Id(p1).participant2Id(p2).status(MatchStatus.SCHEDULED).build();

        Match m2 = Match.builder()
                .id(UUID.randomUUID()).bracketId(bracketId).round(1)
                .participant1Id(p3).participant2Id(p4).status(MatchStatus.COMPLETED).winnerId(p3).build();

        when(matchRepository.findById(matchId1)).thenReturn(Optional.of(m1));
        when(matchRepository.findByBracketIdAndRound(bracketId, 1)).thenReturn(List.of(m1, m2));

        MatchResultRequest request = new MatchResultRequest(p1);
        bracketService.recordMatchResult(tournamentId, matchId1, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Match>> captor = ArgumentCaptor.forClass(List.class);
        verify(matchRepository).saveAll(captor.capture());

        List<Match> nextRoundMatches = captor.getValue();
        assertThat(nextRoundMatches).hasSize(1);
        assertThat(nextRoundMatches.get(0).getRound()).isEqualTo(2);
        assertThat(nextRoundMatches.get(0).getParticipant1Id()).isEqualTo(p1);
        assertThat(nextRoundMatches.get(0).getParticipant2Id()).isEqualTo(p3);
    }

    @Test
    @DisplayName("recordMatchResult should determine champion, set tournament COMPLETED, and fan out results when 1 winner remains")
    void recordMatchResult_shouldCrownChampion_andFanOutResults_whenFinalWinner() {
        UUID bracketId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID championId = UUID.randomUUID();
        UUID runnerUpId = UUID.randomUUID();

        Match finalMatch = Match.builder()
                .id(matchId).bracketId(bracketId).round(2)
                .participant1Id(championId).participant2Id(runnerUpId).status(MatchStatus.SCHEDULED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(finalMatch));
        when(matchRepository.findByBracketIdAndRound(bracketId, 2)).thenReturn(List.of(finalMatch));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        TournamentRegistration reg1 = TournamentRegistration.builder()
                .userId(championId).userEmail("champ@test.com").userName("Champion").status(RegistrationStatus.CONFIRMED).build();
        TournamentRegistration reg2 = TournamentRegistration.builder()
                .userId(runnerUpId).userEmail(null).userName(null).status(RegistrationStatus.CONFIRMED).build();

        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(reg1, reg2));
        when(userClient.getUserById(championId)).thenReturn(Optional.of(
                new UserClient.UserSummaryDto(championId, "champ", "champ@test.com", "The Champion", "CUSTOMER")
        ));
        when(userClient.getUserById(runnerUpId)).thenReturn(Optional.of(
                new UserClient.UserSummaryDto(runnerUpId, "runnerup", "runner@test.com", "Runner Up", "CUSTOMER")
        ));

        MatchResultRequest request = new MatchResultRequest(championId);
        bracketService.recordMatchResult(tournamentId, matchId, request);

        assertThat(tournament.getStatus()).isEqualTo(TournamentStatus.COMPLETED);
        verify(tournamentRepository).save(tournament);

        ArgumentCaptor<NotificationClient.TournamentResultsEmailRequest> captor =
                ArgumentCaptor.forClass(NotificationClient.TournamentResultsEmailRequest.class);
        verify(notificationClient, times(2)).sendTournamentResultsPublished(captor.capture());

        assertThat(captor.getAllValues()).allMatch(r -> r.winnerName().equals("The Champion"));
    }

    @Test
    @DisplayName("recordMatchResult should fallback to winner ID string if userClient returns empty for champion")
    void recordMatchResult_shouldFallbackChampionName_whenUserClientEmpty() {
        UUID bracketId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID championId = UUID.randomUUID();

        Match finalMatch = Match.builder()
                .id(matchId).bracketId(bracketId).round(1)
                .participant1Id(championId).participant2Id(null).status(MatchStatus.SCHEDULED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(finalMatch));
        when(matchRepository.findByBracketIdAndRound(bracketId, 1)).thenReturn(List.of(finalMatch));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        TournamentRegistration reg = TournamentRegistration.builder()
                .userId(championId).userEmail("champ@test.com").userName("Champ").status(RegistrationStatus.CONFIRMED).build();

        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(reg));
        when(userClient.getUserById(championId)).thenReturn(Optional.empty());

        MatchResultRequest request = new MatchResultRequest(championId);
        bracketService.recordMatchResult(tournamentId, matchId, request);

        ArgumentCaptor<NotificationClient.TournamentResultsEmailRequest> captor =
                ArgumentCaptor.forClass(NotificationClient.TournamentResultsEmailRequest.class);
        verify(notificationClient).sendTournamentResultsPublished(captor.capture());

        assertThat(captor.getValue().winnerName()).contains(championId.toString());
    }

    @Test
    @DisplayName("recordMatchResult should skip sending results email if participant email cannot be resolved")
    void recordMatchResult_shouldSkipEmail_whenParticipantEmailUnresolvable() {
        UUID bracketId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID championId = UUID.randomUUID();

        Match finalMatch = Match.builder()
                .id(matchId).bracketId(bracketId).round(1)
                .participant1Id(championId).participant2Id(null).status(MatchStatus.SCHEDULED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(finalMatch));
        when(matchRepository.findByBracketIdAndRound(bracketId, 1)).thenReturn(List.of(finalMatch));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        TournamentRegistration reg = TournamentRegistration.builder()
                .userId(championId).userEmail(null).userName(null).status(RegistrationStatus.CONFIRMED).build();

        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(reg));
        when(userClient.getUserById(championId)).thenReturn(Optional.empty());

        MatchResultRequest request = new MatchResultRequest(championId);
        bracketService.recordMatchResult(tournamentId, matchId, request);

        verify(notificationClient, never()).sendTournamentResultsPublished(any());
    }

    // ── getBracket ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getBracket should return bracket response with matches sorted by round")
    void getBracket_shouldReturnBracket_whenFound() {
        UUID bracketId = UUID.randomUUID();
        Bracket bracket = Bracket.builder().id(bracketId).tournamentId(tournamentId).generatedAt(Instant.now()).build();
        when(bracketRepository.findByTournamentId(tournamentId)).thenReturn(Optional.of(bracket));

        Match m1 = Match.builder().id(UUID.randomUUID()).bracketId(bracketId).round(1)
                .participant1Id(UUID.randomUUID()).participant2Id(UUID.randomUUID()).status(MatchStatus.SCHEDULED).build();
        Match m2 = Match.builder().id(UUID.randomUUID()).bracketId(bracketId).round(2)
                .participant1Id(UUID.randomUUID()).participant2Id(UUID.randomUUID()).status(MatchStatus.SCHEDULED).build();

        when(matchRepository.findByBracketIdOrderByRoundAsc(bracketId)).thenReturn(List.of(m1, m2));

        BracketResponse response = bracketService.getBracket(tournamentId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(bracketId);
        assertThat(response.tournamentId()).isEqualTo(tournamentId);
        assertThat(response.matches()).hasSize(2);
        assertThat(response.matches().get(0).round()).isEqualTo(1);
        assertThat(response.matches().get(1).round()).isEqualTo(2);
    }

    @Test
    @DisplayName("getBracket should throw IllegalStateException when no bracket generated yet")
    void getBracket_shouldThrowIllegalState_whenMissing() {
        when(bracketRepository.findByTournamentId(tournamentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bracketService.getBracket(tournamentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No bracket generated yet");
    }
}
