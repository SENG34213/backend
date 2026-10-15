package com.gamingcastle.bookingservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationClientTest {

    private static final String BASE_URL = "http://localhost:8086";
    private static final String GATEWAY_SECRET = "test-gateway-secret-xyz";
    private static final String SERVICE_USER_ID = "00000000-0000-0000-0000-000000000002";

    private NotificationClient notificationClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        notificationClient = new NotificationClient();
        ReflectionTestUtils.setField(notificationClient, "notificationBaseUrl", BASE_URL);
        ReflectionTestUtils.setField(notificationClient, "gatewaySecret", GATEWAY_SECRET);

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(notificationClient, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    private NotificationClient.BookingEmailRequest createSampleRequest() {
        return new NotificationClient.BookingEmailRequest(
                "player@example.com",
                "Test Player",
                "b1111111-1111-1111-1111-111111111111",
                "PC-01",
                "2026-10-15",
                "10:00",
                "LKR 600.00"
        );
    }

    @Test
    @DisplayName("sendBookingConfirmation: dispatches POST with gateway headers and body")
    void sendBookingConfirmation_shouldDispatchPost() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/booking-confirmed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(header("X-User-Id", SERVICE_USER_ID))
                .andExpect(header("X-User-Role", "SERVICE"))
                .andExpect(jsonPath("$.email").value("player@example.com"))
                .andExpect(jsonPath("$.customerName").value("Test Player"))
                .andExpect(jsonPath("$.gameStationName").value("PC-01"))
                .andRespond(withSuccess());

        notificationClient.sendBookingConfirmation(createSampleRequest());
        mockServer.verify();
    }

    @Test
    @DisplayName("sendBookingRescheduled: dispatches POST with gateway headers and body")
    void sendBookingRescheduled_shouldDispatchPost() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/booking-rescheduled"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(header("X-User-Id", SERVICE_USER_ID))
                .andExpect(header("X-User-Role", "SERVICE"))
                .andExpect(jsonPath("$.bookingId").value("b1111111-1111-1111-1111-111111111111"))
                .andRespond(withSuccess());

        notificationClient.sendBookingRescheduled(createSampleRequest());
        mockServer.verify();
    }

    @Test
    @DisplayName("sendBookingCancelled: dispatches POST with gateway headers and body")
    void sendBookingCancelled_shouldDispatchPost() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/booking-cancelled"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(header("X-User-Id", SERVICE_USER_ID))
                .andExpect(header("X-User-Role", "SERVICE"))
                .andExpect(jsonPath("$.timeSlot").value("10:00"))
                .andRespond(withSuccess());

        notificationClient.sendBookingCancelled(createSampleRequest());
        mockServer.verify();
    }

    @Test
    @DisplayName("sendBookingConfirmation: swallows network error without throwing exception")
    void sendBookingConfirmation_shouldHandleExceptionGracefully() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/booking-confirmed"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatCode(() -> notificationClient.sendBookingConfirmation(createSampleRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendBookingRescheduled: swallows network error without throwing exception")
    void sendBookingRescheduled_shouldHandleExceptionGracefully() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/booking-rescheduled"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatCode(() -> notificationClient.sendBookingRescheduled(createSampleRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendBookingCancelled: swallows network error without throwing exception")
    void sendBookingCancelled_shouldHandleExceptionGracefully() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/booking-cancelled"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThatCode(() -> notificationClient.sendBookingCancelled(createSampleRequest()))
                .doesNotThrowAnyException();
    }
}
