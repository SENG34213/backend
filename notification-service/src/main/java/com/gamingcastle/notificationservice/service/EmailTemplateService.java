package com.gamingcastle.notificationservice.service;

import com.gamingcastle.notificationservice.dto.AccountReactivationEmailRequest;
import com.gamingcastle.notificationservice.dto.BookingEmailRequest;
import com.gamingcastle.notificationservice.dto.EmailContent;
import com.gamingcastle.notificationservice.dto.PasswordResetEmailRequest;
import com.gamingcastle.notificationservice.dto.WelcomeEmailRequest;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplateService {

    public EmailContent createWelcomeEmail(WelcomeEmailRequest request) {
        String subject = "Welcome to Gaming Castle!";
        String content = """
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
                """.formatted(request.fullName());

        return new EmailContent(subject, wrapInLayout("Welcome to Gaming Castle", "Welcome Aboard!", content));
    }

    public EmailContent createPasswordResetEmail(PasswordResetEmailRequest request) {
        String subject = "Your Gaming Castle password reset code";
        String content = """
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
                """.formatted(request.code());

        return new EmailContent(subject, wrapInLayout("Password Reset", "Password Reset", content));
    }

    public EmailContent createBookingConfirmationEmail(BookingEmailRequest request) {
        String subject = "Booking Confirmed - Gaming Castle";
        String content = """
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
                """.formatted(
                request.customerName(),
                request.bookingId(),
                request.gameStationName(),
                request.bookingDate(),
                request.timeSlot(),
                request.amount()
        );

        return new EmailContent(subject, wrapInLayout("Booking Confirmation", "Booking Confirmation", content));
    }

    public EmailContent createBookingRescheduledEmail(BookingEmailRequest request) {
        String subject = "Booking Rescheduled - Gaming Castle";
        String content = """
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
                """.formatted(
                request.customerName(),
                request.bookingId(),
                request.gameStationName(),
                request.bookingDate(),
                request.timeSlot()
        );

        return new EmailContent(subject, wrapInLayout("Booking Rescheduled", "Booking Rescheduled", content));
    }

    public EmailContent createBookingCancelledEmail(BookingEmailRequest request) {
        String subject = "Booking Cancelled - Gaming Castle";
        String content = """
                <h2 style="margin-top:0; color:#222222;">
                    Hi %s,
                </h2>

                <p style="color:#555555; font-size:16px; line-height:1.6;">
                    Your booking (ID: <strong>%s</strong>) on <strong>%s</strong> at <strong>%s</strong> has been cancelled.
                </p>

                <p style="color:#777777; font-size:14px; line-height:1.6; margin-top:20px;">
                    If this was a mistake or you wish to re-book, please visit our platform.
                </p>
                """.formatted(
                request.customerName(),
                request.bookingId(),
                request.bookingDate(),
                request.timeSlot()
        );

        return new EmailContent(subject, wrapInLayout("Booking Cancelled", "Booking Cancelled", content));
    }

    public EmailContent createAccountReactivationEmail(AccountReactivationEmailRequest request) {
        String subject = "Your Gaming Castle account reactivation code";
        String content = """
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
                """.formatted(request.code());

        return new EmailContent(subject, wrapInLayout("Account Reactivation", "Account Reactivation", content));
    }

    public EmailContent createTournamentRegistrationEmail(com.gamingcastle.notificationservice.dto.TournamentRegistrationEmailRequest request) {
        String subject = "Tournament Registration Confirmed - " + request.tournamentName();
        String content = """
                <h2 style="margin-top:0; color:#222222;">
                    Hi %s,
                </h2>

                <p style="color:#555555; font-size:16px; line-height:1.6;">
                    Your registration for <strong>%s</strong> has been successfully confirmed!
                </p>

                <div style="margin:25px 0; padding:20px; background-color:#f8f9fa; border-radius:8px; border-left:4px solid #6c63ff;">
                    <p style="margin:0 0 10px 0; color:#444444; font-size:15px;"><strong>Tournament:</strong> %s</p>
                    <p style="margin:0 0 10px 0; color:#444444; font-size:15px;"><strong>Game:</strong> %s</p>
                    <p style="margin:0 0 10px 0; color:#444444; font-size:15px;"><strong>Start Date:</strong> %s</p>
                    <p style="margin:0; color:#444444; font-size:15px;"><strong>Entry Fee:</strong> %s</p>
                </div>

                <p style="color:#555555; font-size:16px; line-height:1.6;">
                    Brackets and match schedules will be generated once registration closes. Good luck!
                </p>
                """.formatted(
                request.customerName() != null ? request.customerName() : "Gamer",
                request.tournamentName(),
                request.tournamentName(),
                request.gameTitle(),
                request.startDate(),
                request.entryFee()
        );

        return new EmailContent(subject, wrapInLayout("Tournament Registration Confirmed", "Tournament Registration Confirmed", content));
    }

    public EmailContent createTournamentCancelledEmail(com.gamingcastle.notificationservice.dto.TournamentCancelledEmailRequest request) {
        String subject = "Tournament Cancelled - " + request.tournamentName();
        String content = """
                <h2 style="margin-top:0; color:#222222;">
                    Hi %s,
                </h2>

                <p style="color:#555555; font-size:16px; line-height:1.6;">
                    We regret to inform you that the tournament <strong>%s</strong> has been cancelled.
                </p>

                <div style="margin:25px 0; padding:20px; background-color:#fff2f0; border-radius:8px; border-left:4px solid #ff4d4f;">
                    <p style="margin:0; color:#444444; font-size:15px;"><strong>Reason / Details:</strong> %s</p>
                </div>

                <p style="color:#555555; font-size:16px; line-height:1.6;">
                    If an entry fee was charged, a refund will be processed to your original payment method. We apologize for any inconvenience.
                </p>
                """.formatted(
                request.customerName() != null ? request.customerName() : "Gamer",
                request.tournamentName(),
                request.reason() != null ? request.reason() : "Cancelled by tournament administrator."
        );

        return new EmailContent(subject, wrapInLayout("Tournament Cancelled", "Tournament Notice", content));
    }

    public EmailContent createTournamentResultsEmail(com.gamingcastle.notificationservice.dto.TournamentResultsEmailRequest request) {
        String subject = "Tournament Results Published - " + request.tournamentName();
        String content = """
                <h2 style="margin-top:0; color:#222222;">
                    Hi %s,
                </h2>

                <p style="color:#555555; font-size:16px; line-height:1.6;">
                    The results for <strong>%s</strong> (%s) have been finalized!
                </p>

                <div style="margin:25px 0; padding:20px; background-color:#f6ffed; border-radius:8px; border-left:4px solid #52c41a;">
                    <p style="margin:0 0 10px 0; color:#444444; font-size:16px;">🏆 <strong>Tournament Champion:</strong> %s</p>
                    <p style="margin:0; color:#555555; font-size:14px;">%s</p>
                </div>

                <p style="color:#555555; font-size:16px; line-height:1.6;">
                    Thank you to all participants for competing. Check the full bracket on the Gaming Castle platform!
                </p>
                """.formatted(
                request.customerName() != null ? request.customerName() : "Gamer",
                request.tournamentName(),
                request.gameTitle() != null ? request.gameTitle() : "Tournament",
                request.winnerName() != null ? request.winnerName() : "TBD",
                request.message() != null ? request.message() : "Congratulations to the winners!"
        );

        return new EmailContent(subject, wrapInLayout("Tournament Results", "Tournament Results", content));
    }

    public EmailContent createTournamentReminderEmail(com.gamingcastle.notificationservice.dto.TournamentReminderEmailRequest request) {
        String subject = "⏰ 2-Hour Reminder: Upcoming Tournament " + request.tournamentName();
        String content = """
                <h2 style="margin-top:0; color:#222222;">
                    Hi %s,
                </h2>

                <p style="color:#555555; font-size:16px; line-height:1.6;">
                    Your upcoming tournament <strong>%s</strong> (%s) starts in <strong>2 hours</strong>!
                </p>

                <div style="margin:25px 0; padding:20px; background-color:#f0f5ff; border-radius:8px; border-left:4px solid #1890ff;">
                    <p style="margin:0 0 8px 0; color:#333333; font-size:15px;">🎮 <strong>Game Title:</strong> %s</p>
                    <p style="margin:0 0 8px 0; color:#333333; font-size:15px;">🕒 <strong>Start Time:</strong> %s</p>
                    <p style="margin:0 0 8px 0; color:#333333; font-size:15px;">⚔️ <strong>First Round Opponent:</strong> %s</p>
                    <p style="margin:0; color:#333333; font-size:15px;">📊 <strong>Match Info:</strong> %s</p>
                </div>

                <p style="color:#555555; font-size:16px; line-height:1.6;">
                    Please log into Gaming Castle 15 minutes before the tournament start time to check in. Good luck!
                </p>
                """.formatted(
                request.customerName() != null ? request.customerName() : "Gamer",
                request.tournamentName(),
                request.gameTitle() != null ? request.gameTitle() : "Tournament",
                request.gameTitle() != null ? request.gameTitle() : "N/A",
                request.startDate() != null ? request.startDate() : "Starting soon",
                request.opponentName() != null ? request.opponentName() : "TBD",
                request.roundInfo() != null ? request.roundInfo() : "Round 1"
        );

        return new EmailContent(subject, wrapInLayout("Tournament Reminder", "Tournament Starting Soon", content));
    }

    private String wrapInLayout(String pageTitle, String subtitle, String content) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f4f4f7; font-family:Arial, Helvetica, sans-serif;">

                    <div style="max-width:600px; margin:40px auto; padding:20px;">

                        <div style="background-color:#1a1a2e; padding:30px; text-align:center;
                                    border-radius:12px 12px 0 0;">
                            <h1 style="margin:0; color:#ffffff; font-size:28px;">
                                🎮 Gaming Castle
                            </h1>
                            <p style="margin:8px 0 0; color:#c7c7d9; font-size:14px;">
                                %s
                            </p>
                        </div>

                        <div style="background-color:#ffffff; padding:40px 30px;
                                    border-radius:0 0 12px 12px;">

                            %s

                            <hr style="border:none; border-top:1px solid #eeeeee; margin:30px 0;">

                            <p style="margin:0; color:#999999; font-size:12px; text-align:center;">
                                © Gaming Castle. All rights reserved.
                            </p>

                        </div>

                    </div>

                </body>
                </html>
                """.formatted(pageTitle, subtitle, content);
    }
}
