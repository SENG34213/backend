package com.gamingcastle.tournamentservice.controller;

import com.gamingcastle.tournamentservice.dto.*;
import com.gamingcastle.tournamentservice.entity.MatchStatus;
import com.gamingcastle.tournamentservice.entity.RegistrationStatus;
import com.gamingcastle.tournamentservice.entity.TournamentStatus;
import com.gamingcastle.tournamentservice.service.BracketService;
import com.gamingcastle.tournamentservice.service.RegistrationService;
import com.gamingcastle.tournamentservice.service.TournamentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentControllerTest {

    @Mock
    private TournamentService tournamentService;

    @Mock
    private RegistrationService registrationService;

    @Mock
    private BracketService bracketService;

    @InjectMocks
    private TournamentController tournamentController;

    private UUID tournamentId;
    private UUID adminId;
    private UUID userId;
    private TournamentResponse tournamentResponse;

    @BeforeEach
    void setUp() {
        tournamentId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();

        tournamentResponse = new TournamentResponse(
                tournamentId,
                "FC 26 Invitational",
                "EA Sports FC 26",
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS),
                new BigDecimal("2000.00"),
                16,
                2,
                Instant.now().plus(1, ChronoUnit.DAYS),
                TournamentStatus.UPCOMING,
                adminId,
                Instant.now(),
                true
        );
    }

    @Test
    @DisplayName("createTournament should call service and return 201 Created")
    void createTournament_shouldReturnCreated() {
        TournamentRequest request = new TournamentRequest(
                "FC 26 Invitational",
                "EA Sports FC 26",
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS),
                new BigDecimal("2000.00"),
                16,
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        when(tournamentService.createAndPublishTournament(request, adminId)).thenReturn(tournamentResponse);

        ResponseEntity<TournamentResponse> response = tournamentController.createTournament(request, adminId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(tournamentResponse);
        verify(tournamentService).createAndPublishTournament(request, adminId);
    }

    @Test
    @DisplayName("getAllTournaments should return 200 OK with tournaments list")
    void getAllTournaments_shouldReturnOkWithList() {
        when(tournamentService.getAllTournaments()).thenReturn(List.of(tournamentResponse));

        ResponseEntity<List<TournamentResponse>> response = tournamentController.getAllTournaments();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(tournamentResponse);
    }

    @Test
    @DisplayName("getTournamentStats should return 200 OK with admin stats")
    void getTournamentStats_shouldReturnOkWithStats() {
        AdminTournamentStatsResponse stats = new AdminTournamentStatsResponse(10, 4, 2, 3, 1, 48);
        when(tournamentService.getTournamentStats()).thenReturn(stats);

        ResponseEntity<AdminTournamentStatsResponse> response = tournamentController.getTournamentStats();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(stats);
    }

    @Test
    @DisplayName("getTournamentById should return 200 OK with tournament response")
    void getTournamentById_shouldReturnOkWithTournament() {
        when(tournamentService.getTournamentById(tournamentId)).thenReturn(tournamentResponse);

        ResponseEntity<TournamentResponse> response = tournamentController.getTournamentById(tournamentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(tournamentResponse);
    }

    @Test
    @DisplayName("updateTournamentStatus should return 200 OK with updated tournament")
    void updateTournamentStatus_shouldReturnOkWithUpdatedTournament() {
        TournamentStatusUpdateRequest request = new TournamentStatusUpdateRequest(TournamentStatus.ONGOING);
        when(tournamentService.updateTournamentStatus(tournamentId, request)).thenReturn(tournamentResponse);

        ResponseEntity<TournamentResponse> response = tournamentController.updateTournamentStatus(tournamentId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(tournamentResponse);
    }

    @Test
    @DisplayName("cancelTournament should return 200 OK with cancelled tournament")
    void cancelTournament_shouldReturnOkWithCancelledTournament() {
        when(tournamentService.cancelTournament(tournamentId)).thenReturn(tournamentResponse);

        ResponseEntity<TournamentResponse> response = tournamentController.cancelTournament(tournamentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(tournamentResponse);
    }

    @Test
    @DisplayName("registerForTournament should return 201 Created with registration response")
    void registerForTournament_shouldReturnCreated() {
        RegistrationResponse regResponse = new RegistrationResponse(
                UUID.randomUUID(),
                tournamentId,
                "FC 26 Invitational",
                userId,
                RegistrationStatus.CONFIRMED,
                UUID.randomUUID(),
                Instant.now()
        );

        when(registrationService.registerForTournament(tournamentId, userId, "user@test.com", "Gamer Pro"))
                .thenReturn(regResponse);

        ResponseEntity<RegistrationResponse> response = tournamentController.registerForTournament(
                tournamentId, userId, "user@test.com", "Gamer Pro"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(regResponse);
    }

    @Test
    @DisplayName("getRegistrations should return 200 OK with registrations list")
    void getRegistrations_shouldReturnOkWithList() {
        RegistrationResponse regResponse = new RegistrationResponse(
                UUID.randomUUID(),
                tournamentId,
                "FC 26 Invitational",
                userId,
                RegistrationStatus.CONFIRMED,
                UUID.randomUUID(),
                Instant.now()
        );

        when(registrationService.getRegistrationsForTournament(tournamentId)).thenReturn(List.of(regResponse));

        ResponseEntity<List<RegistrationResponse>> response = tournamentController.getRegistrations(tournamentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(regResponse);
    }

    @Test
    @DisplayName("generateBracket should return 201 Created with generated bracket")
    void generateBracket_shouldReturnCreated() {
        BracketResponse bracketResponse = new BracketResponse(UUID.randomUUID(), tournamentId, Instant.now(), List.of());
        when(bracketService.generateBracket(tournamentId)).thenReturn(bracketResponse);

        ResponseEntity<BracketResponse> response = tournamentController.generateBracket(tournamentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(bracketResponse);
    }

    @Test
    @DisplayName("getBracket should return 200 OK with tournament bracket")
    void getBracket_shouldReturnOk() {
        BracketResponse bracketResponse = new BracketResponse(UUID.randomUUID(), tournamentId, Instant.now(), List.of());
        when(bracketService.getBracket(tournamentId)).thenReturn(bracketResponse);

        ResponseEntity<BracketResponse> response = tournamentController.getBracket(tournamentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(bracketResponse);
    }

    @Test
    @DisplayName("recordMatchResult should return 200 OK with updated match")
    void recordMatchResult_shouldReturnOk() {
        UUID matchId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        MatchResultRequest request = new MatchResultRequest(winnerId);
        MatchResponse matchResponse = new MatchResponse(
                matchId, UUID.randomUUID(), 1, winnerId, UUID.randomUUID(), winnerId, MatchStatus.COMPLETED, Instant.now()
        );

        when(bracketService.recordMatchResult(tournamentId, matchId, request)).thenReturn(matchResponse);

        ResponseEntity<MatchResponse> response = tournamentController.recordMatchResult(tournamentId, matchId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(matchResponse);
    }

    @Test
    @DisplayName("sendPreTournamentReminders should trigger reminders and return 200 OK")
    void sendPreTournamentReminders_shouldReturnOk() {
        ResponseEntity<Void> response = tournamentController.sendPreTournamentReminders(tournamentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(tournamentService).sendPreTournamentReminders(tournamentId);
    }
}
