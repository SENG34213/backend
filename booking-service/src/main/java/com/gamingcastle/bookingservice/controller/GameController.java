package com.gamingcastle.bookingservice.controller;

import com.gamingcastle.bookingservice.dto.GameRequest;
import com.gamingcastle.bookingservice.dto.GameResponse;
import com.gamingcastle.bookingservice.dto.GameStationResponse;
import com.gamingcastle.bookingservice.dto.GameStatsResponse;
import com.gamingcastle.bookingservice.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/games", "/api/admin/games"})
@Tag(name = "Game Titles", description = "Game titles library, catalog filtering, and station assignment")
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @Operation(summary = "Get all game titles with optional search, genre, platform and status filters")
    @GetMapping
    public ResponseEntity<List<GameResponse>> getAllGames(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean featured) {

        log.info("Fetching game titles with search='{}', genre='{}', platform='{}', active={}, featured={}",
                search, genre, platform, active, featured);

        List<GameResponse> games = gameService.getAllGames(search, genre, platform, active, featured);
        return ResponseEntity.ok(games);
    }

    @Operation(summary = "Get aggregate statistics for games library")
    @GetMapping("/stats")
    public ResponseEntity<GameStatsResponse> getGameStats() {
        log.info("Fetching game catalog statistics");
        return ResponseEntity.ok(gameService.getGameStats());
    }

    @Operation(summary = "Get a single game title by ID")
    @GetMapping("/{id}")
    public ResponseEntity<GameResponse> getGameById(@PathVariable UUID id) {
        log.info("Fetching game title with ID: {}", id);
        return ResponseEntity.ok(gameService.getGameById(id));
    }

    @Operation(summary = "Create a new game title — ADMIN only")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<GameResponse> createGame(@Valid @RequestBody GameRequest request) {
        log.info("Admin creating new game title: '{}'", request.title());
        GameResponse created = gameService.createGame(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Update an existing game title — ADMIN only")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<GameResponse> updateGame(
            @PathVariable UUID id,
            @Valid @RequestBody GameRequest request) {
        log.info("Admin updating game title ID: {}", id);
        return ResponseEntity.ok(gameService.updateGame(id, request));
    }

    @Operation(summary = "Delete a game title — ADMIN only")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGame(@PathVariable UUID id) {
        log.info("Admin deleting game title ID: {}", id);
        gameService.deleteGame(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Toggle game title active status — ADMIN only")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<GameResponse> toggleActive(@PathVariable UUID id) {
        log.info("Admin toggling active state for game ID: {}", id);
        return ResponseEntity.ok(gameService.toggleActive(id));
    }

    @Operation(summary = "Get all stations where this game is installed")
    @GetMapping("/{id}/stations")
    public ResponseEntity<List<GameStationResponse>> getStationsForGame(@PathVariable UUID id) {
        log.info("Fetching stations equipped with game ID: {}", id);
        return ResponseEntity.ok(gameService.getStationsForGame(id));
    }

    @Operation(summary = "Update stations where this game is installed — ADMIN only")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/stations")
    public ResponseEntity<List<GameStationResponse>> updateStationsForGame(
            @PathVariable UUID id,
            @RequestBody List<UUID> stationIds) {
        log.info("Admin updating stations for game ID: {}", id);
        return ResponseEntity.ok(gameService.updateStationsForGame(id, stationIds));
    }
}
