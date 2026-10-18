package com.gamingcastle.notificationservice.controller;

import com.gamingcastle.notificationservice.dto.*;
import com.gamingcastle.notificationservice.entity.Notification;
import com.gamingcastle.notificationservice.enums.NotificationStatus;
import com.gamingcastle.notificationservice.enums.NotificationType;
import com.gamingcastle.notificationservice.repository.NotificationRepository;
import com.gamingcastle.notificationservice.service.SmsService;
import com.gamingcastle.notificationservice.service.SocketNotificationBroadcaster;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationControllerIntegrationTest {

    private static final String GATEWAY_SECRET = "test-gateway-internal-secret";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    private SmsService smsService;

    @MockBean
    private SocketNotificationBroadcaster socketNotificationBroadcaster;

    private UUID serviceUserId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        serviceUserId = UUID.randomUUID();

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    private HttpHeaders serviceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", GATEWAY_SECRET);
        headers.set("X-User-Id", serviceUserId.toString());
        headers.set("X-User-Role", "SERVICE");
        return headers;
    }

    // ── Security & Authentication ─────────────────────────────────────────────

    @Test
    @DisplayName("Security: rejects requests missing X-Gateway-Secret header with 401 Unauthorized")
    void shouldReturn401_whenGatewaySecretMissing() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", serviceUserId.toString());
        headers.set("X-User-Role", "SERVICE");

        WelcomeEmailRequest request = new WelcomeEmailRequest("user@example.com", "User");
        HttpEntity<WelcomeEmailRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/notifications/welcome", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Security: rejects requests with non-SERVICE role with 403 Forbidden")
    void shouldReturn403_whenRoleIsNotService() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", GATEWAY_SECRET);
        headers.set("X-User-Id", serviceUserId.toString());
        headers.set("X-User-Role", "CUSTOMER");

        WelcomeEmailRequest request = new WelcomeEmailRequest("user@example.com", "User");
        HttpEntity<WelcomeEmailRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/notifications/welcome", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Email Endpoint Integration Tests ──────────────────────────────────────

    @Test
    @DisplayName("POST /api/notifications/welcome: sends welcome email and persists record")
    void sendWelcomeEmail_shouldSucceed() {
        WelcomeEmailRequest request = new WelcomeEmailRequest("gamer@example.com", "John Player");
        HttpEntity<WelcomeEmailRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/welcome", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.WELCOME_EMAIL.name());
        assertThat(records.get(0).getRecipientEmail()).isEqualTo("gamer@example.com");
        assertThat(records.get(0).getStatus()).isEqualTo(NotificationStatus.SENT.name());
    }

    @Test
    @DisplayName("POST /api/notifications/password-reset: sends password reset email and persists record")
    void sendPasswordResetEmail_shouldSucceed() {
        PasswordResetEmailRequest request = new PasswordResetEmailRequest("gamer@example.com", "123456");
        HttpEntity<PasswordResetEmailRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/password-reset", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.PASSWORD_RESET_EMAIL.name());
        assertThat(records.get(0).getStatus()).isEqualTo(NotificationStatus.SENT.name());
    }

    @Test
    @DisplayName("POST /api/notifications/account-reactivation: sends reactivation email and persists record")
    void sendAccountReactivationEmail_shouldSucceed() {
        AccountReactivationEmailRequest request = new AccountReactivationEmailRequest("gamer@example.com", "654321");
        HttpEntity<AccountReactivationEmailRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/account-reactivation", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.ACCOUNT_REACTIVATION_EMAIL.name());
    }

    @Test
    @DisplayName("POST /api/notifications/booking-confirmed: sends confirmation email and persists record")
    void sendBookingConfirmation_shouldSucceed() {
        BookingEmailRequest request = new BookingEmailRequest(
                "gamer@example.com", "John", "BKG-001", "PC-01", "2026-10-25", "10:00 - 12:00", "LKR 600"
        );
        HttpEntity<BookingEmailRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/booking-confirmed", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.BOOKING_CONFIRMATION.name());
    }

    @Test
    @DisplayName("POST /api/notifications/booking-rescheduled: sends rescheduled email and persists record")
    void sendBookingRescheduled_shouldSucceed() {
        BookingEmailRequest request = new BookingEmailRequest(
                "gamer@example.com", "John", "BKG-001", "PC-02", "2026-10-26", "14:00 - 16:00", "LKR 600"
        );
        HttpEntity<BookingEmailRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/booking-rescheduled", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.BOOKING_RESCHEDULED.name());
    }

    @Test
    @DisplayName("POST /api/notifications/booking-cancelled: sends cancellation email and persists record")
    void sendBookingCancelled_shouldSucceed() {
        BookingEmailRequest request = new BookingEmailRequest(
                "gamer@example.com", "John", "BKG-001", "PC-01", "2026-10-25", "10:00 - 12:00", "LKR 600"
        );
        HttpEntity<BookingEmailRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/booking-cancelled", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.BOOKING_CANCELLED.name());
    }

    @Test
    @DisplayName("POST /api/notifications/tournament-registration-confirmed: sends email and persists record")
    void sendTournamentRegistrationConfirmation_shouldSucceed() {
        TournamentRegistrationEmailRequest request = new TournamentRegistrationEmailRequest(
                "gamer@example.com", "John", "Valorant Cup", "Valorant", "2026-11-01", "LKR 1,000"
        );
        HttpEntity<TournamentRegistrationEmailRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/tournament-registration-confirmed", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.TOURNAMENT_REGISTRATION_CONFIRMED.name());
    }

    @Test
    @DisplayName("POST /api/notifications/tournament-cancelled: sends email and persists record")
    void sendTournamentCancelled_shouldSucceed() {
        TournamentCancelledEmailRequest request = new TournamentCancelledEmailRequest(
                "gamer@example.com", "John", "Valorant Cup", "Organizer cancelled"
        );
        HttpEntity<TournamentCancelledEmailRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/tournament-cancelled", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.TOURNAMENT_CANCELLED.name());
    }

    @Test
    @DisplayName("POST /api/notifications/tournament-results-published: sends email and persists record")
    void sendTournamentResults_shouldSucceed() {
        TournamentResultsEmailRequest request = new TournamentResultsEmailRequest(
                "gamer@example.com", "John", "Valorant Cup", "Valorant", "Team Alpha", "Well played!"
        );
        HttpEntity<TournamentResultsEmailRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/tournament-results-published", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.TOURNAMENT_RESULTS_PUBLISHED.name());
    }

    @Test
    @DisplayName("POST /api/notifications/tournament-reminder: sends email and persists record")
    void sendTournamentReminder_shouldSucceed() {
        TournamentReminderEmailRequest request = new TournamentReminderEmailRequest(
                "gamer@example.com", "John", "Valorant Cup", "Valorant", "2026-11-01 10:00", "Team Beta", "Finals"
        );
        HttpEntity<TournamentReminderEmailRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/tournament-reminder", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.TOURNAMENT_REMINDER.name());
    }

    // ── SMS Endpoint Integration Tests ────────────────────────────────────────

    @Test
    @DisplayName("POST /api/notifications/password-reset-sms: sends reset SMS and persists record")
    void sendPasswordResetSms_shouldSucceed() {
        PasswordResetSmsRequest request = new PasswordResetSmsRequest("+94771234567", "123456");
        HttpEntity<PasswordResetSmsRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/password-reset-sms", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.PASSWORD_RESET_SMS.name());
        assertThat(records.get(0).getRecipientEmail()).isEqualTo("+94771234567");
    }

    @Test
    @DisplayName("POST /api/notifications/account-reactivation-sms: sends reactivation SMS and persists record")
    void sendAccountReactivationSms_shouldSucceed() {
        AccountReactivationSmsRequest request = new AccountReactivationSmsRequest("+94771234567", "654321");
        HttpEntity<AccountReactivationSmsRequest> entity = new HttpEntity<>(request, serviceHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/notifications/account-reactivation-sms", entity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Notification> records = notificationRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getNotificationType()).isEqualTo(NotificationType.ACCOUNT_REACTIVATION_SMS.name());
    }
}
