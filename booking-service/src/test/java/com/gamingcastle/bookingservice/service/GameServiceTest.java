package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.GameRequest;
import com.gamingcastle.bookingservice.dto.GameResponse;
import com.gamingcastle.bookingservice.dto.GameStationResponse;
import com.gamingcastle.bookingservice.dto.GameStatsResponse;
import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.entity.GameTitle;
import com.gamingcastle.bookingservice.entity.StationType;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.repository.GameRepository;
import com.gamingcastle.bookingservice.repository.GameStationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameStationRepository gameStationRepository;

    private GameService gameService;

    private UUID gameId;
    private GameTitle gameTitle;
    private UUID stationId;
    private GameStation station;

    @BeforeEach
    void setUp() {
        gameService = new GameServiceImpl(gameRepository, gameStationRepository);
        gameId = UUID.randomUUID();
        stationId = UUID.randomUUID();

        station = GameStation.builder()
                .id(stationId)
                .stationCode("PS5-01")
                .type(StationType.CONSOLE)
                .hourlyRate(BigDecimal.valueOf(500))
                .active(true)
                .build();

        Set<GameStation> stations = new HashSet<>();
        stations.add(station);

        gameTitle = GameTitle.builder()
                .id(gameId)
                .title("Elden Ring")
                .genre("ACTION_RPG")
                .platforms("PC,PS5")
                .releaseYear(2022)
                .ageRating("MATURE")
                .description("Action role-playing game set in the Lands Between")
                .coverUrl("https://example.com/elden-ring.jpg")
                .maxPlayers(4)
                .featured(true)
                .active(true)
                .createdAt(Instant.now().minusSeconds(3600))
                .updatedAt(Instant.now())
                .stations(stations)
                .build();
    }

    // ── getAllGames ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllGames: returns all games sorted by createdAt desc when no filters provided")
    void getAllGames_shouldReturnAllGames_whenNoFilters() {
        GameTitle older = GameTitle.builder()
                .id(UUID.randomUUID())
                .title("Dark Souls")
                .genre("ACTION_RPG")
                .platforms("PC")
                .releaseYear(2011)
                .ageRating("MATURE")
                .createdAt(Instant.now().minusSeconds(7200))
                .stations(new HashSet<>())
                .build();

        GameTitle withNullCreatedAt = GameTitle.builder()
                .id(UUID.randomUUID())
                .title("No Date Game")
                .genre("PUZZLE")
                .platforms("PC")
                .releaseYear(2020)
                .ageRating("EVERYONE")
                .createdAt(null)
                .stations(new HashSet<>())
                .build();

        when(gameRepository.findAll()).thenReturn(List.of(older, gameTitle, withNullCreatedAt));

        List<GameResponse> result = gameService.getAllGames(null, null, null, null, null);

        assertThat(result).hasSize(3);
        // newer first, then older, then null createdAt last
        assertThat(result.get(0).title()).isEqualTo("Elden Ring");
        assertThat(result.get(1).title()).isEqualTo("Dark Souls");
        assertThat(result.get(2).title()).isEqualTo("No Date Game");
    }

    @Test
    @DisplayName("getAllGames: filters by search query across title, genre, and description")
    void getAllGames_shouldFilterBySearch() {
        GameTitle g2 = GameTitle.builder()
                .id(UUID.randomUUID())
                .title("FIFA 23")
                .genre("SPORTS")
                .platforms("PS5")
                .releaseYear(2023)
                .ageRating("EVERYONE")
                .description("Football simulation")
                .stations(new HashSet<>())
                .build();

        GameTitle g3NullDesc = GameTitle.builder()
                .id(UUID.randomUUID())
                .title("Mystery Game")
                .genre("ADVENTURE")
                .platforms("PC")
                .releaseYear(2021)
                .ageRating("TEEN")
                .description(null)
                .stations(new HashSet<>())
                .build();

        when(gameRepository.findAll()).thenReturn(List.of(gameTitle, g2, g3NullDesc));

        // Matches title
        List<GameResponse> titleMatch = gameService.getAllGames("elden", null, null, null, null);
        assertThat(titleMatch).hasSize(1);
        assertThat(titleMatch.get(0).title()).isEqualTo("Elden Ring");

        // Matches genre
        List<GameResponse> genreMatch = gameService.getAllGames("sports", null, null, null, null);
        assertThat(genreMatch).hasSize(1);
        assertThat(genreMatch.get(0).title()).isEqualTo("FIFA 23");

        // Matches description
        List<GameResponse> descMatch = gameService.getAllGames("lands between", null, null, null, null);
        assertThat(descMatch).hasSize(1);
        assertThat(descMatch.get(0).title()).isEqualTo("Elden Ring");

        // Blank search returns all
        List<GameResponse> blankSearch = gameService.getAllGames("   ", null, null, null, null);
        assertThat(blankSearch).hasSize(3);

        // No match returns empty
        List<GameResponse> noMatch = gameService.getAllGames("NonExistentXYZ", null, null, null, null);
        assertThat(noMatch).isEmpty();
    }

    @Test
    @DisplayName("getAllGames: filters by genre, skipping when genre is ALL or blank")
    void getAllGames_shouldFilterByGenre() {
        GameTitle g2 = GameTitle.builder()
                .id(UUID.randomUUID())
                .title("FIFA 23")
                .genre("SPORTS")
                .stations(new HashSet<>())
                .build();

        when(gameRepository.findAll()).thenReturn(List.of(gameTitle, g2));

        List<GameResponse> match = gameService.getAllGames(null, "sports", null, null, null);
        assertThat(match).hasSize(1);
        assertThat(match.get(0).title()).isEqualTo("FIFA 23");

        // "ALL" ignores filter
        List<GameResponse> allGenre = gameService.getAllGames(null, "ALL", null, null, null);
        assertThat(allGenre).hasSize(2);

        // Blank ignores filter
        List<GameResponse> blankGenre = gameService.getAllGames(null, "   ", null, null, null);
        assertThat(blankGenre).hasSize(2);
    }

    @Test
    @DisplayName("getAllGames: filters by platform, skipping when platform is ALL or blank")
    void getAllGames_shouldFilterByPlatform() {
        GameTitle g2 = GameTitle.builder()
                .id(UUID.randomUUID())
                .title("Gran Turismo")
                .platforms("PS5")
                .stations(new HashSet<>())
                .build();

        GameTitle g3 = GameTitle.builder()
                .id(UUID.randomUUID())
                .title("Halo")
                .platforms("XBOX_SERIES_X")
                .stations(new HashSet<>())
                .build();

        when(gameRepository.findAll()).thenReturn(List.of(gameTitle, g2, g3));

        List<GameResponse> pcGames = gameService.getAllGames(null, null, "pc", null, null);
        assertThat(pcGames).hasSize(1);
        assertThat(pcGames.get(0).title()).isEqualTo("Elden Ring");

        // "ALL" ignores filter
        List<GameResponse> allPlatforms = gameService.getAllGames(null, null, "ALL", null, null);
        assertThat(allPlatforms).hasSize(3);

        // Blank ignores filter
        List<GameResponse> blankPlatform = gameService.getAllGames(null, null, "  ", null, null);
        assertThat(blankPlatform).hasSize(3);
    }

    @Test
    @DisplayName("getAllGames: filters by active and featured flags")
    void getAllGames_shouldFilterByActiveAndFeatured() {
        GameTitle inactive = GameTitle.builder()
                .id(UUID.randomUUID())
                .title("Old Title")
                .active(false)
                .featured(false)
                .stations(new HashSet<>())
                .build();

        when(gameRepository.findAll()).thenReturn(List.of(gameTitle, inactive));

        // Active only
        List<GameResponse> activeOnly = gameService.getAllGames(null, null, null, true, null);
        assertThat(activeOnly).hasSize(1);
        assertThat(activeOnly.get(0).title()).isEqualTo("Elden Ring");

        // Inactive only
        List<GameResponse> inactiveOnly = gameService.getAllGames(null, null, null, false, null);
        assertThat(inactiveOnly).hasSize(1);
        assertThat(inactiveOnly.get(0).title()).isEqualTo("Old Title");

        // Featured only
        List<GameResponse> featuredOnly = gameService.getAllGames(null, null, null, null, true);
        assertThat(featuredOnly).hasSize(1);
        assertThat(featuredOnly.get(0).title()).isEqualTo("Elden Ring");

        // Non-featured only
        List<GameResponse> nonFeaturedOnly = gameService.getAllGames(null, null, null, null, false);
        assertThat(nonFeaturedOnly).hasSize(1);
        assertThat(nonFeaturedOnly.get(0).title()).isEqualTo("Old Title");
    }

    // ── getGameById ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getGameById: returns GameResponse when game exists")
    void getGameById_shouldReturnResponse_whenGameExists() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameTitle));

        GameResponse response = gameService.getGameById(gameId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(gameId);
        assertThat(response.title()).isEqualTo("Elden Ring");
        assertThat(response.installedStationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getGameById: throws NOT_FOUND when game does not exist")
    void getGameById_shouldThrowNotFound_whenGameDoesNotExist() {
        UUID unknownId = UUID.randomUUID();
        when(gameRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.getGameById(unknownId))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getErrorCode()).isEqualTo("GAME_NOT_FOUND");
                });
    }

    // ── createGame ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createGame: successfully creates game when title is unique")
    void createGame_shouldSucceed_whenTitleUnique() {
        GameRequest request = new GameRequest(
                "Cyberpunk 2077",
                "ACTION_RPG",
                List.of("PC", "PS5"),
                2020,
                "MATURE",
                "Open world action RPG",
                "https://example.com/cyberpunk.jpg",
                1,
                true,
                true,
                List.of(stationId)
        );

        when(gameRepository.findByTitleIgnoreCase("Cyberpunk 2077")).thenReturn(Optional.empty());
        when(gameStationRepository.findAllById(List.of(stationId))).thenReturn(List.of(station));
        when(gameRepository.save(any(GameTitle.class))).thenAnswer(inv -> {
            GameTitle g = inv.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });

        GameResponse response = gameService.createGame(request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Cyberpunk 2077");
        assertThat(response.genre()).isEqualTo("ACTION_RPG");
        assertThat(response.platforms()).containsExactly("PC", "PS5");
        assertThat(response.featured()).isTrue();
        assertThat(response.active()).isTrue();
        verify(gameRepository).save(any(GameTitle.class));
    }

    @Test
    @DisplayName("createGame: applies default values when optional fields are null or empty")
    void createGame_shouldApplyDefaults_whenOptionalFieldsNullOrEmpty() {
        GameRequest request = new GameRequest(
                "Simple Game",
                "CASUAL",
                null,
                2021,
                "EVERYONE",
                null,
                null,
                0, // <= 0 defaults to 1
                null,
                null,
                null
        );

        when(gameRepository.findByTitleIgnoreCase("Simple Game")).thenReturn(Optional.empty());
        when(gameRepository.save(any(GameTitle.class))).thenAnswer(inv -> {
            GameTitle g = inv.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });

        GameResponse response = gameService.createGame(request);

        assertThat(response).isNotNull();
        assertThat(response.maxPlayers()).isEqualTo(1);
        assertThat(response.featured()).isFalse();
        assertThat(response.active()).isTrue();
        assertThat(response.description()).isEmpty();
        assertThat(response.coverUrl()).isEmpty();
        verify(gameStationRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("createGame: throws CONFLICT when game title already exists")
    void createGame_shouldThrowConflict_whenTitleAlreadyExists() {
        GameRequest request = new GameRequest(
                "Elden Ring",
                "ACTION_RPG",
                List.of("PC"),
                2022,
                "MATURE",
                null,
                null,
                1,
                false,
                true
        );

        when(gameRepository.findByTitleIgnoreCase("Elden Ring")).thenReturn(Optional.of(gameTitle));

        assertThatThrownBy(() -> gameService.createGame(request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("GAME_TITLE_EXISTS");
                });

        verify(gameRepository, never()).save(any());
    }

    // ── updateGame ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateGame: successfully updates existing game when title unchanged")
    void updateGame_shouldSucceed_whenTitleUnchanged() {
        GameRequest updateReq = new GameRequest(
                "Elden Ring", // unchanged
                "ACTION_RPG",
                List.of("PC", "PS5", "XBOX_SERIES_X"),
                2022,
                "MATURE",
                "Updated description",
                "https://example.com/updated.jpg",
                6,
                false,
                true,
                List.of(stationId)
        );

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameTitle));
        when(gameStationRepository.findAllById(List.of(stationId))).thenReturn(List.of(station));
        when(gameRepository.save(any(GameTitle.class))).thenAnswer(inv -> inv.getArgument(0));

        GameResponse updated = gameService.updateGame(gameId, updateReq);

        assertThat(updated.title()).isEqualTo("Elden Ring");
        assertThat(updated.description()).isEqualTo("Updated description");
        assertThat(updated.maxPlayers()).isEqualTo(6);
        assertThat(updated.featured()).isFalse();
        assertThat(updated.platforms()).containsExactly("PC", "PS5", "XBOX_SERIES_X");
        verify(gameRepository, never()).findByTitleIgnoreCase(any());
        verify(gameRepository).save(gameTitle);
    }

    @Test
    @DisplayName("updateGame: successfully updates when title changed to a new unique title")
    void updateGame_shouldSucceed_whenTitleChangedToUnique() {
        GameRequest updateReq = new GameRequest(
                "Elden Ring: Shadow of the Erdtree",
                "ACTION_RPG",
                List.of("PC"),
                2024,
                "MATURE",
                "Expansion",
                "https://example.com/erdtree.jpg",
                4,
                true,
                true,
                null
        );

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameTitle));
        when(gameRepository.findByTitleIgnoreCase("Elden Ring: Shadow of the Erdtree")).thenReturn(Optional.empty());
        when(gameRepository.save(any(GameTitle.class))).thenAnswer(inv -> inv.getArgument(0));

        GameResponse updated = gameService.updateGame(gameId, updateReq);

        assertThat(updated.title()).isEqualTo("Elden Ring: Shadow of the Erdtree");
        verify(gameRepository).save(gameTitle);
    }

    @Test
    @DisplayName("updateGame: allows title update if found match has same gameId (self)")
    void updateGame_shouldAllow_whenTitleMatchedToSelf() {
        GameRequest updateReq = new GameRequest(
                "elden ring", // case changed
                "ACTION_RPG",
                null,
                2022,
                "MATURE",
                null,
                null,
                0,
                null,
                null,
                null
        );

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameTitle));
        when(gameRepository.save(any(GameTitle.class))).thenAnswer(inv -> inv.getArgument(0));

        GameResponse updated = gameService.updateGame(gameId, updateReq);

        assertThat(updated.title()).isEqualTo("elden ring");
    }

    @Test
    @DisplayName("updateGame: throws CONFLICT when new title is already taken by another game")
    void updateGame_shouldThrowConflict_whenTitleTakenByOtherGame() {
        UUID otherId = UUID.randomUUID();
        GameTitle otherGame = GameTitle.builder()
                .id(otherId)
                .title("Dark Souls")
                .build();

        GameRequest updateReq = new GameRequest(
                "Dark Souls",
                "ACTION_RPG",
                List.of("PC"),
                2011,
                "MATURE",
                null,
                null,
                1,
                false,
                true
        );

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameTitle));
        when(gameRepository.findByTitleIgnoreCase("Dark Souls")).thenReturn(Optional.of(otherGame));

        assertThatThrownBy(() -> gameService.updateGame(gameId, updateReq))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("GAME_TITLE_EXISTS");
                });

        verify(gameRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateGame: throws NOT_FOUND when game does not exist")
    void updateGame_shouldThrowNotFound_whenGameDoesNotExist() {
        UUID unknownId = UUID.randomUUID();
        GameRequest updateReq = new GameRequest("Title", "Genre", List.of("PC"), 2020, "E", null, null, 1, false, true);

        when(gameRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.updateGame(unknownId, updateReq))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getErrorCode()).isEqualTo("GAME_NOT_FOUND");
                });
    }

    // ── deleteGame ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteGame: successfully deletes existing game")
    void deleteGame_shouldSucceed_whenGameExists() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameTitle));

        gameService.deleteGame(gameId);

        verify(gameRepository).delete(gameTitle);
    }

    @Test
    @DisplayName("deleteGame: throws NOT_FOUND when game does not exist")
    void deleteGame_shouldThrowNotFound_whenGameDoesNotExist() {
        UUID unknownId = UUID.randomUUID();
        when(gameRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.deleteGame(unknownId))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getErrorCode()).isEqualTo("GAME_NOT_FOUND");
                });

        verify(gameRepository, never()).delete(any());
    }

    // ── toggleActive ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleActive: toggles active status of game")
    void toggleActive_shouldToggleActiveStatus() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameTitle));
        when(gameRepository.save(any(GameTitle.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(gameTitle.isActive()).isTrue();

        GameResponse response = gameService.toggleActive(gameId);
        assertThat(response.active()).isFalse();

        GameResponse response2 = gameService.toggleActive(gameId);
        assertThat(response2.active()).isTrue();
    }

    @Test
    @DisplayName("toggleActive: throws NOT_FOUND when game does not exist")
    void toggleActive_shouldThrowNotFound_whenGameDoesNotExist() {
        UUID unknownId = UUID.randomUUID();
        when(gameRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.toggleActive(unknownId))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getErrorCode()).isEqualTo("GAME_NOT_FOUND");
                });
    }

    // ── getGameStats ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getGameStats: returns aggregate statistics with top genre")
    void getGameStats_shouldReturnStats_whenGenresAvailable() {
        when(gameRepository.count()).thenReturn(10L);
        when(gameRepository.countByActive(true)).thenReturn(8L);
        when(gameRepository.countByFeatured(true)).thenReturn(3L);
        when(gameRepository.countByMaxPlayersGreaterThan(1)).thenReturn(5L);

        Object[] genreRow = new Object[]{"ACTION_RPG", 5L};
        List<Object[]> genresList = new ArrayList<>();
        genresList.add(genreRow);
        when(gameRepository.findTopGenres()).thenReturn(genresList);

        GameStatsResponse stats = gameService.getGameStats();

        assertThat(stats.totalGames()).isEqualTo(10L);
        assertThat(stats.activeGames()).isEqualTo(8L);
        assertThat(stats.featuredGames()).isEqualTo(3L);
        assertThat(stats.topGenre()).isEqualTo("ACTION_RPG");
        assertThat(stats.multiplayerGamesCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getGameStats: defaults top genre to ACTION when genres list is empty or null")
    void getGameStats_shouldDefaultTopGenre_whenGenresEmptyOrNull() {
        when(gameRepository.count()).thenReturn(0L);
        when(gameRepository.countByActive(true)).thenReturn(0L);
        when(gameRepository.countByFeatured(true)).thenReturn(0L);
        when(gameRepository.countByMaxPlayersGreaterThan(1)).thenReturn(0L);
        when(gameRepository.findTopGenres()).thenReturn(Collections.emptyList());

        GameStatsResponse stats = gameService.getGameStats();

        assertThat(stats.topGenre()).isEqualTo("ACTION");
    }

    // ── Station - Game Configuration ──────────────────────────────────────────

    @Test
    @DisplayName("getGamesForStation: returns games installed on station")
    void getGamesForStation_shouldReturnGames_whenStationExists() {
        when(gameStationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(gameRepository.findByStationId(stationId)).thenReturn(List.of(gameTitle));

        List<GameResponse> games = gameService.getGamesForStation(stationId);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).title()).isEqualTo("Elden Ring");
    }

    @Test
    @DisplayName("getGamesForStation: throws NOT_FOUND when station does not exist")
    void getGamesForStation_shouldThrowNotFound_whenStationDoesNotExist() {
        UUID unknownId = UUID.randomUUID();
        when(gameStationRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.getGamesForStation(unknownId))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getErrorCode()).isEqualTo("STATION_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("assignGamesToStation: assigns new games and unassigns removed games")
    void assignGamesToStation_shouldUpdateStationGames() {
        UUID newGameId = UUID.randomUUID();
        GameTitle newGame = GameTitle.builder()
                .id(newGameId)
                .title("Tekken 8")
                .genre("FIGHTING")
                .platforms("PS5")
                .releaseYear(2024)
                .ageRating("TEEN")
                .stations(new HashSet<>())
                .build();

        when(gameStationRepository.findById(stationId)).thenReturn(Optional.of(station));
        // currently assigned: gameTitle
        when(gameRepository.findByStationId(stationId))
                .thenReturn(List.of(gameTitle)) // first call for currentGames
                .thenReturn(List.of(newGame));   // second call in getGamesForStation

        // new list only contains newGameId (gameTitle will be unassigned)
        when(gameRepository.findAllById(List.of(newGameId))).thenReturn(List.of(newGame));

        List<GameResponse> result = gameService.assignGamesToStation(stationId, List.of(newGameId));

        assertThat(gameTitle.getStations()).doesNotContain(station);
        assertThat(newGame.getStations()).contains(station);
        verify(gameRepository).save(gameTitle);
        verify(gameRepository).save(newGame);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Tekken 8");
    }

    @Test
    @DisplayName("assignGamesToStation: throws NOT_FOUND when station does not exist")
    void assignGamesToStation_shouldThrowNotFound_whenStationDoesNotExist() {
        UUID unknownId = UUID.randomUUID();
        when(gameStationRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.assignGamesToStation(unknownId, List.of(gameId)))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getErrorCode()).isEqualTo("STATION_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("removeGameFromStation: removes station from game if contained")
    void removeGameFromStation_shouldRemoveStation_whenContained() {
        when(gameStationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameTitle));

        assertThat(gameTitle.getStations()).contains(station);

        gameService.removeGameFromStation(stationId, gameId);

        assertThat(gameTitle.getStations()).doesNotContain(station);
        verify(gameRepository).save(gameTitle);
    }

    @Test
    @DisplayName("removeGameFromStation: does nothing if station not attached to game")
    void removeGameFromStation_shouldDoNothing_whenStationNotAttached() {
        gameTitle.getStations().clear();
        when(gameStationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameTitle));

        gameService.removeGameFromStation(stationId, gameId);

        verify(gameRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeGameFromStation: throws NOT_FOUND when station does not exist")
    void removeGameFromStation_shouldThrowNotFound_whenStationNotFound() {
        UUID unknownStationId = UUID.randomUUID();
        when(gameStationRepository.findById(unknownStationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.removeGameFromStation(unknownStationId, gameId))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getErrorCode()).isEqualTo("STATION_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("getStationsForGame: returns list of stations assigned to game")
    void getStationsForGame_shouldReturnStations_whenGameExists() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameTitle));
        when(gameRepository.findStationsByGameId(gameId)).thenReturn(List.of(station));

        List<GameStationResponse> stations = gameService.getStationsForGame(gameId);

        assertThat(stations).hasSize(1);
        assertThat(stations.get(0).stationCode()).isEqualTo("PS5-01");
    }

    @Test
    @DisplayName("updateStationsForGame: updates stations for game")
    void updateStationsForGame_shouldUpdateStations_whenGameExists() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameTitle));
        when(gameStationRepository.findAllById(List.of(stationId))).thenReturn(List.of(station));

        List<GameStationResponse> result = gameService.updateStationsForGame(gameId, List.of(stationId));

        assertThat(result).hasSize(1);
        assertThat(gameTitle.getStations()).contains(station);
        verify(gameRepository).save(gameTitle);

        // When stationIds is null or empty
        List<GameStationResponse> emptyResult = gameService.updateStationsForGame(gameId, null);
        assertThat(emptyResult).isEmpty();
        assertThat(gameTitle.getStations()).isEmpty();
    }
}
