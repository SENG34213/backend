package com.gamingcastle.tournamentservice.scheduler;

import com.gamingcastle.tournamentservice.service.TournamentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TournamentReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(TournamentReminderScheduler.class);
    private final TournamentService tournamentService;

    public TournamentReminderScheduler(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    /**
     * Periodically checks for tournaments starting in <= 2 hours
     * and sends 2-hour pre-tournament notification emails to registered participants.
     * Runs every 5 minutes.
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void schedulePreTournamentReminders() {
        log.info("Running scheduled check for upcoming 2-hour tournament reminders...");
        try {
            tournamentService.processUpcomingPreTournamentReminders();
        } catch (Exception e) {
            log.error("Error processing pre-tournament reminders: {}", e.getMessage(), e);
        }
    }
}
