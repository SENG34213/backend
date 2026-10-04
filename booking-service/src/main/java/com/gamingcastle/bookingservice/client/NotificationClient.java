package com.gamingcastle.bookingservice.client;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class NotificationClient {

    private final RestTemplate restTemplate;

    public NotificationClient() {
        this.restTemplate = new RestTemplate();
    }

    // A simpler way without needing full eureka load balancing for direct call, 
    // but ideally we'd use @LoadBalanced RestTemplate. For now, since gateway is on 8080 
    // or notification is on 8086 locally, we'll hardcode localhost:8086 for the example.
    private final String BASE_URL = "http://localhost:8086/api/notifications";

    public void sendBookingConfirmation(BookingEmailRequest request) {
        try {
            restTemplate.postForEntity(BASE_URL + "/booking-confirmed", request, Void.class);
        } catch (Exception e) {
            System.err.println("Failed to send booking confirmation email: " + e.getMessage());
        }
    }

    public void sendBookingRescheduled(BookingEmailRequest request) {
        try {
            restTemplate.postForEntity(BASE_URL + "/booking-rescheduled", request, Void.class);
        } catch (Exception e) {
            System.err.println("Failed to send booking rescheduled email: " + e.getMessage());
        }
    }

    public void sendBookingCancelled(BookingEmailRequest request) {
        try {
            restTemplate.postForEntity(BASE_URL + "/booking-cancelled", request, Void.class);
        } catch (Exception e) {
            System.err.println("Failed to send booking cancelled email: " + e.getMessage());
        }
    }

    public record BookingEmailRequest(
            String email,
            String customerName,
            String bookingId,
            String gameStationName,
            String bookingDate,
            String timeSlot
    ) {}
}
