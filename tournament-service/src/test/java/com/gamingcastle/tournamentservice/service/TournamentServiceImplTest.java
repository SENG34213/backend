package com.gamingcastle.tournamentservice.service;

import com.gamingcastle.tournamentservice.client.NotificationClient;
import com.gamingcastle.tournamentservice.client.UserClient;
import com.gamingcastle.tournamentservice.dto.AdminTournamentStatsResponse;
import com.gamingcastle.tournamentservice.dto.TournamentRequest;
import com.gamingcastle.tournamentservice.dto.TournamentResponse;
import com.gamingcastle.tournamentservice.dto.TournamentStatusUpdateRequest;
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
class TournamentServiceImplTest {

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
    private TournamentServiceImpl tournamentService;

    private UUID tournamentId;
    private UUID adminId;
    private Tournament tournament;

    @BeforeEach
    void setUp() {
        tournamentId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        tournament = Tournament.builder()
                .id(tournamentId)
                .name("Tekken 8 Clash")
                .gameTitle("Tekken 8")
                .startDate(Instant.now().plus(2, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(3, ChronoUnit.DAYS))
                .entryFee(new BigDecimal("1500.00"))
                .maxParticipants(16)
                .participantCount(0)
                .registrationDeadline(Instant.now().plus(1, ChronoUnit.DAYS))
                .status(TournamentStatus.UPCOMING)
                .createdByAdminId(adminId)
                .createdAt(Instant.now())
                .reminderSent(false)
                .build();
    }

    // ── createAndPublishTournament ───────────────────────────────────────────

    @Test
    @DisplayName("createAndPublishTournament should save tournament with UPCOMING status and 0 participants")
    void createAndPublishTournament_shouldSaveAndReturnResponse() {
        TournamentRequest request = new TournamentRequest(
                "Valorant Showdown",
                "Valorant",
                Instant.now().plus(5, ChronoUnit.DAYS),
                Instant.now().plus(6, ChronoUnit.DAYS),
                new BigDecimal("2000.00"),
                32,
                Instant.now().plus(4, ChronoUnit.DAYS)
        );

        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(invocation -> {
            Tournament t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(Instant.now());
            return t;
        });

        TournamentResponse response = tournamentService.createAndPublishTournament(request, adminId);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Valorant Showdown");
        assertThat(response.gameTitle()).isEqualTo("Valorant");
        assertThat(response.status()).isEqualTo(TournamentStatus.UPCOMING);
        assertThat(response.participantCount()).isZero();
        assertThat(response.createdByAdminId()).isEqualTo(adminId);
        assertThat(response.isRegistrationOpen()).isTrue();
        verify(tournamentRepository).save(any(Tournament.class));
    }

    // ── getAllTournaments ───────────────────────────────────────────────────

    @Test
    @DisplayName("getAllTournaments should return all tournaments mapped to responses")
    void getAllTournaments_shouldReturnMappedList() {
        when(tournamentRepository.findAll()).thenReturn(List.of(tournament));

        List<TournamentResponse> result = tournamentService.getAllTournaments();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(tournamentId);
        assertThat(result.get(0).name()).isEqualTo("Tekken 8 Clash");
    }

    // ── getTournamentById ───────────────────────────────────────────────────

    @Test
    @DisplayName("getTournamentById should return tournament when found")
    void getTournamentById_shouldReturnTournament_whenFound() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        TournamentResponse response = tournamentService.getTournamentById(tournamentId);

        assertThat(response.id()).isEqualTo(tournamentId);
        assertThat(response.name()).isEqualTo("Tekken 8 Clash");
    }

