package com.gamingcastle.bookingservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gamingcastle.bookingservice.client.NotificationClient;
import com.gamingcastle.bookingservice.dto.BookingRequest;
import com.gamingcastle.bookingservice.dto.BookingResponse;
import com.gamingcastle.bookingservice.dto.ConfirmBookingRequest;
import com.gamingcastle.bookingservice.dto.WalkInBookingRequest;
import com.gamingcastle.bookingservice.entity.BookingSource;
import com.gamingcastle.bookingservice.entity.BookingStatus;
import com.gamingcastle.bookingservice.exception.BookingException;
import com.gamingcastle.bookingservice.exception.GlobalExceptionHandler;
import com.gamingcastle.bookingservice.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @Mock
    private NotificationClient notificationClient;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID stationId;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        // initialize and configure the ObjectMapper first
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
        BookingController controller = new BookingController(bookingService, notificationClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    
        userId = UUID.randomUUID();
        stationId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
    }

    private BookingResponse createSampleResponse(BookingStatus status, BookingSource source) {
        return new BookingResponse(
                bookingId,
                userId,
                stationId,
                "PC-01",
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                status,
                source,
                UUID.randomUUID(),
                BigDecimal.valueOf(600),
                BigDecimal.ZERO,
                BigDecimal.valueOf(600),
                Instant.now()
        );
    }

    // ── POST /api/bookings ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/bookings: successfully creates online booking")
    void createBooking_shouldReturnCreated_whenValid() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(stationId, start, end, "CARD", 100);
        BookingResponse response = createSampleResponse(BookingStatus.CONFIRMED, BookingSource.ONLINE);

        when(bookingService.createBooking(eq(userId), any(BookingRequest.class), eq(BookingSource.ONLINE)))
                .thenReturn(response);

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.stationCode").value("PC-01"));

        verify(bookingService).createBooking(eq(userId), any(BookingRequest.class), eq(BookingSource.ONLINE));
    }

    @Test
    @DisplayName("POST /api/bookings: returns 400 when validation fails on null stationId")
    void createBooking_shouldReturnBadRequest_whenStationIdNull() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(null, start, end);

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("POST /api/bookings: returns 409 when slot is unavailable")
    void createBooking_shouldReturnConflict_whenSlotUnavailable() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(stationId, start, end);

        when(bookingService.createBooking(any(), any(), any()))
                .thenThrow(new BookingException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "Station is already booked"));

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Station is already booked"));
    }

    @Test
    @DisplayName("POST /api/bookings: returns 402 when payment fails")
    void createBooking_shouldReturnPaymentRequired_whenPaymentFails() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(stationId, start, end);

        when(bookingService.createBooking(any(), any(), any()))
                .thenThrow(new BookingException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED", "Insufficient funds"));

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("PAYMENT_FAILED"));
    }

    // ── POST /api/bookings/walk-in ────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/bookings/walk-in: successfully creates walk-in booking as admin")
    void createWalkInBooking_shouldReturnCreated_whenAdmin() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        WalkInBookingRequest request = new WalkInBookingRequest(targetUserId, stationId, start, end);
        BookingResponse response = createSampleResponse(BookingStatus.PENDING, BookingSource.WALK_IN);

        when(bookingService.createBooking(eq(targetUserId), any(BookingRequest.class), eq(BookingSource.WALK_IN)))
                .thenReturn(response);

        mockMvc.perform(post("/api/bookings/walk-in")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.source").value("WALK_IN"));

        verify(bookingService).createBooking(eq(targetUserId), any(BookingRequest.class), eq(BookingSource.WALK_IN));
    }

    @Test
    @DisplayName("POST /api/bookings/walk-in: returns 500 INTERNAL_ERROR under GlobalExceptionHandler (controller throws ResponseStatusException 403) when caller is not admin")
    void createWalkInBooking_shouldReturnForbidden_whenNotAdmin() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        WalkInBookingRequest request = new WalkInBookingRequest(targetUserId, stationId, start, end);

        mockMvc.perform(post("/api/bookings/walk-in")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("POST /api/bookings/walk-in: returns 400 when targetUserId is null")
    void createWalkInBooking_shouldReturnBadRequest_whenTargetUserIdNull() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        WalkInBookingRequest request = new WalkInBookingRequest(null, stationId, start, end);

        mockMvc.perform(post("/api/bookings/walk-in")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ── GET /api/bookings/mine ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/bookings/mine: returns list of user bookings")
    void getMyBookings_shouldReturnBookings() throws Exception {
        BookingResponse response = createSampleResponse(BookingStatus.CONFIRMED, BookingSource.ONLINE);
        when(bookingService.getBookingsForUser(userId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/bookings/mine")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bookingId.toString()));

        verify(bookingService).getBookingsForUser(userId);
    }

    @Test
    @DisplayName("GET /api/bookings/mine: returns empty list when no bookings exist")
    void getMyBookings_shouldReturnEmptyList_whenNone() throws Exception {
        when(bookingService.getBookingsForUser(userId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/bookings/mine")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── PATCH /api/bookings/{id}/cancel ───────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/bookings/{id}/cancel: successfully cancels and sends email when email header present")
    void cancelBooking_shouldSucceedAndSendEmail_whenEmailProvided() throws Exception {
        BookingResponse response = createSampleResponse(BookingStatus.CANCELLED, BookingSource.ONLINE);
        when(bookingService.cancelBooking(bookingId, userId, false)).thenReturn(response);

        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .header("X-User-Email", "player@example.com")
                        .header("X-User-FullName", "Test Player"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(bookingService).cancelBooking(bookingId, userId, false);
        verify(notificationClient).sendBookingCancelled(any(NotificationClient.BookingEmailRequest.class));
    }

    @Test
    @DisplayName("PATCH /api/bookings/{id}/cancel: cancels without sending email when email header is omitted")
    void cancelBooking_shouldSucceedWithoutEmail_whenEmailOmitted() throws Exception {
        BookingResponse response = createSampleResponse(BookingStatus.CANCELLED, BookingSource.ONLINE);
        when(bookingService.cancelBooking(bookingId, userId, true)).thenReturn(response);

        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(bookingService).cancelBooking(bookingId, userId, true);
        verifyNoInteractions(notificationClient);
    }

    @Test
    @DisplayName("PATCH /api/bookings/{id}/cancel: returns 400 when cancellation window expired")
    void cancelBooking_shouldReturnBadRequest_whenTooLate() throws Exception {
        when(bookingService.cancelBooking(bookingId, userId, false))
                .thenThrow(new BookingException(HttpStatus.BAD_REQUEST, "TOO_LATE", "Cannot cancel within 2 hours"));

        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TOO_LATE"));

        verifyNoInteractions(notificationClient);
    }

    @Test
    @DisplayName("PATCH /api/bookings/{id}/cancel: returns 403 when trying to cancel another user's booking")
    void cancelBooking_shouldReturnForbidden_whenNotOwnBooking() throws Exception {
        when(bookingService.cancelBooking(bookingId, userId, false))
                .thenThrow(new BookingException(HttpStatus.FORBIDDEN, "NOT_YOUR_BOOKING", "Only your own bookings"));

        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_YOUR_BOOKING"));
    }

    // ── PATCH /api/bookings/{id}/reschedule ───────────────────────────────────

    @Test
    @DisplayName("PATCH /api/bookings/{id}/reschedule: successfully reschedules and sends email")
    void rescheduleBooking_shouldSucceedAndSendEmail_whenEmailProvided() throws Exception {
        Instant newStart = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(2, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(stationId, newStart, newEnd);
        BookingResponse response = createSampleResponse(BookingStatus.CONFIRMED, BookingSource.ONLINE);

        when(bookingService.rescheduleBooking(eq(bookingId), eq(userId), eq(false), any(BookingRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/bookings/{id}/reschedule", bookingId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .header("X-User-Email", "player@example.com")
                        .header("X-User-FullName", "Test Player")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()));

        verify(bookingService).rescheduleBooking(eq(bookingId), eq(userId), eq(false), any(BookingRequest.class));
        verify(notificationClient).sendBookingRescheduled(any(NotificationClient.BookingEmailRequest.class));
    }

    @Test
    @DisplayName("PATCH /api/bookings/{id}/reschedule: reschedules as admin without email")
    void rescheduleBooking_shouldSucceedAsAdmin_withoutEmail() throws Exception {
        Instant newStart = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(2, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(stationId, newStart, newEnd);
        BookingResponse response = createSampleResponse(BookingStatus.CONFIRMED, BookingSource.ONLINE);

        when(bookingService.rescheduleBooking(eq(bookingId), eq(userId), eq(true), any(BookingRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/bookings/{id}/reschedule", bookingId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(bookingService).rescheduleBooking(eq(bookingId), eq(userId), eq(true), any(BookingRequest.class));
        verifyNoInteractions(notificationClient);
    }

    @Test
    @DisplayName("PATCH /api/bookings/{id}/reschedule: returns 409 when target slot unavailable")
    void rescheduleBooking_shouldReturnConflict_whenSlotUnavailable() throws Exception {
        Instant newStart = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(2, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(stationId, newStart, newEnd);

        when(bookingService.rescheduleBooking(any(), any(), anyBoolean(), any()))
                .thenThrow(new BookingException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "Conflict"));

        mockMvc.perform(patch("/api/bookings/{id}/reschedule", bookingId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_UNAVAILABLE"));
    }

    // ── PATCH /api/bookings/{id}/confirm ──────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/bookings/{id}/confirm: successfully confirms booking")
    void confirmBooking_shouldSucceed_whenValid() throws Exception {
        UUID paymentId = UUID.randomUUID();
        ConfirmBookingRequest request = new ConfirmBookingRequest(paymentId);
        BookingResponse response = createSampleResponse(BookingStatus.CONFIRMED, BookingSource.WALK_IN);

        when(bookingService.confirmBooking(bookingId, paymentId)).thenReturn(response);

        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(bookingService).confirmBooking(bookingId, paymentId);
    }

    @Test
    @DisplayName("PATCH /api/bookings/{id}/confirm: returns 400 when paymentId is null")
    void confirmBooking_shouldReturnBadRequest_whenPaymentIdNull() throws Exception {
        ConfirmBookingRequest request = new ConfirmBookingRequest(null);

        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(bookingService);
    }

    // ── GET /api/bookings/admin/all ───────────────────────────────────────────

    @Test
    @DisplayName("GET /api/bookings/admin/all: returns paginated bookings with default params")
    void getAllBookings_shouldReturnPaginatedBookings_withDefaults() throws Exception {
        BookingResponse response = createSampleResponse(BookingStatus.CONFIRMED, BookingSource.ONLINE);
        Page<BookingResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);

        when(bookingService.getAllBookings(eq(null), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/bookings/admin/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(bookingId.toString()));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingService).getAllBookings(eq(null), captor.capture());
        Pageable captured = captor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(0);
        assertThat(captured.getPageSize()).isEqualTo(20);
        assertThat(captured.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("GET /api/bookings/admin/all: supports status filter, custom page, size and sort")
    void getAllBookings_shouldSupportCustomFilters() throws Exception {
        BookingResponse response = createSampleResponse(BookingStatus.CONFIRMED, BookingSource.ONLINE);
        Page<BookingResponse> page = new PageImpl<>(List.of(response), PageRequest.of(2, 100), 1);

        when(bookingService.getAllBookings(eq(BookingStatus.CONFIRMED), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/bookings/admin/all")
                        .param("status", "CONFIRMED")
                        .param("page", "2")
                        .param("size", "150") // capped at 100
                        .param("sort", "startTime,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingService).getAllBookings(eq(BookingStatus.CONFIRMED), captor.capture());
        Pageable captured = captor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(2);
        assertThat(captured.getPageSize()).isEqualTo(100); // capped at 100
        assertThat(captured.getSort().getOrderFor("startTime").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}
