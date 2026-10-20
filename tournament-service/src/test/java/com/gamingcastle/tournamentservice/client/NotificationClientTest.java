package com.gamingcastle.tournamentservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NotificationClientTest {

    private static final String BASE_URL = "http://localhost:8086";
    private static final String SECRET = "test-secret";

    private NotificationClient notificationClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        notificationClient = new NotificationClient();
        RestTemplate restTemplate = new RestTemplate();
        ReflectionTestUtils.setField(notificationClient, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(notificationClient, "notificationBaseUrl", BASE_URL);
        ReflectionTestUtils.setField(notificationClient, "gatewaySecret", SECRET);

        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    @DisplayName("sendTournamentRegistrationConfirmed sends POST request with service headers")
    void sendTournamentRegistrationConfirmed_shouldSendRequest() {
        mockServer.expect(requestTo("http://localhost:8086/api/notifications/tournament-registration-confirmed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", SECRET))
                .andExpect(header("X-User-Role", "SERVICE"))
                .andRespond(withSuccess());

        NotificationClient.TournamentRegistrationEmailRequest request =
                new NotificationClient.TournamentRegistrationEmailRequest(
                        "user@test.com", "Gamer", "FC Cup", "FC 26", "2026-11-01 10:00", "LKR 1000"
                );

        notificationClient.sendTournamentRegistrationConfirmed(request);
        mockServer.verify();
    }

    @Test
    @DisplayName("sendTournamentCancelled sends POST request with service headers")
    void sendTournamentCancelled_shouldSendRequest() {
        mockServer.expect(requestTo("http://localhost:8086/api/notifications/tournament-cancelled"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", SECRET))
                .andExpect(header("X-User-Role", "SERVICE"))
                .andRespond(withSuccess());

        NotificationClient.TournamentCancelledEmailRequest request =
                new NotificationClient.TournamentCancelledEmailRequest(
                        "user@test.com", "Gamer", "FC Cup", "Admin cancelled"
                );

        notificationClient.sendTournamentCancelled(request);
        mockServer.verify();
    }

    @Test
    @DisplayName("sendTournamentResultsPublished sends POST request with service headers")
    void sendTournamentResultsPublished_shouldSendRequest() {
        mockServer.expect(requestTo("http://localhost:8086/api/notifications/tournament-results-published"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", SECRET))
                .andExpect(header("X-User-Role", "SERVICE"))
                .andRespond(withSuccess());

        NotificationClient.TournamentResultsEmailRequest request =
                new NotificationClient.TournamentResultsEmailRequest(
                        "user@test.com", "Gamer", "FC Cup", "FC 26", "Winner", "Congrats"
                );

        notificationClient.sendTournamentResultsPublished(request);
        mockServer.verify();
    }

    @Test
    @DisplayName("sendTournamentReminder sends POST request with service headers")
    void sendTournamentReminder_shouldSendRequest() {
        mockServer.expect(requestTo("http://localhost:8086/api/notifications/tournament-reminder"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", SECRET))
                .andExpect(header("X-User-Role", "SERVICE"))
                .andRespond(withSuccess());

        NotificationClient.TournamentReminderEmailRequest request =
                new NotificationClient.TournamentReminderEmailRequest(
                        "user@test.com", "Gamer", "FC Cup", "FC 26", "2026-11-01 10:00", "Opponent", "Round 1"
                );

        notificationClient.sendTournamentReminder(request);
        mockServer.verify();
    }

    @Test
    @DisplayName("Notification methods should catch exception safely when remote service fails")
    void sendMethods_shouldHandleExceptionsSafely() {
        mockServer.expect(requestTo("http://localhost:8086/api/notifications/tournament-reminder"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        NotificationClient.TournamentReminderEmailRequest request =
                new NotificationClient.TournamentReminderEmailRequest(
                        "user@test.com", "Gamer", "FC Cup", "FC 26", "2026-11-01 10:00", "Opponent", "Round 1"
                );

        // Does not throw exception
        notificationClient.sendTournamentReminder(request);
        mockServer.verify();
    }
}
