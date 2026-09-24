package com.gamingcastle.bookingservice.service.controller;

import com.gamingcastle.bookingservice.dto.BookingRequest;
import com.gamingcastle.bookingservice.dto.BookingResponse;
import com.gamingcastle.bookingservice.dto.WalkInBookingRequest;
import com.gamingcastle.bookingservice.entity.GameStation;
import com.gamingcastle.bookingservice.repository.GameStationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real Gateway-header-trust model: no JWT is involved here
 * (that's api-gateway's job) — these tests set X-User-Id/X-User-Role
 * directly on the request, exactly as GatewayHeaderAuthFilter expects a
 * request arriving from the Gateway to look. Requires the `test` Spring
 * profile (application-test.properties) and Flyway's seed data (4 game
 * stations, including PC-01) to have run — see CI-2 in the foundation plan.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private GameStationRepository gameStationRepository;

    private GameStation station;
    private UUID customerId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        station = gameStationRepository.findAll().stream()
                .filter(s -> "PC-01".equals(s.getStationCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Seed station PC-01 not found - check V1__create_booking_tables.sql ran against the test DB"));
        customerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    private HttpHeaders headersFor(UUID userId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId.toString());
        headers.set("X-User-Role", role);
        return headers;
    }

    @Test
    void postBookings_asCustomer_shouldSucceed() {
        // Arrange
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), start, end);
        HttpEntity<BookingRequest> entity = new HttpEntity<>(request, headersFor(customerId, "CUSTOMER"));

        // Act
        ResponseEntity<BookingResponse> response =
                restTemplate.postForEntity("/api/bookings", entity, BookingResponse.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo(customerId);
    }

    @Test
    void postWalkIn_asCustomer_shouldReturn403() {
        // Arrange
        Instant start = Instant.now().plus(11, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        WalkInBookingRequest request = new WalkInBookingRequest(customerId, station.getId(), start, end);
        HttpEntity<WalkInBookingRequest> entity = new HttpEntity<>(request, headersFor(customerId, "CUSTOMER"));

        // Act
        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/bookings/walk-in", entity, String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void postWalkIn_asAdmin_shouldSucceed() {
        // Arrange
        Instant start = Instant.now().plus(12, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        WalkInBookingRequest request = new WalkInBookingRequest(customerId, station.getId(), start, end);
        HttpEntity<WalkInBookingRequest> entity = new HttpEntity<>(request, headersFor(adminId, "ADMIN"));

        // Act
        ResponseEntity<BookingResponse> response =
                restTemplate.postForEntity("/api/bookings/walk-in", entity, BookingResponse.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo(customerId); // booked FOR the customer, not the admin
    }

    @Test
    void cancelBooking_asDifferentCustomer_shouldReturn403() {
        // Arrange — create a booking as customerId
        Instant start = Instant.now().plus(13, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), start, end);
        HttpEntity<BookingRequest> createEntity = new HttpEntity<>(request, headersFor(customerId, "CUSTOMER"));
        ResponseEntity<BookingResponse> createResponse =
                restTemplate.postForEntity("/api/bookings", createEntity, BookingResponse.class);
        UUID bookingId = createResponse.getBody().id();

        UUID otherCustomerId = UUID.randomUUID();
        HttpEntity<Void> cancelEntity = new HttpEntity<>(headersFor(otherCustomerId, "CUSTOMER"));

        // Act
        ResponseEntity<String> cancelResponse = restTemplate.exchange(
                "/api/bookings/" + bookingId + "/cancel", HttpMethod.PATCH, cancelEntity, String.class);

        // Assert
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cancelBooking_asAdmin_shouldSucceed_evenForSomeoneElsesBooking() {
        // Arrange
        Instant start = Instant.now().plus(14, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), start, end);
        HttpEntity<BookingRequest> createEntity = new HttpEntity<>(request, headersFor(customerId, "CUSTOMER"));
        ResponseEntity<BookingResponse> createResponse =
                restTemplate.postForEntity("/api/bookings", createEntity, BookingResponse.class);
        UUID bookingId = createResponse.getBody().id();

        HttpEntity<Void> cancelEntity = new HttpEntity<>(headersFor(adminId, "ADMIN"));

        // Act
        ResponseEntity<BookingResponse> cancelResponse = restTemplate.exchange(
                "/api/bookings/" + bookingId + "/cancel", HttpMethod.PATCH, cancelEntity, BookingResponse.class);

        // Assert
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getMyBookings_shouldReturnOnlyCallersBookings() {
        // Arrange
        Instant start = Instant.now().plus(15, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = new BookingRequest(station.getId(), start, end);
        HttpEntity<BookingRequest> createEntity = new HttpEntity<>(request, headersFor(customerId, "CUSTOMER"));
        restTemplate.postForEntity("/api/bookings", createEntity, BookingResponse.class);

        HttpEntity<Void> getEntity = new HttpEntity<>(headersFor(customerId, "CUSTOMER"));

        // Act
        ResponseEntity<BookingResponse[]> response = restTemplate.exchange(
                "/api/bookings/mine", HttpMethod.GET, getEntity, BookingResponse[].class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody())
                .allSatisfy(booking -> assertThat(booking.userId()).isEqualTo(customerId));
    }

    @Test
    void anyEndpoint_withoutIdentityHeaders_shouldReturn401() {
        HttpEntity<Void> entity = new HttpEntity<>(new HttpHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/bookings/mine", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
