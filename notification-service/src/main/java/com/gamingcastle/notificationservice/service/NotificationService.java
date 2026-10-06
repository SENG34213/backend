package com.gamingcastle.notificationservice.service;

import com.gamingcastle.notificationservice.dto.AccountReactivationEmailRequest;
import com.gamingcastle.notificationservice.dto.AccountReactivationSmsRequest;
import com.gamingcastle.notificationservice.dto.BookingEmailRequest;
import com.gamingcastle.notificationservice.dto.PasswordResetEmailRequest;
import com.gamingcastle.notificationservice.dto.WelcomeEmailRequest;
import com.gamingcastle.notificationservice.entity.Notification;
import com.gamingcastle.notificationservice.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    private final EmailSenderService emailSenderService;
    private final SmsService smsService;
    private final NotificationRepository notificationRepository;

    public NotificationService(EmailSenderService emailSenderService, SmsService smsService, NotificationRepository notificationRepository) {
        this.emailSenderService = emailSenderService;
        this.smsService = smsService;
        this.notificationRepository = notificationRepository;
    }

    public void sendWelcomeEmail(WelcomeEmailRequest request) {
        String subject = "Welcome to Gaming Castle!";
        String htmlBody = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Welcome to Gaming Castle</title>
            </head>
            <body style="margin:0; padding:0; background-color:#f4f4f7; font-family:Arial, Helvetica, sans-serif;">

                <div style="max-width:600px; margin:40px auto; padding:20px;">

                    <div style="background-color:#1a1a2e; padding:30px; text-align:center;
                                border-radius:12px 12px 0 0;">
                        <h1 style="margin:0; color:#ffffff; font-size:28px;">
                            🎮 Gaming Castle
                        </h1>
                        <p style="margin:8px 0 0; color:#c7c7d9; font-size:14px;">
                            Welcome Aboard!
                        </p>
                    </div>

                    <div style="background-color:#ffffff; padding:40px 30px;
                                border-radius:0 0 12px 12px;">

                        <h2 style="margin-top:0; color:#222222;">
                            Hi %s,
                        </h2>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            Welcome to Gaming Castle! We are thrilled to have you here.
                        </p>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            Get ready to explore the best gaming experiences. Your account has been successfully created.
                        </p>

                        <div style="margin:30px 0; text-align:center;">
                            <a href="#" style="display:inline-block; padding:15px 25px; background-color:#6c63ff; color:#ffffff; text-decoration:none; border-radius:8px; font-weight:bold;">
                                Go to Dashboard
                            </a>
                        </div>

                        <p style="color:#777777; font-size:14px; line-height:1.6;">
                            If you have any questions, feel free to reply to this email.
                        </p>

                        <hr style="border:none; border-top:1px solid #eeeeee; margin:30px 0;">

                        <p style="margin:0; color:#999999; font-size:12px; text-align:center;">
                            © Gaming Castle. All rights reserved.
                        </p>

                    </div>

                </div>

            </body>
            </html>
            """.formatted(request.fullName());
        try {
            emailSenderService.sendHtmlEmail(request.email(), subject, htmlBody);
            logNotification("WELCOME_EMAIL", request.email(), "SENT", null);
        } catch (Exception e) {
            logNotification("WELCOME_EMAIL", request.email(), "FAILED", e.getMessage());
            throw e;
        }
    }

    public void sendPasswordResetEmail(PasswordResetEmailRequest request) {
        String subject = "Your Gaming Castle password reset code";
        String htmlBody = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Password Reset</title>
            </head>
            <body style="margin:0; padding:0; background-color:#f4f4f7; font-family:Arial, Helvetica, sans-serif;">

                <div style="max-width:600px; margin:40px auto; padding:20px;">

                    <div style="background-color:#1a1a2e; padding:30px; text-align:center;
                                border-radius:12px 12px 0 0;">
                        <h1 style="margin:0; color:#ffffff; font-size:28px;">
                            🎮 Gaming Castle
                        </h1>
                        <p style="margin:8px 0 0; color:#c7c7d9; font-size:14px;">
                            Password Reset
                        </p>
                    </div>

                    <div style="background-color:#ffffff; padding:40px 30px;
                                border-radius:0 0 12px 12px;">

                        <h2 style="margin-top:0; color:#222222;">
                            Reset Your Password
                        </h2>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            We received a request to reset your Gaming Castle account password.
                        </p>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            Use the verification code below:
                        </p>

                        <div style="margin:30px 0; text-align:center;">
                            <div style="display:inline-block;
                                        padding:18px 35px;
                                        background-color:#f1f0ff;
                                        border:2px dashed #6c63ff;
                                        border-radius:10px;
                                        color:#6c63ff;
                                        font-size:32px;
                                        font-weight:bold;
                                        letter-spacing:8px;">
                                %s
                            </div>
                        </div>

                        <p style="color:#555555; font-size:14px; line-height:1.6;">
                            ⏱ This verification code will expire in
                            <strong>15 minutes</strong>.
                        </p>

                        <p style="color:#777777; font-size:14px; line-height:1.6;">
                            If you didn't request a password reset, you can safely ignore
                            this email. Your account remains secure.
                        </p>

                        <hr style="border:none; border-top:1px solid #eeeeee; margin:30px 0;">

                        <p style="margin:0; color:#999999; font-size:12px; text-align:center;">
                            © Gaming Castle. All rights reserved.
                        </p>

                    </div>

                </div>

            </body>
            </html>
            """.formatted(request.code());
        try {
            emailSenderService.sendHtmlEmail(request.email(), subject, htmlBody);
            logNotification("PASSWORD_RESET_EMAIL", request.email(), "SENT", null);
        } catch (Exception e) {
            logNotification("PASSWORD_RESET_EMAIL", request.email(), "FAILED", e.getMessage());
            throw e;
        }
    }

    public void sendBookingConfirmationEmail(BookingEmailRequest request) {
        String subject = "Booking Confirmed - Gaming Castle";
        String htmlBody = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Booking Confirmation</title>
            </head>
            <body style="margin:0; padding:0; background-color:#f4f4f7; font-family:Arial, Helvetica, sans-serif;">

                <div style="max-width:600px; margin:40px auto; padding:20px;">

                    <div style="background-color:#1a1a2e; padding:30px; text-align:center;
                                border-radius:12px 12px 0 0;">
                        <h1 style="margin:0; color:#ffffff; font-size:28px;">
                            🎮 Gaming Castle
                        </h1>
                        <p style="margin:8px 0 0; color:#c7c7d9; font-size:14px;">
                            Booking Confirmation
                        </p>
                    </div>

                    <div style="background-color:#ffffff; padding:40px 30px;
                                border-radius:0 0 12px 12px;">

                        <h2 style="margin-top:0; color:#222222;">
                            Hi %s,
                        </h2>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            Your booking (ID: <strong>%s</strong>) has been successfully confirmed!
                        </p>

                        <div style="margin:25px 0; padding:20px; background-color:#f8f9fa; border-radius:8px; border-left:4px solid #6c63ff;">
                            <p style="margin:0 0 10px 0; color:#444444; font-size:15px;"><strong>Game Station:</strong> %s</p>
                            <p style="margin:0 0 10px 0; color:#444444; font-size:15px;"><strong>Date:</strong> %s</p>
                            <p style="margin:0 0 10px 0; color:#444444; font-size:15px;"><strong>Time Slot:</strong> %s (24Hrs)</p>
                            <p style="margin:0; color:#444444; font-size:15px;"><strong>Amount:</strong> %s</p>
                        </div>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            We look forward to hosting you!
                        </p>

                        <hr style="border:none; border-top:1px solid #eeeeee; margin:30px 0;">

                        <p style="margin:0; color:#999999; font-size:12px; text-align:center;">
                            © Gaming Castle. All rights reserved.
                        </p>

                    </div>

                </div>

            </body>
            </html>
            """.formatted(request.customerName(), request.bookingId(), request.gameStationName(), request.bookingDate(), request.timeSlot(), request.amount());
        try {
            emailSenderService.sendHtmlEmail(request.email(), subject, htmlBody);
            logNotification("BOOKING_CONFIRMATION", request.email(), "SENT", null);
        } catch (Exception e) {
            logNotification("BOOKING_CONFIRMATION", request.email(), "FAILED", e.getMessage());
            throw e;
        }
    }

    public void sendBookingRescheduledEmail(BookingEmailRequest request) {
        String subject = "Booking Rescheduled - Gaming Castle";
        String htmlBody = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Booking Rescheduled</title>
            </head>
            <body style="margin:0; padding:0; background-color:#f4f4f7; font-family:Arial, Helvetica, sans-serif;">

                <div style="max-width:600px; margin:40px auto; padding:20px;">

                    <div style="background-color:#1a1a2e; padding:30px; text-align:center;
                                border-radius:12px 12px 0 0;">
                        <h1 style="margin:0; color:#ffffff; font-size:28px;">
                            🎮 Gaming Castle
                        </h1>
                        <p style="margin:8px 0 0; color:#c7c7d9; font-size:14px;">
                            Booking Rescheduled
                        </p>
                    </div>

                    <div style="background-color:#ffffff; padding:40px 30px;
                                border-radius:0 0 12px 12px;">

                        <h2 style="margin-top:0; color:#222222;">
                            Hi %s,
                        </h2>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            Your booking (ID: <strong>%s</strong>) has been successfully rescheduled.
                        </p>

                        <div style="margin:25px 0; padding:20px; background-color:#f8f9fa; border-radius:8px; border-left:4px solid #6c63ff;">
                            <p style="margin:0 0 10px 0; color:#444444; font-size:15px;"><strong>New Game Station:</strong> %s</p>
                            <p style="margin:0 0 10px 0; color:#444444; font-size:15px;"><strong>New Date:</strong> %s</p>
                            <p style="margin:0; color:#444444; font-size:15px;"><strong>New Time Slot:</strong> %s</p>
                        </div>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            We look forward to seeing you at the new time!
                        </p>

                        <hr style="border:none; border-top:1px solid #eeeeee; margin:30px 0;">

                        <p style="margin:0; color:#999999; font-size:12px; text-align:center;">
                            © Gaming Castle. All rights reserved.
                        </p>

                    </div>

                </div>

            </body>
            </html>
            """.formatted(request.customerName(), request.bookingId(), request.gameStationName(), request.bookingDate(), request.timeSlot());
        try {
            emailSenderService.sendHtmlEmail(request.email(), subject, htmlBody);
            logNotification("BOOKING_RESCHEDULED", request.email(), "SENT", null);
        } catch (Exception e) {
            logNotification("BOOKING_RESCHEDULED", request.email(), "FAILED", e.getMessage());
            throw e;
        }
    }

    public void sendBookingCancelledEmail(BookingEmailRequest request) {
        String subject = "Booking Cancelled - Gaming Castle";
        String htmlBody = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Booking Cancelled</title>
            </head>
            <body style="margin:0; padding:0; background-color:#f4f4f7; font-family:Arial, Helvetica, sans-serif;">

                <div style="max-width:600px; margin:40px auto; padding:20px;">

                    <div style="background-color:#1a1a2e; padding:30px; text-align:center;
                                border-radius:12px 12px 0 0;">
                        <h1 style="margin:0; color:#ffffff; font-size:28px;">
                            🎮 Gaming Castle
                        </h1>
                        <p style="margin:8px 0 0; color:#c7c7d9; font-size:14px;">
                            Booking Cancelled
                        </p>
                    </div>

                    <div style="background-color:#ffffff; padding:40px 30px;
                                border-radius:0 0 12px 12px;">

                        <h2 style="margin-top:0; color:#222222;">
                            Hi %s,
                        </h2>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            Your booking (ID: <strong>%s</strong>) on <strong>%s</strong> at <strong>%s</strong> has been cancelled.
                        </p>

                        <p style="color:#777777; font-size:14px; line-height:1.6; margin-top:20px;">
                            If this was a mistake or you wish to re-book, please visit our platform.
                        </p>

                        <hr style="border:none; border-top:1px solid #eeeeee; margin:30px 0;">

                        <p style="margin:0; color:#999999; font-size:12px; text-align:center;">
                            © Gaming Castle. All rights reserved.
                        </p>

                    </div>

                </div>

            </body>
            </html>
            """.formatted(request.customerName(), request.bookingId(), request.bookingDate(), request.timeSlot());
        try {
            emailSenderService.sendHtmlEmail(request.email(), subject, htmlBody);
            logNotification("BOOKING_CANCELLED", request.email(), "SENT", null);
        } catch (Exception e) {
            logNotification("BOOKING_CANCELLED", request.email(), "FAILED", e.getMessage());
            throw e;
        }
    }

    public void sendPasswordResetSms(com.gamingcastle.notificationservice.dto.PasswordResetSmsRequest request) {
        String message = "Your Gaming Castle password reset code is " + request.code() + ". It expires in 15 minutes.";
        try {
            smsService.send(request.phoneNumber(), message);
            logNotification("PASSWORD_RESET_SMS", request.phoneNumber(), "SENT", null);
        } catch (Exception e) {
            logNotification("PASSWORD_RESET_SMS", request.phoneNumber(), "FAILED", e.getMessage());
            throw e;
        }
    }

    public void sendAccountReactivationEmail(AccountReactivationEmailRequest request) {
        String subject = "Your Gaming Castle account reactivation code";
        String htmlBody = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Account Reactivation</title>
            </head>
            <body style="margin:0; padding:0; background-color:#f4f4f7; font-family:Arial, Helvetica, sans-serif;">

                <div style="max-width:600px; margin:40px auto; padding:20px;">

                    <div style="background-color:#1a1a2e; padding:30px; text-align:center;
                                border-radius:12px 12px 0 0;">
                        <h1 style="margin:0; color:#ffffff; font-size:28px;">
                            🎮 Gaming Castle
                        </h1>
                        <p style="margin:8px 0 0; color:#c7c7d9; font-size:14px;">
                            Account Reactivation
                        </p>
                    </div>

                    <div style="background-color:#ffffff; padding:40px 30px;
                                border-radius:0 0 12px 12px;">

                        <h2 style="margin-top:0; color:#222222;">
                            Reactivate Your Account
                        </h2>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            We received a request to reactivate your Gaming Castle account.
                        </p>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            Use the verification code below to restore access to your account:
                        </p>

                        <div style="margin:30px 0; text-align:center;">
                            <div style="display:inline-block;
                                        padding:18px 35px;
                                        background-color:#f1f0ff;
                                        border:2px dashed #6c63ff;
                                        border-radius:10px;
                                        color:#6c63ff;
                                        font-size:32px;
                                        font-weight:bold;
                                        letter-spacing:8px;">
                                %s
                            </div>
                        </div>

                        <p style="color:#555555; font-size:14px; line-height:1.6;">
                            ⏱ This verification code will expire in
                            <strong>15 minutes</strong>.
                        </p>

                        <p style="color:#777777; font-size:14px; line-height:1.6;">
                            If you didn't request account reactivation, you can safely ignore
                            this email.
                        </p>

                        <hr style="border:none; border-top:1px solid #eeeeee; margin:30px 0;">

                        <p style="margin:0; color:#999999; font-size:12px; text-align:center;">
                            © Gaming Castle. All rights reserved.
                        </p>

                    </div>

                </div>

            </body>
            </html>
            """.formatted(request.code());
        try {
            emailSenderService.sendHtmlEmail(request.email(), subject, htmlBody);
            logNotification("ACCOUNT_REACTIVATION_EMAIL", request.email(), "SENT", null);
        } catch (Exception e) {
            logNotification("ACCOUNT_REACTIVATION_EMAIL", request.email(), "FAILED", e.getMessage());
            throw e;
        }
    }

    public void sendAccountReactivationSms(AccountReactivationSmsRequest request) {
        String message = "Your Gaming Castle account reactivation code is " + request.code() + ". It expires in 15 minutes.";
        try {
            smsService.send(request.phoneNumber(), message);
            logNotification("ACCOUNT_REACTIVATION_SMS", request.phoneNumber(), "SENT", null);
        } catch (Exception e) {
            logNotification("ACCOUNT_REACTIVATION_SMS", request.phoneNumber(), "FAILED", e.getMessage());
            throw e;
        }
    }



    private void logNotification(String type, String recipient, String status, String error) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .notificationType(type)
                .recipientEmail(recipient)
                .status(status)
                .errorMessage(error)
                .createdAt(Instant.now())
                .sentAt(status.equals("SENT") ? Instant.now() : null)
                .build();
        notificationRepository.save(notification);
    }
}
