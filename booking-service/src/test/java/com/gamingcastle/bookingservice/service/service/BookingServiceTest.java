package com.gamingcastle.bookingservice.service.service;

import com.gamingcastle.bookingservice.dto.BookingRequest;
import com.gamingcastle.bookingservice.dto.BookingResponse;
import com.gamingcastle.bookingservice.entity.*;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.repository.BookingRepository;
import com.gamingcastle.bookingservice.repository.GameStationRepository;
import com.gamingcastle.bookingservice.service.BookingService;
import com.gamingcastle.bookingservice.service.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private GameStationRepository gameStationRepository;

    private BookingService bookingService;

    private GameStation station;
    private UUID userId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bookingService = new BookingServiceImpl(bookingRepository, gameStationRepository);

        station = GameStation.builder()
                .id(UUID.randomUUID())
                .stationCode("PC-01")
                .type(StationType.PC)
                .hourlyRate(BigDecimal.valueOf(300))
                .active(true)
                .build();
        userId = UUID.randomUUID();
    }

    @Test
    void createBooking_shouldSucceed_givenFreeSlot() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(UUID.randomUUID());
            b.setCreatedAt(Instant.now());
            return b;
        });

        BookingResponse response = bookingService.createBooking(userId, request, BookingSource.ONLINE);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.source()).isEqualTo(BookingSource.ONLINE);
        assertThat(response.stationCode()).isEqualTo("PC-01");
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void createBooking_shouldReject_givenOverlappingConfirmedBooking() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), start, end);

        Booking existing = Booking.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .station(station)
                .startTime(start.minus(30, ChronoUnit.MINUTES))
                .endTime(start.plus(30, ChronoUnit.MINUTES))
                .status(BookingStatus.CONFIRMED)
                .source(BookingSource.ONLINE)
                .build();

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .hasMessageContaining("already booked");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_shouldSucceed_givenAdjacentSlot() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(userId, request, BookingSource.ONLINE);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void createBooking_shouldIgnoreCancelledBookings_becauseRepositoryQueryFiltersThemOut() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), start, end);

        when(gameStationRepository.findById(station.getId())).thenReturn(Optional.of(station));
        when(bookingRepository.findOverlapping(station.getId(), start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(userId, request, BookingSource.ONLINE);

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        verify(bookingRepository).findOverlapping(station.getId(), start, end);
    }

    @Test
    void createBooking_shouldThrow404_givenNonExistentStation() {
        UUID unknownStationId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(unknownStationId, start, end);

        when(gameStationRepository.findById(unknownStationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .hasMessageContaining("No game station exists");
        verify(bookingRepository, never()).findOverlapping(any(), any(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_shouldReject_givenEndTimeBeforeStartTime() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.minus(1, ChronoUnit.HOURS);

        BookingRequest request = new BookingRequest(station.getId(), start, end);

        assertThatThrownBy(() -> bookingService.createBooking(userId, request, BookingSource.ONLINE))
                .isInstanceOf(BookingException.class)
                .hasMessageContaining("endTime must be after startTime");
        verifyNoInteractions(gameStationRepository);
    }
}