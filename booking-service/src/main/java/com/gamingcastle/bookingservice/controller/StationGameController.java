package com.gamingcastle.bookingservice.controller;

import com.gamingcastle.bookingservice.dto.ConfigureStationGamesRequest;
import com.gamingcastle.bookingservice.dto.GameResponse;
import com.gamingcastle.bookingservice.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stations/{stationId}/games")
@Tag(name = "Station Games Configuration", description = "Configure and manage games installed on gaming stations")
public class StationGameController {

    private static final Logger log = LoggerFactory.getLogger(StationGameController.class);

    private final GameService gameService;

    public StationGameController(GameService gameService) {
        this.gameService = gameService;
    }

    @Operation(summary = "Get all games installed on a given station")
    @GetMapping
    public ResponseEntity<List<GameResponse>> getGamesForStation(@PathVariable UUID stationId) {
        log.info("Fetching installed games for station ID: {}", stationId);
        List<GameResponse> games = gameService.getGamesForStation(stationId);
        return ResponseEntity.ok(games);
    }

    @Operation(summary = "Configure/assign games to a station — ADMIN only")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<List<GameResponse>> assignGamesToStation(
            @PathVariable UUID stationId,
            @Valid @RequestBody ConfigureStationGamesRequest request) {
        log.info("Admin assigning {} games to station ID: {}", request.gameIds().size(), stationId);
        List<GameResponse> assigned = gameService.assignGamesToStation(stationId, request.gameIds());
        return ResponseEntity.ok(assigned);
    }

    @Operation(summary = "Remove a game from a station — ADMIN only")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{gameId}")
    public ResponseEntity<Void> removeGameFromStation(
            @PathVariable UUID stationId,
            @PathVariable UUID gameId) {
        log.info("Admin removing game ID: {} from station ID: {}", gameId, stationId);
        gameService.removeGameFromStation(stationId, gameId);
        return ResponseEntity.noContent().build();
    }
}
