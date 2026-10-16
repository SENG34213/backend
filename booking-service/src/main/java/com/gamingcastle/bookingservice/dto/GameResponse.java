package com.gamingcastle.bookingservice.dto;

import com.gamingcastle.bookingservice.entity.GameTitle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameResponse(
    UUID id,
    String title,
    String genre,
    List<String> platforms,
    int releaseYear,
    String ageRating,
    String description,
    String coverUrl,
    int maxPlayers,
    boolean featured,
    boolean active,
    int installedStationCount,
    List<GameStationResponse> stations,
    Instant createdAt,
    Instant updatedAt
) {
    public GameResponse(
        UUID id,
        String title,
        String genre,
        List<String> platforms,
        int releaseYear,
        String ageRating,
        String description,
        String coverUrl,
        int maxPlayers,
        boolean featured,
        boolean active,
        int installedStationCount,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, title, genre, platforms, releaseYear, ageRating, description, coverUrl, maxPlayers, featured, active, installedStationCount, java.util.Collections.emptyList(), createdAt, updatedAt);
    }

    public static GameResponse from(GameTitle game, int stationCount) {
        List<GameStationResponse> stationList = game.getStations() != null
            ? game.getStations().stream().map(GameStationResponse::from).toList()
            : java.util.Collections.emptyList();
        return new GameResponse(
            game.getId(),
            game.getTitle(),
            game.getGenre(),
            game.getPlatformList(),
            game.getReleaseYear(),
            game.getAgeRating(),
            game.getDescription(),
            game.getCoverUrl(),
            game.getMaxPlayers(),
            game.isFeatured(),
            game.isActive(),
            stationCount,
            stationList,
            game.getCreatedAt(),
            game.getUpdatedAt()
        );
    }

    public static GameResponse from(GameTitle game) {
        int count = game.getStations() != null ? game.getStations().size() : 0;
        return from(game, count);
    }
}
