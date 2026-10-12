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

    public record BookingEmailRequest(
            String email,
            String customerName,
            String bookingId,
            String gameStationName,
            String bookingDate,
            String timeSlot,
            String amount
    ) {}
}
