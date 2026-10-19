package com.gamingcastle.notificationservice.service;

import com.gamingcastle.notificationservice.dto.*;
import com.gamingcastle.notificationservice.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private SmsService smsService;

    @Mock
    private NotificationRecordService notificationRecordService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private SocketNotificationBroadcaster socketNotificationBroadcaster;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                emailSenderService,
                smsService,
                notificationRecordService,
                emailTemplateService,
                socketNotificationBroadcaster
        );
    }

    // ── Email Notifications ───────────────────────────────────────────────────

    @Test
    @DisplayName("sendWelcomeEmail: creates template, sends email, and records success")
    void sendWelcomeEmail_shouldSendAndRecordSuccess_whenValid() {
        WelcomeEmailRequest request = new WelcomeEmailRequest("gamer@example.com", "John Doe");
        EmailContent emailContent = new EmailContent("Welcome", "<p>Welcome</p>");

        when(emailTemplateService.createWelcomeEmail(request)).thenReturn(emailContent);

        notificationService.sendWelcomeEmail(request);

        verify(emailSenderService).sendHtmlEmail(request.email(), emailContent.subject(), emailContent.htmlBody());
        verify(notificationRecordService).recordSuccess(NotificationType.WELCOME_EMAIL, request.email());
    }

    @Test
    @DisplayName("sendWelcomeEmail: records failure when email sending throws exception")
    void sendWelcomeEmail_shouldRecordFailure_whenEmailSenderFails() {
        WelcomeEmailRequest request = new WelcomeEmailRequest("gamer@example.com", "John Doe");
        EmailContent emailContent = new EmailContent("Welcome", "<p>Welcome</p>");

        when(emailTemplateService.createWelcomeEmail(request)).thenReturn(emailContent);
        doThrow(new RuntimeException("SMTP connection refused"))
                .when(emailSenderService).sendHtmlEmail(anyString(), anyString(), anyString());

        notificationService.sendWelcomeEmail(request);

        verify(notificationRecordService).recordFailure(
                eq(NotificationType.WELCOME_EMAIL),
                eq(request.email()),
                eq("SMTP connection refused")
        );
    }

    @Test
    @DisplayName("sendPasswordResetEmail: creates template, sends email, and records success")
    void sendPasswordResetEmail_shouldSendAndRecordSuccess() {
        PasswordResetEmailRequest request = new PasswordResetEmailRequest("gamer@example.com", "123456");
        EmailContent emailContent = new EmailContent("Reset", "<p>123456</p>");

        when(emailTemplateService.createPasswordResetEmail(request)).thenReturn(emailContent);

        notificationService.sendPasswordResetEmail(request);

        verify(emailSenderService).sendHtmlEmail(request.email(), emailContent.subject(), emailContent.htmlBody());
        verify(notificationRecordService).recordSuccess(NotificationType.PASSWORD_RESET_EMAIL, request.email());
    }

    @Test
    @DisplayName("sendAccountReactivationEmail: creates template, sends email, and records success")
    void sendAccountReactivationEmail_shouldSendAndRecordSuccess() {
        AccountReactivationEmailRequest request = new AccountReactivationEmailRequest("gamer@example.com", "654321");
        EmailContent emailContent = new EmailContent("Reactivate", "<p>654321</p>");

        when(emailTemplateService.createAccountReactivationEmail(request)).thenReturn(emailContent);

        notificationService.sendAccountReactivationEmail(request);

        verify(emailSenderService).sendHtmlEmail(request.email(), emailContent.subject(), emailContent.htmlBody());
        verify(notificationRecordService).recordSuccess(NotificationType.ACCOUNT_REACTIVATION_EMAIL, request.email());
    }

    @Test
    @DisplayName("sendBookingConfirmationEmail: sends email and broadcasts booking event to socket")
    void sendBookingConfirmationEmail_shouldSendEmailAndBroadcast() {
        BookingEmailRequest request = new BookingEmailRequest(
                "gamer@example.com", "John", "bkg-100", "PC-01", "2026-10-20", "10:00 - 12:00", "LKR 500"
        );
        EmailContent emailContent = new EmailContent("Booking Confirmed", "<p>Confirmed</p>");

        when(emailTemplateService.createBookingConfirmationEmail(request)).thenReturn(emailContent);

        notificationService.sendBookingConfirmationEmail(request);

        verify(emailSenderService).sendHtmlEmail(request.email(), emailContent.subject(), emailContent.htmlBody());
        verify(notificationRecordService).recordSuccess(NotificationType.BOOKING_CONFIRMATION, request.email());
        verify(socketNotificationBroadcaster).broadcast(
                eq("BOOKING_CREATED"),
                eq("BOOKING"),
                eq("New Booking Confirmed"),
                contains("Station PC-01 booked by John"),
                eq("bkg-100"),
                eq("/admin/bookings")
        );
    }

    @Test
    @DisplayName("sendBookingRescheduledEmail: sends email and broadcasts rescheduled event to socket")
    void sendBookingRescheduledEmail_shouldSendEmailAndBroadcast() {
        BookingEmailRequest request = new BookingEmailRequest(
                "gamer@example.com", "John", "bkg-100", "PC-02", "2026-10-22", "14:00 - 16:00", "LKR 600"
        );
        EmailContent emailContent = new EmailContent("Booking Rescheduled", "<p>Rescheduled</p>");

        when(emailTemplateService.createBookingRescheduledEmail(request)).thenReturn(emailContent);

        notificationService.sendBookingRescheduledEmail(request);

        verify(emailSenderService).sendHtmlEmail(request.email(), emailContent.subject(), emailContent.htmlBody());
        verify(notificationRecordService).recordSuccess(NotificationType.BOOKING_RESCHEDULED, request.email());
        verify(socketNotificationBroadcaster).broadcast(
                eq("BOOKING_RESCHEDULED"),
                eq("RESCHEDULE"),
                eq("Booking Rescheduled"),
                contains("rescheduled to 14:00 - 16:00"),
                eq("bkg-100"),
                eq("/admin/bookings")
        );
    }

    @Test
    @DisplayName("sendBookingCancelledEmail: sends email and broadcasts cancellation event to socket")
    void sendBookingCancelledEmail_shouldSendEmailAndBroadcast() {
        BookingEmailRequest request = new BookingEmailRequest(
                "gamer@example.com", "John", "bkg-100", "PC-01", "2026-10-20", "10:00 - 12:00", "LKR 500"
        );
        EmailContent emailContent = new EmailContent("Booking Cancelled", "<p>Cancelled</p>");

        when(emailTemplateService.createBookingCancelledEmail(request)).thenReturn(emailContent);

        notificationService.sendBookingCancelledEmail(request);

        verify(emailSenderService).sendHtmlEmail(request.email(), emailContent.subject(), emailContent.htmlBody());
        verify(notificationRecordService).recordSuccess(NotificationType.BOOKING_CANCELLED, request.email());
        verify(socketNotificationBroadcaster).broadcast(
                eq("BOOKING_CANCELLED"),
                eq("CANCEL"),
                eq("Booking Cancelled"),
                contains("was cancelled"),
                eq("bkg-100"),
                eq("/admin/bookings")
        );
    }

    @Test
    @DisplayName("sendTournamentRegistrationEmail: sends email and broadcasts registration event")
    void sendTournamentRegistrationEmail_shouldSendEmailAndBroadcast() {
        TournamentRegistrationEmailRequest request = new TournamentRegistrationEmailRequest(
                "gamer@example.com", "John", "Apex Showdown", "Apex", "2026-11-10", "LKR 500"
        );
        EmailContent emailContent = new EmailContent("Registration Confirmed", "<p>Registered</p>");

        when(emailTemplateService.createTournamentRegistrationEmail(request)).thenReturn(emailContent);

        notificationService.sendTournamentRegistrationEmail(request);

        verify(emailSenderService).sendHtmlEmail(request.email(), emailContent.subject(), emailContent.htmlBody());
        verify(notificationRecordService).recordSuccess(NotificationType.TOURNAMENT_REGISTRATION_CONFIRMED, request.email());
        verify(socketNotificationBroadcaster).broadcast(
                eq("TOURNAMENT_REGISTRATION"),
                eq("TOURNAMENT"),
                eq("Tournament Enrollment"),
                contains("John registered for Apex Showdown"),
                isNull(),
                eq("/admin/tournaments")
        );
    }

    @Test
    @DisplayName("sendTournamentCancelledEmail: sends email and broadcasts tournament cancelled event")
    void sendTournamentCancelledEmail_shouldSendEmailAndBroadcast() {
        TournamentCancelledEmailRequest request = new TournamentCancelledEmailRequest(
                "gamer@example.com", "John", "Apex Showdown", "Server maintenance"
        );
        EmailContent emailContent = new EmailContent("Tournament Cancelled", "<p>Cancelled</p>");

        when(emailTemplateService.createTournamentCancelledEmail(request)).thenReturn(emailContent);

        notificationService.sendTournamentCancelledEmail(request);

        verify(emailSenderService).sendHtmlEmail(request.email(), emailContent.subject(), emailContent.htmlBody());
        verify(notificationRecordService).recordSuccess(NotificationType.TOURNAMENT_CANCELLED, request.email());
        verify(socketNotificationBroadcaster).broadcast(
                eq("TOURNAMENT_CANCELLED"),
                eq("CANCEL"),
                eq("Tournament Cancelled"),
                contains("Apex Showdown has been cancelled"),
                isNull(),
                eq("/admin/tournaments")
        );
    }

    @Test
    @DisplayName("sendTournamentResultsEmail: creates template, sends email, and records success")
    void sendTournamentResultsEmail_shouldSendAndRecordSuccess() {
        TournamentResultsEmailRequest request = new TournamentResultsEmailRequest(
                "gamer@example.com", "John", "Apex Showdown", "Apex", "WinnerPro", "GG!"
        );
        EmailContent emailContent = new EmailContent("Results Published", "<p>Results</p>");

        when(emailTemplateService.createTournamentResultsEmail(request)).thenReturn(emailContent);

        notificationService.sendTournamentResultsEmail(request);

        verify(emailSenderService).sendHtmlEmail(request.email(), emailContent.subject(), emailContent.htmlBody());
        verify(notificationRecordService).recordSuccess(NotificationType.TOURNAMENT_RESULTS_PUBLISHED, request.email());
    }

    @Test
    @DisplayName("sendTournamentReminderEmail: creates template, sends email, and records success")
    void sendTournamentReminderEmail_shouldSendAndRecordSuccess() {
        TournamentReminderEmailRequest request = new TournamentReminderEmailRequest(
                "gamer@example.com", "John", "Apex Showdown", "Apex", "2026-11-10", "Opponent", "Round 1"
        );
        EmailContent emailContent = new EmailContent("Tournament Reminder", "<p>Reminder</p>");

        when(emailTemplateService.createTournamentReminderEmail(request)).thenReturn(emailContent);

        notificationService.sendTournamentReminderEmail(request);

        verify(emailSenderService).sendHtmlEmail(request.email(), emailContent.subject(), emailContent.htmlBody());
        verify(notificationRecordService).recordSuccess(NotificationType.TOURNAMENT_REMINDER, request.email());
    }

    // ── SMS Notifications ─────────────────────────────────────────────────────

    @Test
    @DisplayName("sendPasswordResetSms: sends formatted SMS with expiration and records success")
    void sendPasswordResetSms_shouldSendAndRecordSuccess() {
        PasswordResetSmsRequest request = new PasswordResetSmsRequest("+94771234567", "987654");

        notificationService.sendPasswordResetSms(request);

        verify(smsService).send(
                eq("+94771234567"),
                eq("Your Gaming Castle password reset code is 987654. It expires in 15 minutes.")
        );
        verify(notificationRecordService).recordSuccess(NotificationType.PASSWORD_RESET_SMS, "+94771234567");
    }

    @Test
    @DisplayName("sendPasswordResetSms: records failure when SMS transport fails")
    void sendPasswordResetSms_shouldRecordFailure_whenSmsFails() {
        PasswordResetSmsRequest request = new PasswordResetSmsRequest("+94771234567", "987654");
        doThrow(new RuntimeException("Twilio service unreachable")).when(smsService).send(anyString(), anyString());

        notificationService.sendPasswordResetSms(request);

        verify(notificationRecordService).recordFailure(
                eq(NotificationType.PASSWORD_RESET_SMS),
                eq("+94771234567"),
                eq("Twilio service unreachable")
        );
    }

    @Test
    @DisplayName("sendAccountReactivationSms: sends formatted SMS and records success")
    void sendAccountReactivationSms_shouldSendAndRecordSuccess() {
        AccountReactivationSmsRequest request = new AccountReactivationSmsRequest("+94771234567", "555444");

        notificationService.sendAccountReactivationSms(request);

        verify(smsService).send(
                eq("+94771234567"),
                eq("Your Gaming Castle account reactivation code is 555444. It expires in 15 minutes.")
        );
        verify(notificationRecordService).recordSuccess(NotificationType.ACCOUNT_REACTIVATION_SMS, "+94771234567");
    }

    @Test
    @DisplayName("sendAccountReactivationSms: records failure when SMS transport fails")
    void sendAccountReactivationSms_shouldRecordFailure_whenSmsFails() {
        AccountReactivationSmsRequest request = new AccountReactivationSmsRequest("+94771234567", "555444");
        doThrow(new RuntimeException("Twilio quota exceeded")).when(smsService).send(anyString(), anyString());

        notificationService.sendAccountReactivationSms(request);

        verify(notificationRecordService).recordFailure(
                eq(NotificationType.ACCOUNT_REACTIVATION_SMS),
                eq("+94771234567"),
                eq("Twilio quota exceeded")
        );
    }
}
