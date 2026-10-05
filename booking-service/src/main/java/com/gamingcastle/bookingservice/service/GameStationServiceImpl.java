package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.GameStationRequest;
import com.gamingcastle.bookingservice.dto.GameStationResponse;
import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.repository.GameStationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admin management of game stations — create, update, soft-delete/reactivate, list.
 */
@Service
public class GameStationServiceImpl implements GameStationService {

    private final GameStationRepository gameStationRepository;

    public GameStationServiceImpl(GameStationRepository gameStationRepository) {
        this.gameStationRepository = gameStationRepository;
    }

    @Override
    @Transactional
    public GameStationResponse createStation(GameStationRequest request) {
        // Reject duplicate station codes
        gameStationRepository.findByStationCode(request.stationCode()).ifPresent(existing -> {
            throw new BookingException(HttpStatus.CONFLICT, "STATION_CODE_EXISTS",
                    "A station with code '" + request.stationCode() + "' already exists");
        });

        GameStation station = GameStation.builder()
                .stationCode(request.stationCode())
                .type(request.type())
                .hourlyRate(request.hourlyRate())
                .active(request.active())
                .build();

        return GameStationResponse.from(gameStationRepository.save(station));
    }

    @Override
    @Transactional
    public GameStationResponse updateStation(UUID id, GameStationRequest request) {
        GameStation station = findOrThrow(id);

        // If code is changing, check for conflicts with another station
        if (!station.getStationCode().equals(request.stationCode())) {
            gameStationRepository.findByStationCode(request.stationCode()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BookingException(HttpStatus.CONFLICT, "STATION_CODE_EXISTS",
                            "A station with code '" + request.stationCode() + "' already exists");
                }
            });
        }

        station.setStationCode(request.stationCode());
        station.setType(request.type());
        station.setHourlyRate(request.hourlyRate());
        station.setActive(request.active());

        return GameStationResponse.from(gameStationRepository.save(station));
    }

    @Override
    @Transactional
    public void deleteStation(UUID id) {
        GameStation station = findOrThrow(id);
        // Soft-delete: mark as inactive rather than physically removing the row,
        // so existing bookings still reference a valid station record.
        station.setActive(false);
        gameStationRepository.save(station);
    }

    @Override
    public List<GameStationResponse> getAllStations() {
        return gameStationRepository.findAll()
                .stream()
                .map(GameStationResponse::from)
                .toList();
    }

    @Override
    public GameStationResponse getStationById(UUID id) {
        return GameStationResponse.from(findOrThrow(id));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private GameStation findOrThrow(UUID id) {
        return gameStationRepository.findById(id)
                .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND",
                        "No game station exists with id " + id));
    }
}
