package com.gamingcastle.notificationservice.controller;

import com.gamingcastle.notificationservice.dto.*;
import com.gamingcastle.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService);
    }

    @Test
    @DisplayName("sendWelcomeEmail: delegates to notificationService and returns 200 OK")
    void sendWelcomeEmail_shouldReturnOk() {
        WelcomeEmailRequest req = new WelcomeEmailRequest("user@test.com", "User");
        ResponseEntity<Void> res = controller.sendWelcomeEmail(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendWelcomeEmail(req);
    }

    @Test
    @DisplayName("sendPasswordResetEmail: delegates to notificationService and returns 200 OK")
    void sendPasswordResetEmail_shouldReturnOk() {
        PasswordResetEmailRequest req = new PasswordResetEmailRequest("user@test.com", "123456");
        ResponseEntity<Void> res = controller.sendPasswordResetEmail(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendPasswordResetEmail(req);
    }

    @Test
    @DisplayName("sendAccountReactivationEmail: delegates to notificationService and returns 200 OK")
    void sendAccountReactivationEmail_shouldReturnOk() {
        AccountReactivationEmailRequest req = new AccountReactivationEmailRequest("user@test.com", "654321");
        ResponseEntity<Void> res = controller.sendAccountReactivationEmail(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendAccountReactivationEmail(req);
    }

    @Test
    @DisplayName("sendBookingConfirmation: delegates to notificationService and returns 200 OK")
    void sendBookingConfirmation_shouldReturnOk() {
        BookingEmailRequest req = new BookingEmailRequest("user@test.com", "User", "1", "PC-01", "2026-10-20", "10:00", "500");
        ResponseEntity<Void> res = controller.sendBookingConfirmation(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendBookingConfirmationEmail(req);
    }

    @Test
    @DisplayName("sendBookingRescheduled: delegates to notificationService and returns 200 OK")
    void sendBookingRescheduled_shouldReturnOk() {
        BookingEmailRequest req = new BookingEmailRequest("user@test.com", "User", "1", "PC-01", "2026-10-21", "12:00", "500");
        ResponseEntity<Void> res = controller.sendBookingRescheduled(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendBookingRescheduledEmail(req);
    }

    @Test
    @DisplayName("sendBookingCancelled: delegates to notificationService and returns 200 OK")
    void sendBookingCancelled_shouldReturnOk() {
        BookingEmailRequest req = new BookingEmailRequest("user@test.com", "User", "1", "PC-01", "2026-10-20", "10:00", "500");
        ResponseEntity<Void> res = controller.sendBookingCancelled(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendBookingCancelledEmail(req);
    }

    @Test
    @DisplayName("sendTournamentRegistrationConfirmation: delegates to notificationService and returns 200 OK")
    void sendTournamentRegistrationConfirmation_shouldReturnOk() {
        TournamentRegistrationEmailRequest req = new TournamentRegistrationEmailRequest("user@test.com", "User", "Tourney", "Game", "Date", "Fee");
        ResponseEntity<Void> res = controller.sendTournamentRegistrationConfirmation(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendTournamentRegistrationEmail(req);
    }

    @Test
    @DisplayName("sendTournamentCancelled: delegates to notificationService and returns 200 OK")
    void sendTournamentCancelled_shouldReturnOk() {
        TournamentCancelledEmailRequest req = new TournamentCancelledEmailRequest("user@test.com", "User", "Tourney", "Reason");
        ResponseEntity<Void> res = controller.sendTournamentCancelled(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendTournamentCancelledEmail(req);
    }

    @Test
    @DisplayName("sendTournamentResults: delegates to notificationService and returns 200 OK")
    void sendTournamentResults_shouldReturnOk() {
        TournamentResultsEmailRequest req = new TournamentResultsEmailRequest("user@test.com", "User", "Tourney", "Game", "Winner", "Msg");
        ResponseEntity<Void> res = controller.sendTournamentResults(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendTournamentResultsEmail(req);
    }

    @Test
    @DisplayName("sendTournamentReminder: delegates to notificationService and returns 200 OK")
    void sendTournamentReminder_shouldReturnOk() {
        TournamentReminderEmailRequest req = new TournamentReminderEmailRequest("user@test.com", "User", "Tourney", "Game", "Date", "Opponent", "Round");
        ResponseEntity<Void> res = controller.sendTournamentReminder(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendTournamentReminderEmail(req);
    }

    @Test
    @DisplayName("sendPasswordResetSms: delegates to notificationService and returns 200 OK")
    void sendPasswordResetSms_shouldReturnOk() {
        PasswordResetSmsRequest req = new PasswordResetSmsRequest("+94771234567", "123456");
        ResponseEntity<Void> res = controller.sendPasswordResetSms(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendPasswordResetSms(req);
    }

    @Test
    @DisplayName("sendAccountReactivationSms: delegates to notificationService and returns 200 OK")
    void sendAccountReactivationSms_shouldReturnOk() {
        AccountReactivationSmsRequest req = new AccountReactivationSmsRequest("+94771234567", "654321");
        ResponseEntity<Void> res = controller.sendAccountReactivationSms(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).sendAccountReactivationSms(req);
    }
}
