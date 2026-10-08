package com.gamingcastle.notificationservice.controller;

import com.gamingcastle.notificationservice.dto.*;
import com.gamingcastle.notificationservice.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/welcome")
    public ResponseEntity<Void> sendWelcomeEmail(@RequestBody WelcomeEmailRequest request) {
        notificationService.sendWelcomeEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> sendPasswordResetEmail(@RequestBody PasswordResetEmailRequest request) {
        notificationService.sendPasswordResetEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/account-reactivation")
    public ResponseEntity<Void> sendAccountReactivationEmail(@RequestBody AccountReactivationEmailRequest request) {
        notificationService.sendAccountReactivationEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/booking-confirmed")
    public ResponseEntity<Void> sendBookingConfirmation(@RequestBody BookingEmailRequest request) {
        notificationService.sendBookingConfirmationEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/booking-rescheduled")
    public ResponseEntity<Void> sendBookingRescheduled(@RequestBody BookingEmailRequest request) {
        notificationService.sendBookingRescheduledEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/booking-cancelled")
    public ResponseEntity<Void> sendBookingCancelled(@RequestBody BookingEmailRequest request) {
        notificationService.sendBookingCancelledEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tournament-registration-confirmed")
    public ResponseEntity<Void> sendTournamentRegistrationConfirmation(
            @RequestBody TournamentRegistrationEmailRequest request) {
        notificationService.sendTournamentRegistrationEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tournament-cancelled")
    public ResponseEntity<Void> sendTournamentCancelled(
            @RequestBody TournamentCancelledEmailRequest request) {
        notificationService.sendTournamentCancelledEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tournament-results-published")
    public ResponseEntity<Void> sendTournamentResults(
            @RequestBody TournamentResultsEmailRequest request) {
        notificationService.sendTournamentResultsEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tournament-reminder")
    public ResponseEntity<Void> sendTournamentReminder(
            @RequestBody TournamentReminderEmailRequest request) {
        notificationService.sendTournamentReminderEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password-reset-sms")
    public ResponseEntity<Void> sendPasswordResetSms(@RequestBody PasswordResetSmsRequest request) {
        notificationService.sendPasswordResetSms(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/account-reactivation-sms")
    public ResponseEntity<Void> sendAccountReactivationSms(@RequestBody AccountReactivationSmsRequest request) {
        notificationService.sendAccountReactivationSms(request);
        return ResponseEntity.ok().build();
    }
}