    @Test
    @DisplayName("getTournamentById should throw TournamentNotFoundException when not found")
    void getTournamentById_shouldThrowException_whenNotFound() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tournamentService.getTournamentById(tournamentId))
                .isInstanceOf(TournamentNotFoundException.class)
                .hasMessageContaining(tournamentId.toString());
    }

    // ── updateTournamentStatus ──────────────────────────────────────────────

    @Test
    @DisplayName("updateTournamentStatus should update and persist new status")
    void updateTournamentStatus_shouldUpdateAndReturn_whenFound() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        TournamentStatusUpdateRequest request = new TournamentStatusUpdateRequest(TournamentStatus.ONGOING);
        TournamentResponse response = tournamentService.updateTournamentStatus(tournamentId, request);

        assertThat(tournament.getStatus()).isEqualTo(TournamentStatus.ONGOING);
        assertThat(response.status()).isEqualTo(TournamentStatus.ONGOING);
        verify(tournamentRepository).save(tournament);
    }

    @Test
    @DisplayName("updateTournamentStatus should throw TournamentNotFoundException when not found")
    void updateTournamentStatus_shouldThrowException_whenNotFound() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.empty());

        TournamentStatusUpdateRequest request = new TournamentStatusUpdateRequest(TournamentStatus.COMPLETED);

        assertThatThrownBy(() -> tournamentService.updateTournamentStatus(tournamentId, request))
                .isInstanceOf(TournamentNotFoundException.class);
    }

    // ── cancelTournament ────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelTournament should cancel tournament and fan out notifications to confirmed and pending participants")
    void cancelTournament_shouldFanOutNotifications_toParticipants() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        TournamentRegistration reg1 = TournamentRegistration.builder()
                .id(UUID.randomUUID())
                .tournament(tournament)
                .userId(user1)
                .userEmail("player1@test.com")
                .userName("Player One")
                .status(RegistrationStatus.CONFIRMED)
                .build();

        TournamentRegistration reg2 = TournamentRegistration.builder()
                .id(UUID.randomUUID())
                .tournament(tournament)
                .userId(user2)
                .userEmail(null) // email missing, should resolve via userClient
                .userName(null)
                .status(RegistrationStatus.PENDING_PAYMENT)
                .build();

        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(reg1, reg2));
        when(userClient.getUserById(user2)).thenReturn(Optional.of(
                new UserClient.UserSummaryDto(user2, "p2", "player2@test.com", "Player Two", "CUSTOMER")
        ));

        TournamentResponse response = tournamentService.cancelTournament(tournamentId);

        assertThat(tournament.getStatus()).isEqualTo(TournamentStatus.CANCELLED);
        assertThat(reg1.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(reg2.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);

        verify(notificationClient, times(2)).sendTournamentCancelled(any());
        verify(registrationRepository).save(reg1);
        verify(registrationRepository).save(reg2);
        assertThat(response.status()).isEqualTo(TournamentStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelTournament should skip sending email if user email cannot be resolved")
    void cancelTournament_shouldSkipEmail_whenUserEmailMissing() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(tournament);

        UUID user1 = UUID.randomUUID();
        TournamentRegistration reg = TournamentRegistration.builder()
                .id(UUID.randomUUID())
                .tournament(tournament)
                .userId(user1)
                .userEmail(null)
                .userName(null)
                .status(RegistrationStatus.CONFIRMED)
                .build();

        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(reg));
        when(userClient.getUserById(user1)).thenReturn(Optional.empty());

        tournamentService.cancelTournament(tournamentId);

        verify(notificationClient, never()).sendTournamentCancelled(any());
        verify(registrationRepository).save(reg);
    }

    @Test
    @DisplayName("cancelTournament should throw TournamentNotFoundException when tournament does not exist")
    void cancelTournament_shouldThrowException_whenNotFound() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tournamentService.cancelTournament(tournamentId))
                .isInstanceOf(TournamentNotFoundException.class);
    }

    // ── isRegistrationOpen ──────────────────────────────────────────────────

    @Test
    @DisplayName("isRegistrationOpen should return true when upcoming, not full, and before deadline")
    void isRegistrationOpen_shouldReturnTrue_whenEligible() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        boolean isOpen = tournamentService.isRegistrationOpen(tournamentId);

        assertThat(isOpen).isTrue();
    }

    @Test
    @DisplayName("isRegistrationOpen should return false when participant count reached max")
    void isRegistrationOpen_shouldReturnFalse_whenFull() {
        tournament.setParticipantCount(16);
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        boolean isOpen = tournamentService.isRegistrationOpen(tournamentId);

        assertThat(isOpen).isFalse();
    }

    @Test
    @DisplayName("isRegistrationOpen should return false when deadline passed")
    void isRegistrationOpen_shouldReturnFalse_whenPastDeadline() {
        tournament.setRegistrationDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        boolean isOpen = tournamentService.isRegistrationOpen(tournamentId);

        assertThat(isOpen).isFalse();
    }

    @Test
    @DisplayName("isRegistrationOpen should return false when status is not UPCOMING")
    void isRegistrationOpen_shouldReturnFalse_whenNotUpcoming() {
        tournament.setStatus(TournamentStatus.ONGOING);
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        boolean isOpen = tournamentService.isRegistrationOpen(tournamentId);

        assertThat(isOpen).isFalse();
    }

    @Test
    @DisplayName("isRegistrationOpen should throw TournamentNotFoundException when not found")
    void isRegistrationOpen_shouldThrowException_whenNotFound() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tournamentService.isRegistrationOpen(tournamentId))
                .isInstanceOf(TournamentNotFoundException.class);
    }

    // ── processUpcomingPreTournamentReminders ────────────────────────────────

    @Test
    @DisplayName("processUpcomingPreTournamentReminders should trigger reminders for upcoming tournaments starting in <= 2h")
    void processUpcomingPreTournamentReminders_shouldSendReminders() {
        tournament.setStartDate(Instant.now().plus(1, ChronoUnit.HOURS));
        tournament.setReminderSent(false);

        when(tournamentRepository.findByStartDateLessThanEqualAndReminderSentFalse(any(Instant.class)))
                .thenReturn(List.of(tournament));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        tournamentService.processUpcomingPreTournamentReminders();

        verify(tournamentRepository).save(tournament);
        assertThat(tournament.getReminderSent()).isTrue();
    }

    @Test
    @DisplayName("processUpcomingPreTournamentReminders should ignore tournaments that are not UPCOMING")
    void processUpcomingPreTournamentReminders_shouldIgnoreNonUpcoming() {
        tournament.setStatus(TournamentStatus.CANCELLED);

        when(tournamentRepository.findByStartDateLessThanEqualAndReminderSentFalse(any(Instant.class)))
                .thenReturn(List.of(tournament));

        tournamentService.processUpcomingPreTournamentReminders();

        verify(tournamentRepository, never()).findById(tournamentId);
    }

    // ── sendPreTournamentReminders ──────────────────────────────────────────

    @Test
    @DisplayName("sendPreTournamentReminders should mark reminderSent when no confirmed registrations")
    void sendPreTournamentReminders_shouldMarkSent_whenNoConfirmedRegistrations() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        tournamentService.sendPreTournamentReminders(tournamentId);

        assertThat(tournament.getReminderSent()).isTrue();
        verify(tournamentRepository).save(tournament);
        verify(notificationClient, never()).sendTournamentReminder(any());
    }

    @Test
    @DisplayName("sendPreTournamentReminders should send reminder with opponent info and bye handling")
    void sendPreTournamentReminders_shouldSendReminder_withOpponentInfo() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID(); // odd participant with bye

        TournamentRegistration reg1 = TournamentRegistration.builder()
                .userId(p1).userEmail("p1@test.com").userName("P1").status(RegistrationStatus.CONFIRMED).build();
        TournamentRegistration reg2 = TournamentRegistration.builder()
                .userId(p2).userEmail("p2@test.com").userName("P2").status(RegistrationStatus.CONFIRMED).build();
        TournamentRegistration reg3 = TournamentRegistration.builder()
                .userId(p3).userEmail("p3@test.com").userName(null).status(RegistrationStatus.CONFIRMED).build();

        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(reg1, reg2, reg3));

        UUID bracketId = UUID.randomUUID();
        Bracket bracket = Bracket.builder().id(bracketId).tournamentId(tournamentId).build();
        when(bracketRepository.findByTournamentId(tournamentId)).thenReturn(Optional.of(bracket));

        Match match1 = Match.builder().id(UUID.randomUUID()).bracketId(bracketId).round(1)
                .participant1Id(p1).participant2Id(p2).status(MatchStatus.SCHEDULED).build();
        Match match2 = Match.builder().id(UUID.randomUUID()).bracketId(bracketId).round(1)
                .participant1Id(p3).participant2Id(null).status(MatchStatus.COMPLETED).winnerId(p3).build();

        when(matchRepository.findByBracketIdAndRound(bracketId, 1)).thenReturn(List.of(match1, match2));

        tournamentService.sendPreTournamentReminders(tournamentId);

        ArgumentCaptor<NotificationClient.TournamentReminderEmailRequest> captor =
                ArgumentCaptor.forClass(NotificationClient.TournamentReminderEmailRequest.class);

        verify(notificationClient, times(3)).sendTournamentReminder(captor.capture());
        List<NotificationClient.TournamentReminderEmailRequest> requests = captor.getAllValues();

        // P1's opponent should be P2
        assertThat(requests.stream().anyMatch(r -> r.email().equals("p1@test.com") && r.opponentName().equals("P2"))).isTrue();
        // P2's opponent should be P1
        assertThat(requests.stream().anyMatch(r -> r.email().equals("p2@test.com") && r.opponentName().equals("P1"))).isTrue();
        // P3 has a bye
        assertThat(requests.stream().anyMatch(r -> r.email().equals("p3@test.com") && r.opponentName().contains("Bye"))).isTrue();

        assertThat(tournament.getReminderSent()).isTrue();
    }

    @Test
    @DisplayName("sendPreTournamentReminders should resolve opponent name via userClient if userName missing")
    void sendPreTournamentReminders_shouldResolveOpponentName_fromUserClient() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        TournamentRegistration reg1 = TournamentRegistration.builder()
                .userId(p1).userEmail("p1@test.com").userName("P1").status(RegistrationStatus.CONFIRMED).build();
        TournamentRegistration reg2 = TournamentRegistration.builder()
                .userId(p2).userEmail("p2@test.com").userName(null).status(RegistrationStatus.CONFIRMED).build();

        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(reg1, reg2));

        UUID bracketId = UUID.randomUUID();
        Bracket bracket = Bracket.builder().id(bracketId).tournamentId(tournamentId).build();
        when(bracketRepository.findByTournamentId(tournamentId)).thenReturn(Optional.of(bracket));

        Match match = Match.builder().id(UUID.randomUUID()).bracketId(bracketId).round(1)
                .participant1Id(p1).participant2Id(p2).status(MatchStatus.SCHEDULED).build();
        when(matchRepository.findByBracketIdAndRound(bracketId, 1)).thenReturn(List.of(match));

        when(userClient.getUserById(p2)).thenReturn(Optional.of(
                new UserClient.UserSummaryDto(p2, "p2user", "p2@test.com", "Resolved Player Two", "CUSTOMER")
        ));

        tournamentService.sendPreTournamentReminders(tournamentId);

        ArgumentCaptor<NotificationClient.TournamentReminderEmailRequest> captor =
                ArgumentCaptor.forClass(NotificationClient.TournamentReminderEmailRequest.class);
        verify(notificationClient, times(2)).sendTournamentReminder(captor.capture());

        assertThat(captor.getAllValues().stream()
                .anyMatch(r -> r.email().equals("p1@test.com") && r.opponentName().equals("Resolved Player Two"))).isTrue();
    }

    @Test
    @DisplayName("sendPreTournamentReminders should throw TournamentNotFoundException when tournament does not exist")
    void sendPreTournamentReminders_shouldThrowException_whenNotFound() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tournamentService.sendPreTournamentReminders(tournamentId))
                .isInstanceOf(TournamentNotFoundException.class);
    }

    // ── getTournamentStats ──────────────────────────────────────────────────

    @Test
    @DisplayName("getTournamentStats should return aggregated tournament counts and participant sum")
    void getTournamentStats_shouldReturnAggregatedStats() {
        when(tournamentRepository.count()).thenReturn(10L);
        when(tournamentRepository.countByStatus(TournamentStatus.UPCOMING)).thenReturn(4L);
        when(tournamentRepository.countByStatus(TournamentStatus.ONGOING)).thenReturn(2L);
        when(tournamentRepository.countByStatus(TournamentStatus.COMPLETED)).thenReturn(3L);
        when(tournamentRepository.countByStatus(TournamentStatus.CANCELLED)).thenReturn(1L);
        when(tournamentRepository.sumTotalParticipants()).thenReturn(78L);

        AdminTournamentStatsResponse stats = tournamentService.getTournamentStats();

        assertThat(stats.totalTournaments()).isEqualTo(10L);
        assertThat(stats.upcomingTournaments()).isEqualTo(4L);
        assertThat(stats.ongoingTournaments()).isEqualTo(2L);
        assertThat(stats.completedTournaments()).isEqualTo(3L);
        assertThat(stats.cancelledTournaments()).isEqualTo(1L);
        assertThat(stats.totalParticipants()).isEqualTo(78L);
    }
}
