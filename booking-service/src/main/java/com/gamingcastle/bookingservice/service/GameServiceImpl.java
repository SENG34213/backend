package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.GameRequest;
import com.gamingcastle.bookingservice.dto.GameResponse;
import com.gamingcastle.bookingservice.dto.GameStationResponse;
import com.gamingcastle.bookingservice.dto.GameStatsResponse;
import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.entity.GameTitle;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.repository.GameRepository;
import com.gamingcastle.bookingservice.repository.GameStationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;
    private final GameStationRepository gameStationRepository;

    public GameServiceImpl(GameRepository gameRepository, GameStationRepository gameStationRepository) {
        this.gameRepository = gameRepository;
        this.gameStationRepository = gameStationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GameResponse> getAllGames(String search, String genre, String platform, Boolean active, Boolean featured) {
        List<GameTitle> all = gameRepository.findAll();

        return all.stream()
                .filter(g -> {
                    if (search != null && !search.isBlank()) {
                        String q = search.trim().toLowerCase(Locale.ROOT);
                        boolean matchesTitle = g.getTitle().toLowerCase(Locale.ROOT).contains(q);
                        boolean matchesGenre = g.getGenre().toLowerCase(Locale.ROOT).contains(q);
                        boolean matchesDesc = g.getDescription() != null && g.getDescription().toLowerCase(Locale.ROOT).contains(q);
                        if (!matchesTitle && !matchesGenre && !matchesDesc) return false;
                    }
                    if (genre != null && !genre.isBlank() && !genre.equalsIgnoreCase("ALL")) {
                        if (!g.getGenre().equalsIgnoreCase(genre.trim())) return false;
                    }
                    if (platform != null && !platform.isBlank() && !platform.equalsIgnoreCase("ALL")) {
                        boolean hasPlatform = g.getPlatformList().stream()
                                .anyMatch(p -> p.equalsIgnoreCase(platform.trim()));
                        if (!hasPlatform) return false;
                    }
                    if (active != null) {
                        if (g.isActive() != active) return false;
                    }
                    if (featured != null) {
                        if (g.isFeatured() != featured) return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparing(GameTitle::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(GameResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public GameResponse getGameById(UUID id) {
        GameTitle game = findOrThrow(id);
        return GameResponse.from(game);
    }

    @Override
    @Transactional
    public GameResponse createGame(GameRequest request) {
        gameRepository.findByTitleIgnoreCase(request.title().trim()).ifPresent(existing -> {
            throw new BookingException(HttpStatus.CONFLICT, "GAME_TITLE_EXISTS",
                    "A game with title '" + request.title() + "' already exists");
        });

        GameTitle game = GameTitle.builder()
                .title(request.title().trim())
                .genre(request.genre().trim().toUpperCase(Locale.ROOT))
                .platforms(request.platforms() != null ? String.join(",", request.platforms()) : "")
                .releaseYear(request.releaseYear())
                .ageRating(request.ageRating().trim().toUpperCase(Locale.ROOT))
                .description(request.description() != null ? request.description().trim() : "")
                .coverUrl(request.coverUrl() != null ? request.coverUrl().trim() : "")
                .maxPlayers(request.maxPlayers() > 0 ? request.maxPlayers() : 1)
                .featured(Boolean.TRUE.equals(request.featured()))
                .active(request.active() == null || request.active())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        if (request.stationIds() != null && !request.stationIds().isEmpty()) {
            List<GameStation> stations = gameStationRepository.findAllById(request.stationIds());
            game.setStations(new HashSet<>(stations));
        }

        GameTitle saved = gameRepository.save(game);
        return GameResponse.from(saved);
    }

    @Override
    @Transactional
    public GameResponse updateGame(UUID id, GameRequest request) {
        GameTitle game = findOrThrow(id);

        if (!game.getTitle().equalsIgnoreCase(request.title().trim())) {
            gameRepository.findByTitleIgnoreCase(request.title().trim()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BookingException(HttpStatus.CONFLICT, "GAME_TITLE_EXISTS",
                            "A game with title '" + request.title() + "' already exists");
                }
            });
        }

        game.setTitle(request.title().trim());
        game.setGenre(request.genre().trim().toUpperCase(Locale.ROOT));
        if (request.platforms() != null) {
            game.setPlatformList(request.platforms());
        }
        game.setReleaseYear(request.releaseYear());
        game.setAgeRating(request.ageRating().trim().toUpperCase(Locale.ROOT));
        if (request.description() != null) {
            game.setDescription(request.description().trim());
        }
        if (request.coverUrl() != null) {
            game.setCoverUrl(request.coverUrl().trim());
        }
        if (request.maxPlayers() > 0) {
            game.setMaxPlayers(request.maxPlayers());
        }
        if (request.featured() != null) {
            game.setFeatured(request.featured());
        }
        if (request.active() != null) {
            game.setActive(request.active());
        }
        if (request.stationIds() != null) {
            List<GameStation> stations = gameStationRepository.findAllById(request.stationIds());
            game.setStations(new HashSet<>(stations));
        }
        game.setUpdatedAt(Instant.now());

        GameTitle updated = gameRepository.save(game);
        return GameResponse.from(updated);
    }

    @Override
    @Transactional
    public void deleteGame(UUID id) {
        GameTitle game = findOrThrow(id);
        gameRepository.delete(game);
    }

    @Override
    @Transactional
    public GameResponse toggleActive(UUID id) {
        GameTitle game = findOrThrow(id);
        game.setActive(!game.isActive());
        game.setUpdatedAt(Instant.now());
        GameTitle saved = gameRepository.save(game);
        return GameResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public GameStatsResponse getGameStats() {
        long totalGames = gameRepository.count();
        long activeGames = gameRepository.countByActive(true);
        long featuredGames = gameRepository.countByFeatured(true);
        long multiplayerGames = gameRepository.countByMaxPlayersGreaterThan(1);

        String topGenre = "ACTION";
        List<Object[]> genres = gameRepository.findTopGenres();
        if (genres != null && !genres.isEmpty() && genres.get(0).length > 0) {
            topGenre = String.valueOf(genres.get(0)[0]);
        }

        return new GameStatsResponse(
                totalGames,
                activeGames,
                featuredGames,
                topGenre,
                multiplayerGames
        );
    }

    // ── Station - Game Configuration ──────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<GameResponse> getGamesForStation(UUID stationId) {
        findStationOrThrow(stationId);
        List<GameTitle> games = gameRepository.findByStationId(stationId);
        return games.stream().map(GameResponse::from).toList();
    }

    @Override
    @Transactional
    public List<GameResponse> assignGamesToStation(UUID stationId, List<UUID> gameIds) {
        GameStation station = findStationOrThrow(stationId);

        // Remove station from any currently assigned games that are not in the new list
        List<GameTitle> currentGames = gameRepository.findByStationId(stationId);
        for (GameTitle current : currentGames) {
            if (!gameIds.contains(current.getId())) {
                current.getStations().remove(station);
                gameRepository.save(current);
            }
        }

        // Add station to newly assigned games
        List<GameTitle> gamesToAssign = gameRepository.findAllById(gameIds);
        for (GameTitle game : gamesToAssign) {
            if (!game.getStations().contains(station)) {
                game.getStations().add(station);
                gameRepository.save(game);
            }
        }

        return getGamesForStation(stationId);
    }

    @Override
    @Transactional
    public void removeGameFromStation(UUID stationId, UUID gameId) {
        GameStation station = findStationOrThrow(stationId);
        GameTitle game = findOrThrow(gameId);

        if (game.getStations().contains(station)) {
            game.getStations().remove(station);
            gameRepository.save(game);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<GameStationResponse> getStationsForGame(UUID gameId) {
        findOrThrow(gameId);
        List<GameStation> stations = gameRepository.findStationsByGameId(gameId);
        return stations.stream().map(GameStationResponse::from).toList();
    }

    @Override
    @Transactional
    public List<GameStationResponse> updateStationsForGame(UUID gameId, List<UUID> stationIds) {
        GameTitle game = findOrThrow(gameId);
        List<GameStation> stations = (stationIds != null && !stationIds.isEmpty())
                ? gameStationRepository.findAllById(stationIds)
                : Collections.emptyList();
        game.setStations(new HashSet<>(stations));
        gameRepository.save(game);
        return stations.stream().map(GameStationResponse::from).toList();
    }

    private GameTitle findOrThrow(UUID id) {
        return gameRepository.findById(id).orElseThrow(() ->
                new BookingException(HttpStatus.NOT_FOUND, "GAME_NOT_FOUND",
                        "Game with ID '" + id + "' not found"));
    }

    private GameStation findStationOrThrow(UUID id) {
        return gameStationRepository.findById(id).orElseThrow(() ->
                new BookingException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND",
                        "Game station with ID '" + id + "' not found"));
    }
}
