package com.gamingcastle.bookingservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private static final String SERVICE_USER_ID = "00000000-0000-0000-0000-000000000002";
    private final RestTemplate restTemplate;

    @Value("${services.notification.base-url:http://localhost:8086}")
    private String notificationBaseUrl;

    @Value("${services.socket-server.base-url:http://localhost:5001}")
    private String socketServerBaseUrl;

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    public NotificationClient() {
        this.restTemplate = new RestTemplate();
    }

    public void sendBookingConfirmation(BookingEmailRequest request) {
        try {
            send("/booking-confirmed", request);
        } catch (Exception e) {
            log.error("Failed to send booking confirmation email for bookingId={}", request.bookingId(), e);
        }
    }

    public void sendBookingRescheduled(BookingEmailRequest request) {
        try {
            send("/booking-rescheduled", request);
        } catch (Exception e) {
            log.error("Failed to send booking rescheduled email for bookingId={}", request.bookingId(), e);
        }
    }

    public void sendBookingCancelled(BookingEmailRequest request) {
        try {
            send("/booking-cancelled", request);
        } catch (Exception e) {
            log.error("Failed to send booking cancelled email for bookingId={}", request.bookingId(), e);
        }
    }

    private void send(String path, BookingEmailRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", gatewaySecret);
        headers.set("X-User-Id", SERVICE_USER_ID);
        headers.set("X-User-Role", "SERVICE");
        restTemplate.postForEntity(
                notificationBaseUrl + "/api/notifications" + path,
                new HttpEntity<>(request, headers),
                Void.class);
    }

    /**
     * Bridges notifications directly to Node.js Socket.IO server on port 5001.
     * Instant real-time UI toast and badge updates for admin and customers.
     */
    public void broadcastSocketNotification(SocketNotificationPayload payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(
                    socketServerBaseUrl + "/api/notify",
                    new HttpEntity<>(payload, headers),
                    Void.class);
            log.info("Pushed real-time socket notification: title='{}', target={}", payload.title(), payload.targetUserId());
        } catch (Exception e) {
            log.warn("Could not push socket notification to {}: {}", socketServerBaseUrl, e.getMessage());
        }
    }

    /**
     * Pushes real-time slot lock/unlock/booked events directly to Socket.IO.
     * Prevents page refreshes across all connected customer and admin browsers.
     */
    public void broadcastSlotEvent(java.util.UUID stationId, String date, String timeSlot, String event) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            SlotEventPayload payload = new SlotEventPayload(event, stationId.toString(), date, timeSlot);
            restTemplate.postForEntity(
                    socketServerBaseUrl + "/api/slots/event",
                    new HttpEntity<>(payload, headers),
                    Void.class);
            log.info("Pushed real-time slot event: event='{}', station={}, slot={} {}", event, stationId, date, timeSlot);
        } catch (Exception e) {
            log.warn("Could not push slot event to {}: {}", socketServerBaseUrl, e.getMessage());
        }
    }

    public record BookingEmailRequest(
            String email,
            String customerName,
            String bookingId,
            String gameStationName,
            String bookingDate,
            String timeSlot,
            String amount
    ) {}

    public record SocketNotificationPayload(
            String type,
            String category,
            String title,
            String message,
            String referenceId,
            String link,
            String targetUserId,
            boolean broadcast
    ) {}

    public record SlotEventPayload(
            String event, // 'BOOKED' or 'RELEASED'
            String stationId,
            String date,
            String timeSlot
    ) {}
}
