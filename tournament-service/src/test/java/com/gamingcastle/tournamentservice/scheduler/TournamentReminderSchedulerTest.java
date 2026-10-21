package com.gamingcastle.tournamentservice.scheduler;

import com.gamingcastle.tournamentservice.service.TournamentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TournamentReminderSchedulerTest {

    @Mock
    private TournamentService tournamentService;

    @InjectMocks
    private TournamentReminderScheduler scheduler;

    @Test
    @DisplayName("schedulePreTournamentReminders should invoke tournamentService")
    void schedulePreTournamentReminders_shouldInvokeService() {
        scheduler.schedulePreTournamentReminders();

        verify(tournamentService).processUpcomingPreTournamentReminders();
    }

    @Test
    @DisplayName("schedulePreTournamentReminders should catch exceptions gracefully without throwing")
    void schedulePreTournamentReminders_shouldCatchExceptionsGracefully() {
        doThrow(new RuntimeException("Database unreachable"))
                .when(tournamentService).processUpcomingPreTournamentReminders();

        // Should not throw
        scheduler.schedulePreTournamentReminders();

        verify(tournamentService).processUpcomingPreTournamentReminders();
    }
}
