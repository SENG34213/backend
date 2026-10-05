package com.gamingcastle.bookingservice.dto;

import com.gamingcastle.bookingservice.entity.StationType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record GameStationRequest(
    @NotBlank(message = "Station code cannot be blank")
    String stationCode,
    
    @NotNull(message = "Station type cannot be null")
    StationType type,
    
    @NotNull(message = "Hourly rate cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Hourly rate must be greater than zero")
    BigDecimal hourlyRate,
    
    boolean active
) {}
