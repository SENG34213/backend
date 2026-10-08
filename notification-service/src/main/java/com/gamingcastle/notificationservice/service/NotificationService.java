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

    public NotificationService(
            EmailSenderService emailSenderService,
            SmsService smsService,
            NotificationRecordService notificationRecordService,
            EmailTemplateService emailTemplateService
    ) {
        this.emailSenderService = emailSenderService;
        this.smsService = smsService;
        this.notificationRecordService = notificationRecordService;
        this.emailTemplateService = emailTemplateService;
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
    }

    public void sendBookingRescheduledEmail(BookingEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.BOOKING_RESCHEDULED,
                emailTemplateService.createBookingRescheduledEmail(request)
        );
    }

    public void sendBookingCancelledEmail(BookingEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.BOOKING_CANCELLED,
                emailTemplateService.createBookingCancelledEmail(request)
        );
    }

    public void sendTournamentRegistrationEmail(TournamentRegistrationEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.TOURNAMENT_REGISTRATION_CONFIRMED,
                emailTemplateService.createTournamentRegistrationEmail(request)
        );
    }

    public void sendTournamentCancelledEmail(TournamentCancelledEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.TOURNAMENT_CANCELLED,
                emailTemplateService.createTournamentCancelledEmail(request)
        );
    }

    public void sendTournamentResultsEmail(TournamentResultsEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.TOURNAMENT_RESULTS_PUBLISHED,
                emailTemplateService.createTournamentResultsEmail(request)
        );
    }

    public void sendTournamentReminderEmail(TournamentReminderEmailRequest request) {
        sendEmail(
                request.email(),
                NotificationType.TOURNAMENT_REMINDER,
                emailTemplateService.createTournamentReminderEmail(request)
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
            throw exception;
        }
    }

    private void sendSms(String recipient, NotificationType type, String message) {
        try {
            smsService.send(recipient, message);
            notificationRecordService.recordSuccess(type, recipient);
        } catch (Exception exception) {
            notificationRecordService.recordFailure(type, recipient, exception.getMessage());
            throw exception;
        }
    }
}
