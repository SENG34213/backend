package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.GameRequest;
import com.gamingcastle.bookingservice.dto.GameResponse;
import com.gamingcastle.bookingservice.dto.GameStationResponse;
import com.gamingcastle.bookingservice.dto.GameStatsResponse;

import java.util.List;
import java.util.UUID;

public interface GameService {

    List<GameResponse> getAllGames(String search, String genre, String platform, Boolean active, Boolean featured);

    GameResponse getGameById(UUID id);

    GameResponse createGame(GameRequest request);

    GameResponse updateGame(UUID id, GameRequest request);

    void deleteGame(UUID id);

    GameResponse toggleActive(UUID id);

    GameStatsResponse getGameStats();

    // ── Station - Game Configuration ──────────────────────────────────────────

    List<GameResponse> getGamesForStation(UUID stationId);

    List<GameResponse> assignGamesToStation(UUID stationId, List<UUID> gameIds);

    void removeGameFromStation(UUID stationId, UUID gameId);

    List<GameStationResponse> getStationsForGame(UUID gameId);
    List<GameStationResponse> updateStationsForGame(UUID gameId, List<UUID> stationIds);
}
