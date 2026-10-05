package com.gamingcastle.bookingservice.controller;

import com.gamingcastle.bookingservice.dto.GameStationRequest;
import com.gamingcastle.bookingservice.dto.GameStationResponse;
import com.gamingcastle.bookingservice.service.GameStationService;
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
@RequestMapping("/api/stations")
public class GameStationController {

    private static final Logger log =
            LoggerFactory.getLogger(GameStationController.class);

    private final GameStationService gameStationService;

    public GameStationController(GameStationService gameStationService) {
        this.gameStationService = gameStationService;
    }

    /** List all stations. */
    @GetMapping
    public ResponseEntity<List<GameStationResponse>> getAllStations() {

        log.info("Fetching all game stations");

        List<GameStationResponse> stations =
                gameStationService.getAllStations();

        log.info("Successfully fetched {} game stations", stations.size());

        return ResponseEntity.ok(stations);
    }

    /** Get a single station by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<GameStationResponse> getStation(
            @PathVariable UUID id) {

        log.info("Fetching game station with ID: {}", id);

        GameStationResponse station =
                gameStationService.getStationById(id);

        log.info("Successfully fetched game station with ID: {}", id);

        return ResponseEntity.ok(station);
    }

    /** Create a new game station — ADMIN only. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<GameStationResponse> createStation(
            @Valid @RequestBody GameStationRequest request) {

        log.info("Creating a new game station");

        GameStationResponse created =
                gameStationService.createStation(request);

        log.info("Game station created successfully");

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(created);
    }

    /** Update an existing game station — ADMIN only. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<GameStationResponse> updateStation(
            @PathVariable UUID id,
            @Valid @RequestBody GameStationRequest request) {

        log.info("Updating game station with ID: {}", id);

        GameStationResponse updated =
                gameStationService.updateStation(id, request);

        log.info("Game station with ID {} updated successfully", id);

        return ResponseEntity.ok(updated);
    }

    /** Soft-delete a station — ADMIN only. */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStation(
            @PathVariable UUID id) {

        log.info("Deleting game station with ID: {}", id);

        gameStationService.deleteStation(id);

        log.info("Game station with ID {} deleted successfully", id);

        return ResponseEntity.noContent().build();
    }
}
