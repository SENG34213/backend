package com.gamingcastle.tournamentservice.service;

import com.gamingcastle.tournamentservice.dto.TournamentRequest;
import com.gamingcastle.tournamentservice.dto.TournamentResponse;
import com.gamingcastle.tournamentservice.dto.TournamentStatusUpdateRequest;
import com.gamingcastle.tournamentservice.entity.Tournament;
import com.gamingcastle.tournamentservice.entity.TournamentStatus;
import com.gamingcastle.tournamentservice.exception.TournamentNotFoundException;
import com.gamingcastle.tournamentservice.repository.TournamentRepository;
import com.gamingcastle.tournamentservice.client.NotificationClient;
import com.gamingcastle.tournamentservice.client.UserClient;
import com.gamingcastle.tournamentservice.entity.RegistrationStatus;
import com.gamingcastle.tournamentservice.repository.TournamentRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TournamentServiceImpl implements TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final NotificationClient notificationClient;
    private final UserClient userClient;

    public TournamentServiceImpl(
            TournamentRepository tournamentRepository,
            TournamentRegistrationRepository registrationRepository,
            NotificationClient notificationClient,
            UserClient userClient
    ) {
        this.tournamentRepository = tournamentRepository;
        this.registrationRepository = registrationRepository;
        this.notificationClient = notificationClient;
        this.userClient = userClient;
    }

    @Override
    @Transactional
    public TournamentResponse createAndPublishTournament(TournamentRequest request, UUID adminId) {
        Tournament tournament = Tournament.builder()
                .name(request.name())
                .gameTitle(request.gameTitle())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .entryFee(request.entryFee())
                .maxParticipants(request.maxParticipants())
                .participantCount(0)
                .registrationDeadline(request.registrationDeadline())
                .status(TournamentStatus.UPCOMING)
                .createdByAdminId(adminId)
                .build();

        Tournament saved = tournamentRepository.save(tournament);
        return mapToResponse(saved);
    }

    @Override
    public List<TournamentResponse> getAllTournaments() {
        return tournamentRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public TournamentResponse getTournamentById(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + id));
        return mapToResponse(tournament);
    }

    @Override
    @Transactional
    public TournamentResponse updateTournamentStatus(UUID id, TournamentStatusUpdateRequest request) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + id));
        
        tournament.setStatus(request.status());
        return mapToResponse(tournamentRepository.save(tournament));
    }

    @Override
    @Transactional
    public TournamentResponse cancelTournament(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + id));
        
        tournament.setStatus(TournamentStatus.CANCELLED);
        Tournament saved = tournamentRepository.save(tournament);

        // Fan-out notifications to all registered participants (UC-09 alt flow)
        fanOutTournamentCancellation(tournament);
        
        return mapToResponse(saved);
    }

    private void fanOutTournamentCancellation(Tournament tournament) {
        var registrations = registrationRepository.findByTournamentId(tournament.getId());
        for (var reg : registrations) {
            if (reg.getStatus() == RegistrationStatus.CONFIRMED || reg.getStatus() == RegistrationStatus.PENDING_PAYMENT) {
                reg.setStatus(RegistrationStatus.CANCELLED);
                registrationRepository.save(reg);

                String recipientEmail = reg.getUserEmail();
                String recipientName = reg.getUserName();

                if (recipientEmail == null || recipientEmail.isBlank()) {
                    var userOpt = userClient.getUserById(reg.getUserId());
                    recipientEmail = userOpt.map(UserClient.UserSummaryDto::email).orElse(null);
                    if (recipientName == null || recipientName.isBlank()) {
                        recipientName = userOpt.map(UserClient.UserSummaryDto::fullName).orElse("Gamer");
                    }
                }

                if (recipientEmail != null && !recipientEmail.isBlank()) {
                    notificationClient.sendTournamentCancelled(
                            new NotificationClient.TournamentCancelledEmailRequest(
                                    recipientEmail,
                                    recipientName != null ? recipientName : "Gamer",
                                    tournament.getName(),
                                    "The tournament has been cancelled by the administrator."
                            )
                    );
                } else {
                    System.err.println("Skipping cancellation email: no email found for participant " + reg.getUserId());
                }
            }
        }
    }

    @Override
    public boolean isRegistrationOpen(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new TournamentNotFoundException("Tournament not found: " + id));
        
        boolean isNotFull = tournament.getParticipantCount() < tournament.getMaxParticipants();
        boolean isBeforeDeadline = Instant.now().isBefore(tournament.getRegistrationDeadline());
        
        return isNotFull && isBeforeDeadline && tournament.getStatus() == TournamentStatus.UPCOMING;
    }

    private TournamentResponse mapToResponse(Tournament t) {
        boolean isRegistrationOpen = t.getParticipantCount() < t.getMaxParticipants() &&
                Instant.now().isBefore(t.getRegistrationDeadline()) &&
                t.getStatus() == TournamentStatus.UPCOMING;

        return new TournamentResponse(
                t.getId(),
                t.getName(),
                t.getGameTitle(),
                t.getStartDate(),
                t.getEndDate(),
                t.getEntryFee(),
                t.getMaxParticipants(),
                t.getParticipantCount(),
                t.getRegistrationDeadline(),
                t.getStatus(),
                t.getCreatedByAdminId(),
                t.getCreatedAt(),
                isRegistrationOpen
        );
    }
}
