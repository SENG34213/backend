package com.gamingcastle.bookingservice.repository;

import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.entity.GameTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRepository extends JpaRepository<GameTitle, UUID> {

    Optional<GameTitle> findByTitleIgnoreCase(String title);

    List<GameTitle> findByActive(boolean active);

    long countByActive(boolean active);

    long countByFeatured(boolean featured);

    long countByMaxPlayersGreaterThan(int maxPlayers);

    @Query("SELECT g FROM GameTitle g JOIN g.stations s WHERE s.id = :stationId")
    List<GameTitle> findByStationId(@Param("stationId") UUID stationId);

    @Query("SELECT s FROM GameStation s JOIN s.games g WHERE g.id = :gameId")
    List<GameStation> findStationsByGameId(@Param("gameId") UUID gameId);

    @Query("SELECT g.genre, COUNT(g) FROM GameTitle g GROUP BY g.genre ORDER BY COUNT(g) DESC")
    List<Object[]> findTopGenres();
}
