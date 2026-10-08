package com.gamingcastle.tournamentservice.controller;

import com.gamingcastle.tournamentservice.dto.BracketResponse;
import com.gamingcastle.tournamentservice.dto.MatchResponse;
import com.gamingcastle.tournamentservice.dto.MatchResultRequest;
import com.gamingcastle.tournamentservice.dto.RegistrationResponse;
import com.gamingcastle.tournamentservice.dto.TournamentRequest;
import com.gamingcastle.tournamentservice.dto.TournamentResponse;
import com.gamingcastle.tournamentservice.dto.TournamentStatusUpdateRequest;
import com.gamingcastle.tournamentservice.service.BracketService;
import com.gamingcastle.tournamentservice.service.RegistrationService;
import com.gamingcastle.tournamentservice.service.TournamentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;
    private final RegistrationService registrationService;
    private final BracketService bracketService;

    public TournamentController(
            TournamentService tournamentService,
            RegistrationService registrationService,
            BracketService bracketService
    ) {
        this.tournamentService = tournamentService;
        this.registrationService = registrationService;
        this.bracketService = bracketService;
    }

    /** FR-13: Admin creates and publishes a tournament. */
    @PostMapping
    public ResponseEntity<TournamentResponse> createTournament(
            @Valid @RequestBody TournamentRequest request,
            @RequestHeader("X-User-Id") UUID adminId
    ) {
        TournamentResponse response = tournamentService.createAndPublishTournament(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** FR-14: Any authenticated user browses upcoming tournaments. */
    @GetMapping
    public ResponseEntity<List<TournamentResponse>> getAllTournaments() {
        return ResponseEntity.ok(tournamentService.getAllTournaments());
    }

    /** FR-14: Any authenticated user views a single tournament. */
    @GetMapping("/{id}")
    public ResponseEntity<TournamentResponse> getTournamentById(@PathVariable UUID id) {
        return ResponseEntity.ok(tournamentService.getTournamentById(id));
    }

    /** FR-17: Admin updates tournament status (UPCOMING, ONGOING, COMPLETED). */
    @PatchMapping("/{id}/status")
    public ResponseEntity<TournamentResponse> updateTournamentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TournamentStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(tournamentService.updateTournamentStatus(id, request));
    }

    /** UC-09 alt flow: Admin cancels a tournament — all registered participants notified. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TournamentResponse> cancelTournament(@PathVariable UUID id) {
        return ResponseEntity.ok(tournamentService.cancelTournament(id));
    }

    /**
     * FR-14, UC-08: Customer registers for a tournament.
     * Checks BR-06 (deadline + capacity), creates PENDING_PAYMENT, processes entry fee,
     * confirms registration and increments participantCount.
     */
    @PostMapping("/{id}/register")
    public ResponseEntity<RegistrationResponse> registerForTournament(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-FullName", required = false) String fullName
    ) {
        RegistrationResponse response = registrationService.registerForTournament(id, userId, email, fullName);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** FR-15, UC-09 step 4: Admin monitors registrations in real time. */
    @GetMapping("/{id}/registrations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RegistrationResponse>> getRegistrations(@PathVariable UUID id) {
        return ResponseEntity.ok(registrationService.getRegistrationsForTournament(id));
    }

    /** FR-15/FR-18: Admin generates the single-elimination bracket once registration closes. */
    @PostMapping("/{id}/bracket/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BracketResponse> generateBracket(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bracketService.generateBracket(id));
    }

    /** FR-15/FR-16: View the full bracket for a tournament (all rounds, all matches). */
    @GetMapping("/{id}/bracket")
    public ResponseEntity<BracketResponse> getBracket(@PathVariable UUID id) {
        return ResponseEntity.ok(bracketService.getBracket(id));
    }

    /** FR-15/FR-18: Admin records the winner of a specific match, auto-advances the bracket. */
    @PatchMapping("/{id}/bracket/matches/{matchId}/result")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MatchResponse> recordMatchResult(
            @PathVariable UUID id,
            @PathVariable UUID matchId,
            @Valid @RequestBody MatchResultRequest request
    ) {
        return ResponseEntity.ok(bracketService.recordMatchResult(id, matchId, request));
    }
}
