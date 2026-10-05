package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.GameStationRequest;
import com.gamingcastle.bookingservice.dto.GameStationResponse;
import java.util.List;
import java.util.UUID;

public interface GameStationService {
    GameStationResponse createStation(GameStationRequest request);
    GameStationResponse updateStation(UUID id, GameStationRequest request);
    void deleteStation(UUID id);
    List<GameStationResponse> getAllStations();
    GameStationResponse getStationById(UUID id);
}
