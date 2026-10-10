package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.GameStationRequest;
import com.gamingcastle.bookingservice.dto.GameStationResponse;
import com.gamingcastle.bookingservice.dto.SlotAvailabilityResponse;
import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.repository.BookingRepository;
import com.gamingcastle.bookingservice.repository.GameStationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin management of game stations — create, update, soft-delete/reactivate, list, availability.
 */
@Service
public class GameStationServiceImpl implements GameStationService {

    private final GameStationRepository gameStationRepository;
    private final BookingRepository bookingRepository;

    public GameStationServiceImpl(GameStationRepository gameStationRepository, BookingRepository bookingRepository) {
        this.gameStationRepository = gameStationRepository;
        this.bookingRepository = bookingRepository;
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

    @Override
    @Transactional(readOnly = true)
    public List<SlotAvailabilityResponse> getStationAvailability(UUID id, LocalDate date) {
        findOrThrow(id);

        ZoneId zone = ZoneId.systemDefault();
        List<SlotAvailabilityResponse> slots = new ArrayList<>();

        // Generate 1-hour time slots from 09:00 to 22:00 for the requested date
        for (int hour = 9; hour < 22; hour++) {
            Instant start = date.atTime(hour, 0).atZone(zone).toInstant();
            Instant end = date.atTime(hour + 1, 0).atZone(zone).toInstant();

            var conflicts = bookingRepository.findOverlapping(id, start, end);
            boolean available = conflicts.isEmpty();

            slots.add(new SlotAvailabilityResponse(start, end, available));
        }

        return slots;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private GameStation findOrThrow(UUID id) {
        return gameStationRepository.findById(id)
                .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND",
                        "No game station exists with id " + id));
    }
}
