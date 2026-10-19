package com.gamingcastle.notificationservice.service;

import com.gamingcastle.notificationservice.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateServiceTest {

    private EmailTemplateService emailTemplateService;

    @BeforeEach
    void setUp() {
        emailTemplateService = new EmailTemplateService();
    }

    @Test
    @DisplayName("createWelcomeEmail: generates welcome email with formatted recipient name")
    void createWelcomeEmail_shouldGenerateEmail() {
        WelcomeEmailRequest request = new WelcomeEmailRequest("gamer@example.com", "John Doe");

        EmailContent content = emailTemplateService.createWelcomeEmail(request);

        assertThat(content.subject()).isEqualTo("Welcome to Gaming Castle!");
        assertThat(content.htmlBody()).contains("Hi John Doe,");
        assertThat(content.htmlBody()).contains("Welcome Aboard!");
        assertThat(content.htmlBody()).contains("Gaming Castle");
    }

    @Test
    @DisplayName("createPasswordResetEmail: generates password reset email with formatted code")
    void createPasswordResetEmail_shouldGenerateEmail() {
        PasswordResetEmailRequest request = new PasswordResetEmailRequest("gamer@example.com", "123456");

        EmailContent content = emailTemplateService.createPasswordResetEmail(request);

        assertThat(content.subject()).isEqualTo("Your Gaming Castle password reset code");
        assertThat(content.htmlBody()).contains("123456");
        assertThat(content.htmlBody()).contains("15 minutes");
        assertThat(content.htmlBody()).contains("Reset Your Password");
    }

    @Test
    @DisplayName("createAccountReactivationEmail: generates account reactivation email with code")
    void createAccountReactivationEmail_shouldGenerateEmail() {
        AccountReactivationEmailRequest request = new AccountReactivationEmailRequest("gamer@example.com", "654321");

        EmailContent content = emailTemplateService.createAccountReactivationEmail(request);

        assertThat(content.subject()).isEqualTo("Your Gaming Castle account reactivation code");
        assertThat(content.htmlBody()).contains("654321");
        assertThat(content.htmlBody()).contains("Reactivate Your Account");
    }

    @Test
    @DisplayName("createBookingConfirmationEmail: generates confirmation email with booking details")
    void createBookingConfirmationEmail_shouldGenerateEmail() {
        BookingEmailRequest request = new BookingEmailRequest(
                "gamer@example.com",
                "Alice",
                "BKG-1001",
                "PS5-01",
                "2026-10-20",
                "14:00 - 16:00",
                "LKR 1,000"
        );

        EmailContent content = emailTemplateService.createBookingConfirmationEmail(request);

        assertThat(content.subject()).isEqualTo("Booking Confirmed - Gaming Castle");
        assertThat(content.htmlBody()).contains("Hi Alice,");
        assertThat(content.htmlBody()).contains("BKG-1001");
        assertThat(content.htmlBody()).contains("PS5-01");
        assertThat(content.htmlBody()).contains("2026-10-20");
        assertThat(content.htmlBody()).contains("14:00 - 16:00");
        assertThat(content.htmlBody()).contains("LKR 1,000");
    }

    @Test
    @DisplayName("createBookingRescheduledEmail: generates rescheduled email with new details")
    void createBookingRescheduledEmail_shouldGenerateEmail() {
        BookingEmailRequest request = new BookingEmailRequest(
                "gamer@example.com",
                "Alice",
                "BKG-1001",
                "PC-02",
                "2026-10-22",
                "16:00 - 18:00",
                "LKR 1,200"
        );

        EmailContent content = emailTemplateService.createBookingRescheduledEmail(request);

        assertThat(content.subject()).isEqualTo("Booking Rescheduled - Gaming Castle");
        assertThat(content.htmlBody()).contains("Hi Alice,");
        assertThat(content.htmlBody()).contains("PC-02");
        assertThat(content.htmlBody()).contains("2026-10-22");
    }

    @Test
    @DisplayName("createBookingCancelledEmail: generates cancellation email with refund policy note")
    void createBookingCancelledEmail_shouldGenerateEmail() {
        BookingEmailRequest request = new BookingEmailRequest(
                "gamer@example.com",
                "Alice",
                "BKG-1001",
                "PS5-01",
                "2026-10-20",
                "14:00 - 16:00",
                "LKR 1,000"
        );

        EmailContent content = emailTemplateService.createBookingCancelledEmail(request);

        assertThat(content.subject()).isEqualTo("Booking Cancelled - Gaming Castle");
        assertThat(content.htmlBody()).contains("Hi Alice,");
        assertThat(content.htmlBody()).contains("cancelled");
        assertThat(content.htmlBody()).contains("BKG-1001");
    }

    @Test
    @DisplayName("createTournamentRegistrationEmail: generates tournament registration confirmation")
    void createTournamentRegistrationEmail_shouldGenerateEmail() {
        TournamentRegistrationEmailRequest request = new TournamentRegistrationEmailRequest(
                "gamer@example.com",
                "Bob",
                "Tekken 8 Clash",
                "Tekken 8",
                "2026-11-01",
                "LKR 500"
        );

        EmailContent content = emailTemplateService.createTournamentRegistrationEmail(request);

        assertThat(content.subject()).isEqualTo("Tournament Registration Confirmed - Tekken 8 Clash");
        assertThat(content.htmlBody()).contains("Bob");
        assertThat(content.htmlBody()).contains("Tekken 8 Clash");
        assertThat(content.htmlBody()).contains("Tekken 8");
        assertThat(content.htmlBody()).contains("LKR 500");
    }

    @Test
    @DisplayName("createTournamentCancelledEmail: generates tournament cancellation notice")
    void createTournamentCancelledEmail_shouldGenerateEmail() {
        TournamentCancelledEmailRequest request = new TournamentCancelledEmailRequest(
                "gamer@example.com",
                "Bob",
                "Tekken 8 Clash",
                "Technical issues"
        );

        EmailContent content = emailTemplateService.createTournamentCancelledEmail(request);

        assertThat(content.subject()).isEqualTo("Tournament Cancelled - Tekken 8 Clash");
        assertThat(content.htmlBody()).contains("Bob");
        assertThat(content.htmlBody()).contains("Tekken 8 Clash");
        assertThat(content.htmlBody()).contains("Technical issues");
    }

    @Test
    @DisplayName("createTournamentResultsEmail: generates tournament results published notice")
    void createTournamentResultsEmail_shouldGenerateEmail() {
        TournamentResultsEmailRequest request = new TournamentResultsEmailRequest(
                "gamer@example.com",
                "Bob",
                "Tekken 8 Clash",
                "Tekken 8",
                "ChampionCharlie",
                "Congratulations to all participants!"
        );

        EmailContent content = emailTemplateService.createTournamentResultsEmail(request);

        assertThat(content.subject()).isEqualTo("Tournament Results Published - Tekken 8 Clash");
        assertThat(content.htmlBody()).contains("Tekken 8 Clash");
        assertThat(content.htmlBody()).contains("ChampionCharlie");
        assertThat(content.htmlBody()).contains("Congratulations to all participants!");
    }

    @Test
    @DisplayName("createTournamentReminderEmail: generates tournament reminder email")
    void createTournamentReminderEmail_shouldGenerateEmail() {
        TournamentReminderEmailRequest request = new TournamentReminderEmailRequest(
                "gamer@example.com",
                "Bob",
                "Tekken 8 Clash",
                "Tekken 8",
                "2026-11-01 10:00",
                "Dave",
                "Quarter Finals"
        );

        EmailContent content = emailTemplateService.createTournamentReminderEmail(request);

        assertThat(content.subject()).contains("2-Hour Reminder: Upcoming Tournament Tekken 8 Clash");
        assertThat(content.htmlBody()).contains("Tekken 8 Clash");
        assertThat(content.htmlBody()).contains("Dave");
        assertThat(content.htmlBody()).contains("Quarter Finals");
    }
}
