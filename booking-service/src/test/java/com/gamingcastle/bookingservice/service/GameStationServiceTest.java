package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.dto.GameStationRequest;
import com.gamingcastle.bookingservice.dto.GameStationResponse;
import com.gamingcastle.bookingservice.dto.SlotAvailabilityResponse;
import com.gamingcastle.bookingservice.entity.Booking;
import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.entity.StationType;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.repository.BookingRepository;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameStationServiceTest {

    @Mock
    private GameStationRepository gameStationRepository;

    @Mock
    private BookingRepository bookingRepository;

    private GameStationService gameStationService;

    private UUID stationId;
    private GameStation station;

    @BeforeEach
    void setUp() {
        gameStationService = new GameStationServiceImpl(gameStationRepository, bookingRepository);
        stationId = UUID.randomUUID();
        station = GameStation.builder()
                .id(stationId)
                .stationCode("PC-01")
                .type(StationType.PC)
                .hourlyRate(BigDecimal.valueOf(300))
                .active(true)
                .build();
    }

    // ── createStation ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("createStation: successfully creates station when code is unique")
    void createStation_shouldSucceed_whenCodeUnique() {
        GameStationRequest request = new GameStationRequest("PC-02", StationType.PC, BigDecimal.valueOf(300), true);

        when(gameStationRepository.findByStationCode("PC-02")).thenReturn(Optional.empty());
        when(gameStationRepository.save(any(GameStation.class))).thenAnswer(invocation -> {
            GameStation s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        GameStationResponse response = gameStationService.createStation(request);

        assertThat(response).isNotNull();
        assertThat(response.stationCode()).isEqualTo("PC-02");
        assertThat(response.type()).isEqualTo(StationType.PC);
        assertThat(response.hourlyRate()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(response.active()).isTrue();

        verify(gameStationRepository).save(any(GameStation.class));
    }

    @Test
    @DisplayName("createStation: throws CONFLICT when station code already exists")
    void createStation_shouldThrowConflict_whenCodeExists() {
        GameStationRequest request = new GameStationRequest("PC-01", StationType.PC, BigDecimal.valueOf(300), true);

        when(gameStationRepository.findByStationCode("PC-01")).thenReturn(Optional.of(station));

        assertThatThrownBy(() -> gameStationService.createStation(request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("STATION_CODE_EXISTS");
                });

        verify(gameStationRepository, never()).save(any());
    }

    // ── updateStation ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateStation: updates station successfully without code change")
    void updateStation_shouldSucceed_whenCodeUnchanged() {
        GameStationRequest request = new GameStationRequest("PC-01", StationType.CONSOLE, BigDecimal.valueOf(450), false);

        when(gameStationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(gameStationRepository.save(any(GameStation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameStationResponse response = gameStationService.updateStation(stationId, request);

        assertThat(response.type()).isEqualTo(StationType.CONSOLE);
        assertThat(response.hourlyRate()).isEqualByComparingTo(BigDecimal.valueOf(450));
        assertThat(response.active()).isFalse();

        verify(gameStationRepository, never()).findByStationCode(any());
        verify(gameStationRepository).save(station);
    }

    @Test
    @DisplayName("updateStation: updates station with new unique code")
    void updateStation_shouldSucceed_whenCodeChangedToUnique() {
        GameStationRequest request = new GameStationRequest("PC-NEW", StationType.PC, BigDecimal.valueOf(350), true);

        when(gameStationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(gameStationRepository.findByStationCode("PC-NEW")).thenReturn(Optional.empty());
        when(gameStationRepository.save(any(GameStation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameStationResponse response = gameStationService.updateStation(stationId, request);

        assertThat(response.stationCode()).isEqualTo("PC-NEW");
        verify(gameStationRepository).save(station);
    }

    @Test
    @DisplayName("updateStation: throws CONFLICT when new code is taken by another station")
    void updateStation_shouldThrowConflict_whenNewCodeTakenByAnotherStation() {
        GameStation otherStation = GameStation.builder()
                .id(UUID.randomUUID())
                .stationCode("PC-02")
                .type(StationType.PC)
                .hourlyRate(BigDecimal.valueOf(300))
                .active(true)
                .build();

        GameStationRequest request = new GameStationRequest("PC-02", StationType.PC, BigDecimal.valueOf(350), true);

        when(gameStationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(gameStationRepository.findByStationCode("PC-02")).thenReturn(Optional.of(otherStation));

        assertThatThrownBy(() -> gameStationService.updateStation(stationId, request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> {
                    BookingException be = (BookingException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("STATION_CODE_EXISTS");
                });

        verify(gameStationRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStation: allows updating when findByStationCode returns self")
    void updateStation_shouldAllowWhenCodeMatchesSelfInLookup() {
        GameStationRequest request = new GameStationRequest("PC-01-SAME", StationType.PC, BigDecimal.valueOf(350), true);
        station.setStationCode("PC-01-OLD");

        when(gameStationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(gameStationRepository.findByStationCode("PC-01-SAME")).thenReturn(Optional.of(station)); // same ID
        when(gameStationRepository.save(any(GameStation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameStationResponse response = gameStationService.updateStation(stationId, request);

        assertThat(response.stationCode()).isEqualTo("PC-01-SAME");
        verify(gameStationRepository).save(station);
    }

    @Test
    @DisplayName("updateStation: throws NOT_FOUND when station does not exist")
    void updateStation_shouldThrowNotFound_whenStationDoesNotExist() {
        GameStationRequest request = new GameStationRequest("PC-01", StationType.PC, BigDecimal.valueOf(300), true);

        when(gameStationRepository.findById(stationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameStationService.updateStation(stationId, request))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(gameStationRepository, never()).save(any());
    }

    // ── deleteStation ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteStation: soft-deletes station by setting active to false")
    void deleteStation_shouldSoftDelete_whenStationExists() {
        when(gameStationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(gameStationRepository.save(any(GameStation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gameStationService.deleteStation(stationId);

        assertThat(station.isActive()).isFalse();
        verify(gameStationRepository).save(station);
    }

    @Test
    @DisplayName("deleteStation: throws NOT_FOUND when station does not exist")
    void deleteStation_shouldThrowNotFound_whenStationDoesNotExist() {
        when(gameStationRepository.findById(stationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameStationService.deleteStation(stationId))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(gameStationRepository, never()).save(any());
    }

    // ── getAllStations ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllStations: returns all mapped stations")
    void getAllStations_shouldReturnAllStations() {
        when(gameStationRepository.findAll()).thenReturn(List.of(station));

        List<GameStationResponse> stations = gameStationService.getAllStations();

        assertThat(stations).hasSize(1);
        assertThat(stations.get(0).stationCode()).isEqualTo("PC-01");
    }

    // ── getStationById ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStationById: returns station response when found")
    void getStationById_shouldReturnStation_whenFound() {
        when(gameStationRepository.findById(stationId)).thenReturn(Optional.of(station));

        GameStationResponse response = gameStationService.getStationById(stationId);

        assertThat(response.id()).isEqualTo(stationId);
        assertThat(response.stationCode()).isEqualTo("PC-01");
    }

    @Test
    @DisplayName("getStationById: throws NOT_FOUND when station does not exist")
    void getStationById_shouldThrowNotFound_whenDoesNotExist() {
        when(gameStationRepository.findById(stationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameStationService.getStationById(stationId))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── getStationAvailability ───────────────────────────────────────────────

    @Test
    @DisplayName("getStationAvailability: generates 13 hourly slots and correctly checks conflicts")
    void getStationAvailability_shouldGenerate13SlotsWithAvailability() {
        LocalDate date = LocalDate.of(2026, 10, 15);

        when(gameStationRepository.findById(stationId)).thenReturn(Optional.of(station));

        // Let's simulate hour 10 (10:00 to 11:00) having a conflicting booking
        Booking mockBooking = mock(Booking.class);
        when(bookingRepository.findOverlapping(eq(stationId), any(Instant.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    Instant start = invocation.getArgument(1);
                    // if slot starts at 10:00 (which is hour 10 in system zone)
                    if (start.toString().contains("10:00")) {
                        return List.of(mockBooking);
                    }
                    return Collections.emptyList();
                });

        List<SlotAvailabilityResponse> slots = gameStationService.getStationAvailability(stationId, date);

        assertThat(slots).hasSize(13); // 9:00 through 21:00 (13 1-hour slots)
        verify(bookingRepository, times(13)).findOverlapping(eq(stationId), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("getStationAvailability: throws NOT_FOUND when station does not exist")
    void getStationAvailability_shouldThrowNotFound_whenStationDoesNotExist() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        when(gameStationRepository.findById(stationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameStationService.getStationAvailability(stationId, date))
                .isInstanceOf(BookingException.class)
                .satisfies(e -> assertThat(((BookingException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(bookingRepository);
    }
}
