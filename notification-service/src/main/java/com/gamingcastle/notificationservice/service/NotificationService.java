package com.gamingcastle.notificationservice.service;

import com.gamingcastle.notificationservice.dto.*;
import com.gamingcastle.notificationservice.enums.NotificationType;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final int EXPIRATION_MINUTES = 15;

    private final EmailSenderService emailSenderService;
    private final SmsService smsService;
    private final NotificationRecordService notificationRecordService;
    private final EmailTemplateService emailTemplateService;
    private final SocketNotificationBroadcaster socketNotificationBroadcaster;

    public NotificationService(
            EmailSenderService emailSenderService,
            SmsService smsService,
            NotificationRecordService notificationRecordService,
            EmailTemplateService emailTemplateService,
            SocketNotificationBroadcaster socketNotificationBroadcaster
    ) {
        this.emailSenderService = emailSenderService;
        this.smsService = smsService;
        this.notificationRecordService = notificationRecordService;
        this.emailTemplateService = emailTemplateService;
        this.socketNotificationBroadcaster = socketNotificationBroadcaster;
    }

    public void sendWelcomeEmail(WelcomeEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.WELCOME_EMAIL,
                emailTemplateService.createWelcomeEmail(request)
        );
    }

    public void sendPasswordResetEmail(PasswordResetEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.PASSWORD_RESET_EMAIL,
                emailTemplateService.createPasswordResetEmail(request)
        );
    }

    public void sendAccountReactivationEmail(AccountReactivationEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.ACCOUNT_REACTIVATION_EMAIL,
                emailTemplateService.createAccountReactivationEmail(request)
        );
    }

    public void sendBookingConfirmationEmail(BookingEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.BOOKING_CONFIRMATION,
                emailTemplateService.createBookingConfirmationEmail(request)
        );
        socketNotificationBroadcaster.broadcast(
                "BOOKING_CREATED",
                "BOOKING",
                "New Booking Confirmed",
                "Station " + request.gameStationName() + " booked by " + request.customerName() + " (" + request.timeSlot() + ")",
                request.bookingId(),
                "/admin/bookings",
                request.email(),
                null
        );
    }

    public void sendBookingRescheduledEmail(BookingEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.BOOKING_RESCHEDULED,
                emailTemplateService.createBookingRescheduledEmail(request)
        );
        socketNotificationBroadcaster.broadcast(
                "BOOKING_RESCHEDULED",
                "RESCHEDULE",
                "Booking Rescheduled",
                "Booking #" + request.bookingId() + " at " + request.gameStationName() + " rescheduled to " + request.timeSlot(),
                request.bookingId(),
                "/admin/bookings",
                request.email(),
                null
        );
    }

    public void sendBookingCancelledEmail(BookingEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.BOOKING_CANCELLED,
                emailTemplateService.createBookingCancelledEmail(request)
        );
        socketNotificationBroadcaster.broadcast(
                "BOOKING_CANCELLED",
                "CANCEL",
                "Booking Cancelled",
                "Booking #" + request.bookingId() + " for " + request.customerName() + " at " + request.gameStationName() + " was cancelled",
                request.bookingId(),
                "/admin/bookings",
                request.email(),
                null
        );
    }

    public void sendTournamentRegistrationEmail(TournamentRegistrationEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.TOURNAMENT_REGISTRATION_CONFIRMED,
                emailTemplateService.createTournamentRegistrationEmail(request)
        );
        socketNotificationBroadcaster.broadcast(
                "TOURNAMENT_REGISTRATION",
                "TOURNAMENT",
                "Tournament Enrollment",
                request.customerName() + " registered for " + request.tournamentName() + " (" + request.gameTitle() + ")",
                null,
                "/admin/tournaments",
                request.email(),
                null
        );
    }

    public void sendTournamentCancelledEmail(TournamentCancelledEmailRequest request) {
        if (request.email() != null && !request.email().isBlank()) {
            sendEmail(
                    request.email(),
                    NotificationType.TOURNAMENT_CANCELLED,
                    emailTemplateService.createTournamentCancelledEmail(request)
            );
        }

        java.util.List<String> targetIds = null;
        if (request.userId() != null && !request.userId().isBlank()) {
            targetIds = new java.util.ArrayList<>();
            targetIds.add(request.userId());
            if (request.email() != null && !request.email().isBlank()) {
                targetIds.add(request.email());
            }
        }

        socketNotificationBroadcaster.broadcast(
                "TOURNAMENT_CANCELLED",
                "CANCEL",
                "Tournament Cancelled",
                request.tournamentName() + " has been cancelled",
                null,
                "/tournaments",
                request.email() != null ? request.email() : request.userId(),
                targetIds
        );
    }

    public void sendTournamentResultsEmail(TournamentResultsEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.TOURNAMENT_RESULTS_PUBLISHED,
                emailTemplateService.createTournamentResultsEmail(request)
        );
        socketNotificationBroadcaster.broadcast(
                "TOURNAMENT_RESULTS",
                "TOURNAMENT",
                "Tournament Results Published 🏆",
                "Final results for " + request.tournamentName() + " (" + request.gameTitle() + ") are now live!",
                null,
                "/tournaments",
                request.email(),
                null
        );
    }

    public void sendTournamentReminderEmail(TournamentReminderEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.TOURNAMENT_REMINDER,
                emailTemplateService.createTournamentReminderEmail(request)
        );
        String details = "Reminder: " + request.tournamentName() + " (" + request.gameTitle() + ")";
        if (request.roundInfo() != null && !request.roundInfo().isBlank()) {
            details += " - " + request.roundInfo();
        }
        if (request.opponentName() != null && !request.opponentName().isBlank()) {
            details += " vs " + request.opponentName();
        }
        socketNotificationBroadcaster.broadcast(
                "MATCH_REMINDER",
                "TOURNAMENT",
                "Match Starting Soon ⚡",
                details,
                null,
                "/tournaments",
                request.email(),
                null
        );
    }

    public void sendPasswordResetSms(PasswordResetSmsRequest request) {
        String message = String.format(
                "Your Gaming Castle password reset code is %s. It expires in %d minutes.",
                request.code(),
                EXPIRATION_MINUTES
        );
        sendSms(request.phoneNumber(), NotificationType.PASSWORD_RESET_SMS, message);
    }

    public void sendAccountReactivationSms(AccountReactivationSmsRequest request) {
        String message = String.format(
                "Your Gaming Castle account reactivation code is %s. It expires in %d minutes.",
                request.code(),
                EXPIRATION_MINUTES
        );
        sendSms(request.phoneNumber(), NotificationType.ACCOUNT_REACTIVATION_SMS, message);
    }

    private void sendEmail(String recipient, NotificationType type, EmailContent content) {
        try {
            emailSenderService.sendHtmlEmail(recipient, content.subject(), content.htmlBody());
            notificationRecordService.recordSuccess(type, recipient);
        } catch (Exception exception) {
            notificationRecordService.recordFailure(type, recipient, exception.getMessage());
            // Do not let a notification delivery failure break the primary business flow.
        }
    }

    private void sendSms(String recipient, NotificationType type, String message) {
        try {
            smsService.send(recipient, message);
            notificationRecordService.recordSuccess(type, recipient);
        } catch (Exception exception) {
            notificationRecordService.recordFailure(type, recipient, exception.getMessage());
            // Do not let notification failures break account or booking workflows.
        }
    }
}
