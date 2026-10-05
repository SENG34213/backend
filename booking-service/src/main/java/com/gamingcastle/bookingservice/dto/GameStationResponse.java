package com.gamingcastle.bookingservice.dto;

import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.entity.StationType;
import java.math.BigDecimal;
import java.util.UUID;

public record GameStationResponse(
    UUID id,
    String stationCode,
    StationType type,
    BigDecimal hourlyRate,
    boolean active
) {
    public static GameStationResponse from(GameStation station) {
        return new GameStationResponse(
            station.getId(),
            station.getStationCode(),
            station.getType(),
            station.getHourlyRate(),
            station.isActive()
        );
    }
}
