package com.gamingcastle.tournamentservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private final RestTemplate restTemplate;
    private final String BASE_URL = "http://localhost:8086/api/notifications";

    public NotificationClient() {
        this.restTemplate = new RestTemplate();
    }

    public void sendTournamentRegistrationConfirmed(TournamentRegistrationEmailRequest request) {
        try {
            restTemplate.postForEntity(BASE_URL + "/tournament-registration-confirmed", request, Void.class);
            log.info("Sent tournament registration confirmed email to {}", request.email());
        } catch (Exception e) {
            log.error("Failed to send tournament registration confirmed email to {}: {}", request.email(), e.getMessage());
        }
    }

    public void sendTournamentCancelled(TournamentCancelledEmailRequest request) {
        try {
            restTemplate.postForEntity(BASE_URL + "/tournament-cancelled", request, Void.class);
            log.info("Sent tournament cancelled email to {}", request.email());
        } catch (Exception e) {
            log.error("Failed to send tournament cancelled email to {}: {}", request.email(), e.getMessage());
        }
    }

    public void sendTournamentResultsPublished(TournamentResultsEmailRequest request) {
        try {
            restTemplate.postForEntity(BASE_URL + "/tournament-results-published", request, Void.class);
            log.info("Sent tournament results published email to {}", request.email());
        } catch (Exception e) {
            log.error("Failed to send tournament results published email to {}: {}", request.email(), e.getMessage());
        }
    }

    public void sendTournamentReminder(TournamentReminderEmailRequest request) {
        try {
            restTemplate.postForEntity(BASE_URL + "/tournament-reminder", request, Void.class);
            log.info("Sent tournament reminder email to {}", request.email());
        } catch (Exception e) {
            log.error("Failed to send tournament reminder email to {}: {}", request.email(), e.getMessage());
        }
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
