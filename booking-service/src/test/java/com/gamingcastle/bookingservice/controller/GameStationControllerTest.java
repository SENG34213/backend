package com.gamingcastle.bookingservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamingcastle.bookingservice.dto.GameStationRequest;
import com.gamingcastle.bookingservice.dto.GameStationResponse;
import com.gamingcastle.bookingservice.dto.SlotAvailabilityResponse;
import com.gamingcastle.bookingservice.entity.StationType;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.exception.GlobalExceptionHandler;
import com.gamingcastle.bookingservice.service.GameStationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GameStationControllerTest {

    @Mock
    private GameStationService gameStationService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID stationId;

    @BeforeEach
    void setUp() {
        GameStationController controller = new GameStationController(gameStationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        stationId = UUID.randomUUID();
    }

    private GameStationResponse createSampleResponse(String code, StationType type, BigDecimal rate, boolean active) {
        return new GameStationResponse(stationId, code, type, rate, active);
    }

    // ── GET /api/stations ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/stations: returns list of all game stations")
    void getAllStations_shouldReturnList() throws Exception {
        GameStationResponse station1 = createSampleResponse("PC-01", StationType.PC, BigDecimal.valueOf(300), true);
        GameStationResponse station2 = createSampleResponse("VR-01", StationType.VR, BigDecimal.valueOf(600), true);

        when(gameStationService.getAllStations()).thenReturn(List.of(station1, station2));

        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].stationCode").value("PC-01"))
                .andExpect(jsonPath("$[1].stationCode").value("VR-01"));

        verify(gameStationService).getAllStations();
    }

    // ── GET /api/stations/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/stations/{id}: returns station when found")
    void getStation_shouldReturnStation_whenFound() throws Exception {
        GameStationResponse station = createSampleResponse("PC-01", StationType.PC, BigDecimal.valueOf(300), true);
        when(gameStationService.getStationById(stationId)).thenReturn(station);

        mockMvc.perform(get("/api/stations/{id}", stationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stationId.toString()))
                .andExpect(jsonPath("$.stationCode").value("PC-01"));

        verify(gameStationService).getStationById(stationId);
    }

    @Test
    @DisplayName("GET /api/stations/{id}: returns 404 when station not found")
    void getStation_shouldReturn404_whenNotFound() throws Exception {
        when(gameStationService.getStationById(stationId))
                .thenThrow(new BookingException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND", "Station not found"));

        mockMvc.perform(get("/api/stations/{id}", stationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("STATION_NOT_FOUND"));
    }

    // ── GET /api/stations/{id}/availability ───────────────────────────────────

    @Test
    @DisplayName("GET /api/stations/{id}/availability: returns hourly slot availability for a date")
    void getStationAvailability_shouldReturnSlots() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 15);
        Instant slotStart = Instant.parse("2026-10-15T09:00:00Z");
        Instant slotEnd = Instant.parse("2026-10-15T10:00:00Z");
        SlotAvailabilityResponse slot = new SlotAvailabilityResponse(slotStart, slotEnd, true);

        when(gameStationService.getStationAvailability(stationId, date)).thenReturn(List.of(slot));

        mockMvc.perform(get("/api/stations/{id}/availability", stationId)
                        .param("date", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].available").value(true));

        verify(gameStationService).getStationAvailability(stationId, date);
    }

    // ── POST /api/stations ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/stations: creates new station successfully")
    void createStation_shouldReturnCreated_whenValid() throws Exception {
        GameStationRequest request = new GameStationRequest("PC-05", StationType.PC, BigDecimal.valueOf(350), true);
        GameStationResponse response = createSampleResponse("PC-05", StationType.PC, BigDecimal.valueOf(350), true);

        when(gameStationService.createStation(any(GameStationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stationCode").value("PC-05"))
                .andExpect(jsonPath("$.hourlyRate").value(350));

        verify(gameStationService).createStation(any(GameStationRequest.class));
    }

    @Test
    @DisplayName("POST /api/stations: returns 400 when stationCode is blank")
    void createStation_shouldReturnBadRequest_whenCodeBlank() throws Exception {
        GameStationRequest request = new GameStationRequest("", StationType.PC, BigDecimal.valueOf(350), true);

        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(gameStationService);
    }

    @Test
    @DisplayName("POST /api/stations: returns 400 when hourlyRate is zero or negative")
    void createStation_shouldReturnBadRequest_whenHourlyRateNotPositive() throws Exception {
        GameStationRequest request = new GameStationRequest("PC-05", StationType.PC, BigDecimal.ZERO, true);

        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/stations: returns 409 when stationCode already exists")
    void createStation_shouldReturnConflict_whenCodeExists() throws Exception {
        GameStationRequest request = new GameStationRequest("PC-01", StationType.PC, BigDecimal.valueOf(300), true);

        when(gameStationService.createStation(any(GameStationRequest.class)))
                .thenThrow(new BookingException(HttpStatus.CONFLICT, "STATION_CODE_EXISTS", "Station code already exists"));

        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("STATION_CODE_EXISTS"));
    }

    // ── PUT /api/stations/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/stations/{id}: updates station successfully")
    void updateStation_shouldReturnOk_whenValid() throws Exception {
        GameStationRequest request = new GameStationRequest("PC-01-UPDATED", StationType.PC, BigDecimal.valueOf(320), true);
        GameStationResponse response = createSampleResponse("PC-01-UPDATED", StationType.PC, BigDecimal.valueOf(320), true);

        when(gameStationService.updateStation(eq(stationId), any(GameStationRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/stations/{id}", stationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationCode").value("PC-01-UPDATED"));

        verify(gameStationService).updateStation(eq(stationId), any(GameStationRequest.class));
    }

    @Test
    @DisplayName("PUT /api/stations/{id}: returns 404 when station to update is not found")
    void updateStation_shouldReturn404_whenNotFound() throws Exception {
        GameStationRequest request = new GameStationRequest("PC-01", StationType.PC, BigDecimal.valueOf(300), true);

        when(gameStationService.updateStation(eq(stationId), any(GameStationRequest.class)))
                .thenThrow(new BookingException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND", "Station not found"));

        mockMvc.perform(put("/api/stations/{id}", stationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("STATION_NOT_FOUND"));
    }

    // ── DELETE /api/stations/{id} ─────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/stations/{id}: soft-deletes station returning 204 No Content")
    void deleteStation_shouldReturnNoContent() throws Exception {
        doNothing().when(gameStationService).deleteStation(stationId);

        mockMvc.perform(delete("/api/stations/{id}", stationId))
                .andExpect(status().isNoContent());

        verify(gameStationService).deleteStation(stationId);
    }

    @Test
    @DisplayName("DELETE /api/stations/{id}: returns 404 when station not found")
    void deleteStation_shouldReturn404_whenNotFound() throws Exception {
        doThrow(new BookingException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND", "Station not found"))
                .when(gameStationService).deleteStation(stationId);

        mockMvc.perform(delete("/api/stations/{id}", stationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("STATION_NOT_FOUND"));
    }
}
