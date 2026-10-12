package com.gamingcastle.tournamentservice.client;

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
    private static final String SERVICE_USER_ID = "00000000-0000-0000-0000-000000000003";
    private final RestTemplate restTemplate;

    @Value("${services.notification.base-url:http://localhost:8086}")
    private String notificationBaseUrl;

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    public NotificationClient() {
        this.restTemplate = new RestTemplate();
    }

    public void sendTournamentRegistrationConfirmed(TournamentRegistrationEmailRequest request) {
        try {
            send("/tournament-registration-confirmed", request);
            log.info("Sent tournament registration confirmed email to {}", request.email());
        } catch (Exception e) {
            log.error("Failed to send tournament registration confirmed email to {}: {}", request.email(), e.getMessage());
        }
    }

    public void sendTournamentCancelled(TournamentCancelledEmailRequest request) {
        try {
            send("/tournament-cancelled", request);
            log.info("Sent tournament cancelled email to {}", request.email());
        } catch (Exception e) {
            log.error("Failed to send tournament cancelled email to {}: {}", request.email(), e.getMessage());
        }
    }

    public void sendTournamentResultsPublished(TournamentResultsEmailRequest request) {
        try {
            send("/tournament-results-published", request);
            log.info("Sent tournament results published email to {}", request.email());
        } catch (Exception e) {
            log.error("Failed to send tournament results published email to {}: {}", request.email(), e.getMessage());
        }
    }

    public void sendTournamentReminder(TournamentReminderEmailRequest request) {
        try {
            send("/tournament-reminder", request);
            log.info("Sent tournament reminder email to {}", request.email());
        } catch (Exception e) {
            log.error("Failed to send tournament reminder email to {}: {}", request.email(), e.getMessage());
        }
    }

    private void send(String path, Object request) {
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

    public record TournamentRegistrationEmailRequest(
            String email,
            String customerName,
            String tournamentName,
            String gameTitle,
            String startDate,
            String entryFee
    ) {}

    public record TournamentCancelledEmailRequest(
            String email,
            String customerName,
            String tournamentName,
            String reason
    ) {}

    public record TournamentResultsEmailRequest(
            String email,
            String customerName,
            String tournamentName,
            String gameTitle,
            String winnerName,
            String message
    ) {}

    public record TournamentReminderEmailRequest(
            String email,
            String customerName,
            String tournamentName,
            String gameTitle,
            String startDate,
            String opponentName,
            String roundInfo
    ) {}
}
