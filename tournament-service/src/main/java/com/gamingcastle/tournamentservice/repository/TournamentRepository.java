package com.gamingcastle.tournamentservice.repository;

import com.gamingcastle.tournamentservice.entity.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TournamentRepository extends JpaRepository<Tournament, UUID> {
    List<Tournament> findByStartDateLessThanEqualAndReminderSentFalse(Instant maxStartDate);
}

