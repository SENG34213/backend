package com.gamingcastle.tournamentservice.controller;

import com.gamingcastle.tournamentservice.client.NotificationClient;
import com.gamingcastle.tournamentservice.client.PaymentClient;
import com.gamingcastle.tournamentservice.client.UserClient;
import com.gamingcastle.tournamentservice.dto.*;
import com.gamingcastle.tournamentservice.entity.*;
import com.gamingcastle.tournamentservice.exception.PaymentFailedException;
import com.gamingcastle.tournamentservice.repository.BracketRepository;
import com.gamingcastle.tournamentservice.repository.MatchRepository;
import com.gamingcastle.tournamentservice.repository.TournamentRegistrationRepository;
import com.gamingcastle.tournamentservice.repository.TournamentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TournamentControllerIntegrationTest {

    private static final String GATEWAY_SECRET = "test-gateway-internal-secret";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TournamentRegistrationRepository registrationRepository;

    @Autowired
    private BracketRepository bracketRepository;

    @Autowired
    private MatchRepository matchRepository;

    @MockBean
    private PaymentClient paymentClient;

    @MockBean
    private NotificationClient notificationClient;

    @MockBean
    private UserClient userClient;

    private UUID adminUserId;
    private UUID customerUserId;

    @BeforeEach
    void setUp() {
        matchRepository.deleteAll();
        bracketRepository.deleteAll();
        registrationRepository.deleteAll();
        tournamentRepository.deleteAll();

        adminUserId = UUID.randomUUID();
        customerUserId = UUID.randomUUID();
    }

    private HttpHeaders adminHeaders(UUID adminId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", GATEWAY_SECRET);
        headers.set("X-User-Id", adminId.toString());
        headers.set("X-User-Role", "ADMIN");
        return headers;
    }

    private HttpHeaders customerHeaders(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", GATEWAY_SECRET);
        headers.set("X-User-Id", userId.toString());
        headers.set("X-User-Role", "CUSTOMER");
        headers.set("X-User-Email", "customer@test.com");
        headers.set("X-User-FullName", "Test Customer");
        return headers;
    }

    private HttpHeaders guestHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", GATEWAY_SECRET);
        return headers;
    }

    private Tournament createSampleTournament(String name, TournamentStatus status, int maxParticipants, int participantCount) {
        Tournament t = Tournament.builder()
                .name(name)
                .gameTitle("Tekken 8")
                .startDate(Instant.now().plus(3, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(4, ChronoUnit.DAYS))
                .entryFee(new BigDecimal("1500.00"))
                .maxParticipants(maxParticipants)
                .participantCount(participantCount)
                .registrationDeadline(Instant.now().plus(2, ChronoUnit.DAYS))
                .status(status)
                .createdByAdminId(adminUserId)
                .reminderSent(false)
                .build();
        return tournamentRepository.save(t);
    }

    // ── 1. POST /api/tournaments ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/tournaments - should create tournament when caller has ROLE_ADMIN")
    void createTournament_shouldReturnCreated_whenAdmin() {
        TournamentRequest request = new TournamentRequest(
                "Winter Major 2026",
                "Street Fighter 6",
                Instant.now().plus(5, ChronoUnit.DAYS),
                Instant.now().plus(6, ChronoUnit.DAYS),
                new BigDecimal("2500.00"),
                32,
                Instant.now().plus(4, ChronoUnit.DAYS)
        );

        HttpEntity<TournamentRequest> entity = new HttpEntity<>(request, adminHeaders(adminUserId));
        ResponseEntity<TournamentResponse> response = restTemplate.postForEntity(
                "/api/tournaments", entity, TournamentResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TournamentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.name()).isEqualTo("Winter Major 2026");
        assertThat(body.status()).isEqualTo(TournamentStatus.UPCOMING);
        assertThat(body.participantCount()).isZero();
        assertThat(body.isRegistrationOpen()).isTrue();

        // Verify database persistence
        assertThat(tournamentRepository.findById(body.id())).isPresent();
    }

    @Test
    @DisplayName("POST /api/tournaments - should return 403 Forbidden when caller is CUSTOMER")
    void createTournament_shouldReturnForbidden_whenCustomer() {
        TournamentRequest request = new TournamentRequest(
                "Hacked Tournament",
                "Game",
                Instant.now().plus(5, ChronoUnit.DAYS),
                Instant.now().plus(6, ChronoUnit.DAYS),
                new BigDecimal("100.00"),
                16,
                Instant.now().plus(4, ChronoUnit.DAYS)
        );

        HttpEntity<TournamentRequest> entity = new HttpEntity<>(request, customerHeaders(customerUserId));
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/tournaments", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(tournamentRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST /api/tournaments - should return 400 Bad Request on validation failure")
    void createTournament_shouldReturnBadRequest_whenValidationFails() {
        TournamentRequest invalidRequest = new TournamentRequest(
                "", // Blank name
                "",
                Instant.now().minus(1, ChronoUnit.DAYS), // Past date
                null,
                new BigDecimal("-10.00"), // Negative fee
                1, // Less than 2 participants
                Instant.now().minus(2, ChronoUnit.DAYS)
        );

        HttpEntity<TournamentRequest> entity = new HttpEntity<>(invalidRequest, adminHeaders(adminUserId));
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/tournaments", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    // ── 2. GET /api/tournaments ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/tournaments - should permit guest browsing and return tournament list")
    void getAllTournaments_shouldReturnList_forGuest() {
        createSampleTournament("Tekken Open", TournamentStatus.UPCOMING, 16, 2);

        HttpEntity<Void> entity = new HttpEntity<>(guestHeaders());
        ResponseEntity<List<TournamentResponse>> response = restTemplate.exchange(
                "/api/tournaments",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<TournamentResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).name()).isEqualTo("Tekken Open");
        assertThat(response.getBody().get(0).isRegistrationOpen()).isTrue();
    }

    // ── 3. GET /api/tournaments/admin/stats ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/tournaments/admin/stats - should return stats for ADMIN")
    void getTournamentStats_shouldReturnStats_whenAdmin() {
        createSampleTournament("T1", TournamentStatus.UPCOMING, 16, 4);
        createSampleTournament("T2", TournamentStatus.ONGOING, 8, 8);
        createSampleTournament("T3", TournamentStatus.COMPLETED, 8, 8);

        HttpEntity<Void> entity = new HttpEntity<>(adminHeaders(adminUserId));
        ResponseEntity<AdminTournamentStatsResponse> response = restTemplate.exchange(
                "/api/tournaments/admin/stats",
                HttpMethod.GET,
                entity,
                AdminTournamentStatsResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AdminTournamentStatsResponse stats = response.getBody();
        assertThat(stats).isNotNull();
        assertThat(stats.totalTournaments()).isEqualTo(3L);
        assertThat(stats.upcomingTournaments()).isEqualTo(1L);
        assertThat(stats.ongoingTournaments()).isEqualTo(1L);
        assertThat(stats.completedTournaments()).isEqualTo(1L);
        assertThat(stats.totalParticipants()).isEqualTo(20L);
    }

    @Test
    @DisplayName("GET /api/tournaments/admin/stats - should return 403 Forbidden for CUSTOMER")
    void getTournamentStats_shouldReturnForbidden_whenCustomer() {
        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/tournaments/admin/stats",
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 4. GET /api/tournaments/{id} ───────────────────────────────────────────

    @Test
    @DisplayName("GET /api/tournaments/{id} - should return 200 OK when tournament exists")
    void getTournamentById_shouldReturnTournament_whenFound() {
        Tournament t = createSampleTournament("Smash Bros Melee", TournamentStatus.UPCOMING, 16, 0);

        HttpEntity<Void> entity = new HttpEntity<>(guestHeaders());
        ResponseEntity<TournamentResponse> response = restTemplate.exchange(
                "/api/tournaments/" + t.getId(),
                HttpMethod.GET,
                entity,
                TournamentResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Smash Bros Melee");
    }

    @Test
    @DisplayName("GET /api/tournaments/{id} - should return 404 Not Found when ID does not exist")
    void getTournamentById_shouldReturnNotFound_whenMissing() {
        UUID missingId = UUID.randomUUID();
        HttpEntity<Void> entity = new HttpEntity<>(guestHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/tournaments/" + missingId,
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("NOT_FOUND");
    }

    // ── 5. PATCH /api/tournaments/{id}/status ───────────────────────────────────

    @Test
    @DisplayName("PATCH /api/tournaments/{id}/status - should update status when ADMIN")
    void updateTournamentStatus_shouldUpdate_whenAdmin() {
        Tournament t = createSampleTournament("Status Test", TournamentStatus.UPCOMING, 16, 0);

        TournamentStatusUpdateRequest request = new TournamentStatusUpdateRequest(TournamentStatus.ONGOING);
        HttpEntity<TournamentStatusUpdateRequest> entity = new HttpEntity<>(request, adminHeaders(adminUserId));

        ResponseEntity<TournamentResponse> response = restTemplate.exchange(
                "/api/tournaments/" + t.getId() + "/status",
                HttpMethod.PATCH,
                entity,
                TournamentResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(TournamentStatus.ONGOING);

        // Verify in database
        Tournament updated = tournamentRepository.findById(t.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TournamentStatus.ONGOING);
    }

    @Test
    @DisplayName("PATCH /api/tournaments/{id}/status - should return 403 Forbidden when CUSTOMER")
    void updateTournamentStatus_shouldReturnForbidden_whenCustomer() {
        Tournament t = createSampleTournament("Status Test", TournamentStatus.UPCOMING, 16, 0);

        TournamentStatusUpdateRequest request = new TournamentStatusUpdateRequest(TournamentStatus.ONGOING);
        HttpEntity<TournamentStatusUpdateRequest> entity = new HttpEntity<>(request, customerHeaders(customerUserId));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/tournaments/" + t.getId() + "/status",
                HttpMethod.PATCH,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 6. POST /api/tournaments/{id}/cancel ───────────────────────────────────

    @Test
    @DisplayName("POST /api/tournaments/{id}/cancel - should cancel tournament and fan out notifications when ADMIN")
    void cancelTournament_shouldCancelAndFanOut_whenAdmin() {
        Tournament t = createSampleTournament("Cancel Test", TournamentStatus.UPCOMING, 16, 1);

        TournamentRegistration reg = TournamentRegistration.builder()
                .tournament(t)
                .userId(customerUserId)
                .userEmail("cust@test.com")
                .userName("Player")
                .status(RegistrationStatus.CONFIRMED)
                .build();
        registrationRepository.save(reg);

        HttpEntity<Void> entity = new HttpEntity<>(adminHeaders(adminUserId));
        ResponseEntity<TournamentResponse> response = restTemplate.postForEntity(
                "/api/tournaments/" + t.getId() + "/cancel",
                entity,
                TournamentResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(TournamentStatus.CANCELLED);

        // Verify database state
        Tournament cancelled = tournamentRepository.findById(t.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(TournamentStatus.CANCELLED);

        TournamentRegistration cancelledReg = registrationRepository.findById(reg.getId()).orElseThrow();
        assertThat(cancelledReg.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);

        // Verify notification fanout
        verify(notificationClient).sendTournamentCancelled(any());
    }

    // ── 7. POST /api/tournaments/{id}/register ──────────────────────────────────

    @Test
    @DisplayName("POST /api/tournaments/{id}/register - should register customer, charge payment, and increment count")
    void registerForTournament_shouldRegisterAndIncrement_whenSuccess() {
        Tournament t = createSampleTournament("Apex Cup", TournamentStatus.UPCOMING, 16, 0);

        UUID paymentId = UUID.randomUUID();
        when(paymentClient.chargeEntryFee(eq(t.getEntryFee()), eq(customerUserId), any())).thenReturn(paymentId);

        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));
        ResponseEntity<RegistrationResponse> response = restTemplate.postForEntity(
                "/api/tournaments/" + t.getId() + "/register",
                entity,
                RegistrationResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RegistrationResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(body.paymentId()).isEqualTo(paymentId);
        assertThat(body.userId()).isEqualTo(customerUserId);

        // Verify database state changes
        Tournament updated = tournamentRepository.findById(t.getId()).orElseThrow();
        assertThat(updated.getParticipantCount()).isEqualTo(1);

        List<TournamentRegistration> registrations = registrationRepository.findByTournamentId(t.getId());
        assertThat(registrations).hasSize(1);
        assertThat(registrations.get(0).getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.get(0).getPaymentId()).isEqualTo(paymentId);

        // Verify notification sent
        verify(notificationClient).sendTournamentRegistrationConfirmed(any());
    }

    @Test
    @DisplayName("POST /api/tournaments/{id}/register - should reject duplicate registration with 409 Conflict")
    void registerForTournament_shouldReturnConflict_whenAlreadyRegistered() {
        Tournament t = createSampleTournament("Apex Cup", TournamentStatus.UPCOMING, 16, 1);

        TournamentRegistration existingReg = TournamentRegistration.builder()
                .tournament(t)
                .userId(customerUserId)
                .status(RegistrationStatus.CONFIRMED)
                .build();
        registrationRepository.save(existingReg);

        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/tournaments/" + t.getId() + "/register",
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("DUPLICATE_REGISTRATION");
    }

    @Test
    @DisplayName("POST /api/tournaments/{id}/register - should reject with 409 Conflict when tournament is full")
    void registerForTournament_shouldReturnConflict_whenAtCapacity() {
        Tournament t = createSampleTournament("Full Tournament", TournamentStatus.UPCOMING, 2, 2);

        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/tournaments/" + t.getId() + "/register",
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("REGISTRATION_CLOSED");
    }

    @Test
    @DisplayName("POST /api/tournaments/{id}/register - should handle payment failure with 402 Payment Required")
    void registerForTournament_shouldReturnPaymentRequired_whenPaymentFails() {
        Tournament t = createSampleTournament("Paid Tournament", TournamentStatus.UPCOMING, 16, 0);

        when(paymentClient.chargeEntryFee(any(), eq(customerUserId), any()))
                .thenThrow(new PaymentFailedException("Card declined"));

        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/tournaments/" + t.getId() + "/register",
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_FAILED");

        // Verify registration in DB is marked CANCELLED
        List<TournamentRegistration> regs = registrationRepository.findByTournamentId(t.getId());
        assertThat(regs).hasSize(1);
        assertThat(regs.get(0).getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        // Participant count not incremented
        Tournament refreshed = tournamentRepository.findById(t.getId()).orElseThrow();
        assertThat(refreshed.getParticipantCount()).isZero();
    }

    // ── 8. GET /api/tournaments/{id}/registrations ─────────────────────────────

    @Test
    @DisplayName("GET /api/tournaments/{id}/registrations - should return registrations for ADMIN")
    void getRegistrations_shouldReturnList_whenAdmin() {
        Tournament t = createSampleTournament("Reg List Test", TournamentStatus.UPCOMING, 16, 1);

        TournamentRegistration reg = TournamentRegistration.builder()
                .tournament(t)
                .userId(customerUserId)
                .status(RegistrationStatus.CONFIRMED)
                .build();
        registrationRepository.save(reg);

        HttpEntity<Void> entity = new HttpEntity<>(adminHeaders(adminUserId));
        ResponseEntity<List<RegistrationResponse>> response = restTemplate.exchange(
                "/api/tournaments/" + t.getId() + "/registrations",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<RegistrationResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).userId()).isEqualTo(customerUserId);
    }

    @Test
    @DisplayName("GET /api/tournaments/{id}/registrations - should return 403 Forbidden for CUSTOMER")
    void getRegistrations_shouldReturnForbidden_whenCustomer() {
        Tournament t = createSampleTournament("Reg List Test", TournamentStatus.UPCOMING, 16, 0);

        HttpEntity<Void> entity = new HttpEntity<>(customerHeaders(customerUserId));
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/tournaments/" + t.getId() + "/registrations",
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 9. POST /api/tournaments/{id}/bracket/generate ─────────────────────────

    @Test
    @DisplayName("POST /api/tournaments/{id}/bracket/generate - should create bracket & matches in DB for ADMIN")
    void generateBracket_shouldGenerateMatches_whenAdmin() {
        Tournament t = createSampleTournament("Bracket Tourney", TournamentStatus.UPCOMING, 8, 2);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(t).userId(p1).status(RegistrationStatus.CONFIRMED).build());
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(t).userId(p2).status(RegistrationStatus.CONFIRMED).build());

        HttpEntity<Void> entity = new HttpEntity<>(adminHeaders(adminUserId));
        ResponseEntity<BracketResponse> response = restTemplate.postForEntity(
                "/api/tournaments/" + t.getId() + "/bracket/generate",
                entity,
                BracketResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BracketResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.matches()).hasSize(1);
        assertThat(body.matches().get(0).round()).isEqualTo(1);

        // Verify DB persistence
        assertThat(bracketRepository.findByTournamentId(t.getId())).isPresent();
        assertThat(matchRepository.findByBracketIdOrderByRoundAsc(body.id())).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/tournaments/{id}/bracket/generate - should return 409 Conflict when < 2 participants")
    void generateBracket_shouldReturnConflict_whenUnderTwoParticipants() {
        Tournament t = createSampleTournament("Solo Tourney", TournamentStatus.UPCOMING, 8, 1);
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(t).userId(UUID.randomUUID()).status(RegistrationStatus.CONFIRMED).build());

        HttpEntity<Void> entity = new HttpEntity<>(adminHeaders(adminUserId));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/tournaments/" + t.getId() + "/bracket/generate",
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_STATE");
    }

    // ── 10. GET /api/tournaments/{id}/bracket ──────────────────────────────────

    @Test
    @DisplayName("GET /api/tournaments/{id}/bracket - should return bracket and matches")
    void getBracket_shouldReturnBracket_whenFound() {
        Tournament t = createSampleTournament("Bracket View", TournamentStatus.UPCOMING, 8, 2);
        Bracket b = bracketRepository.save(Bracket.builder().tournamentId(t.getId()).build());
        matchRepository.save(Match.builder()
                .bracketId(b.getId()).round(1).participant1Id(UUID.randomUUID()).participant2Id(UUID.randomUUID())
                .status(MatchStatus.SCHEDULED).build());

        HttpEntity<Void> entity = new HttpEntity<>(guestHeaders());
        ResponseEntity<BracketResponse> response = restTemplate.exchange(
                "/api/tournaments/" + t.getId() + "/bracket",
                HttpMethod.GET,
                entity,
                BracketResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(b.getId());
        assertThat(response.getBody().matches()).hasSize(1);
    }

    // ── 11. PATCH /api/tournaments/{id}/bracket/matches/{matchId}/result ────────

    @Test
    @DisplayName("PATCH /api/tournaments/{id}/bracket/matches/{matchId}/result - should record winner and crown champion")
    void recordMatchResult_shouldRecordWinnerAndCrownChampion_whenAdmin() {
        Tournament t = createSampleTournament("Finals", TournamentStatus.UPCOMING, 2, 2);
        Bracket b = bracketRepository.save(Bracket.builder().tournamentId(t.getId()).build());

        UUID winnerId = customerUserId;
        UUID loserId = UUID.randomUUID();

        Match finalMatch = matchRepository.save(Match.builder()
                .bracketId(b.getId()).round(1).participant1Id(winnerId).participant2Id(loserId)
                .status(MatchStatus.SCHEDULED).build());

        registrationRepository.save(TournamentRegistration.builder()
                .tournament(t).userId(winnerId).userEmail("winner@test.com").status(RegistrationStatus.CONFIRMED).build());
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(t).userId(loserId).userEmail("loser@test.com").status(RegistrationStatus.CONFIRMED).build());

        when(userClient.getUserById(winnerId)).thenReturn(Optional.of(
                new UserClient.UserSummaryDto(winnerId, "winner", "winner@test.com", "The Champ", "CUSTOMER")
        ));

        MatchResultRequest request = new MatchResultRequest(winnerId);
        HttpEntity<MatchResultRequest> entity = new HttpEntity<>(request, adminHeaders(adminUserId));

        ResponseEntity<MatchResponse> response = restTemplate.exchange(
                "/api/tournaments/" + t.getId() + "/bracket/matches/" + finalMatch.getId() + "/result",
                HttpMethod.PATCH,
                entity,
                MatchResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().winnerId()).isEqualTo(winnerId);
        assertThat(response.getBody().status()).isEqualTo(MatchStatus.COMPLETED);

        // Verify tournament completed
        Tournament completedTournament = tournamentRepository.findById(t.getId()).orElseThrow();
        assertThat(completedTournament.getStatus()).isEqualTo(TournamentStatus.COMPLETED);

        // Verify results notification fanout
        verify(notificationClient, atLeastOnce()).sendTournamentResultsPublished(any());
    }

    // ── 12. POST /api/tournaments/{id}/notify-reminders ────────────────────────

    @Test
    @DisplayName("POST /api/tournaments/{id}/notify-reminders - should trigger reminders when ADMIN")
    void sendPreTournamentReminders_shouldReturnOk_whenAdmin() {
        Tournament t = createSampleTournament("Reminder Tourney", TournamentStatus.UPCOMING, 16, 0);

        HttpEntity<Void> entity = new HttpEntity<>(adminHeaders(adminUserId));
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/tournaments/" + t.getId() + "/notify-reminders",
                entity,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Tournament refreshed = tournamentRepository.findById(t.getId()).orElseThrow();
        assertThat(refreshed.getReminderSent()).isTrue();
    }

    // ── 13. Security and Gateway Secret Tests ─────────────────────────────────

    @Test
    @DisplayName("Security: should return 401 Unauthorized when X-Gateway-Secret header is missing")
    void security_shouldReturn401_whenGatewaySecretMissing() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", adminUserId.toString());
        headers.set("X-User-Role", "ADMIN");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/tournaments", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Security: should return 401 Unauthorized when X-Gateway-Secret is invalid")
    void security_shouldReturn401_whenGatewaySecretInvalid() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", "tampered-secret");
        headers.set("X-User-Id", adminUserId.toString());
        headers.set("X-User-Role", "ADMIN");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/tournaments", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Security: should return 401 Unauthorized when X-User-Id is malformed")
    void security_shouldReturn401_whenUserIdMalformed() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", GATEWAY_SECRET);
        headers.set("X-User-Id", "not-a-valid-uuid");
        headers.set("X-User-Role", "ADMIN");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/tournaments", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Security: should return 401 Unauthorized when X-User-Role is unsupported")
    void security_shouldReturn401_whenRoleUnsupported() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", GATEWAY_SECRET);
        headers.set("X-User-Id", adminUserId.toString());
        headers.set("X-User-Role", "MODERATOR");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/tournaments", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
