package com.gamingcastle.tournamentservice.service;

import com.gamingcastle.tournamentservice.dto.RegistrationResponse;
import com.gamingcastle.tournamentservice.entity.RegistrationStatus;
import com.gamingcastle.tournamentservice.entity.Tournament;
import com.gamingcastle.tournamentservice.entity.TournamentRegistration;
import com.gamingcastle.tournamentservice.entity.TournamentStatus;
import com.gamingcastle.tournamentservice.exception.DuplicateRegistrationException;
import com.gamingcastle.tournamentservice.exception.PaymentFailedException;
import com.gamingcastle.tournamentservice.exception.RegistrationClosedException;
import com.gamingcastle.tournamentservice.exception.TournamentNotFoundException;
import com.gamingcastle.tournamentservice.repository.TournamentRegistrationRepository;
import com.gamingcastle.tournamentservice.repository.TournamentRepository;
import com.gamingcastle.tournamentservice.client.NotificationClient;
import com.gamingcastle.tournamentservice.client.UserClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RegistrationServiceImpl implements RegistrationService {

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final NotificationClient notificationClient;
    private final UserClient userClient;

    public RegistrationServiceImpl(
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
    public RegistrationResponse registerForTournament(UUID tournamentId, UUID userId) {
        return registerForTournament(tournamentId, userId, null, null);
    }

    @Override
    @Transactional
    public RegistrationResponse registerForTournament(UUID tournamentId, UUID userId, String email, String fullName) {
        // 1. Load tournament or 404
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException(
                        "Tournament not found: " + tournamentId));

        // 2. BR-06: registration must be open (deadline + capacity + status)
        boolean isBeforeDeadline = Instant.now().isBefore(tournament.getRegistrationDeadline());
        boolean isNotFull = tournament.getParticipantCount() < tournament.getMaxParticipants();
        boolean isUpcoming = tournament.getStatus() == TournamentStatus.UPCOMING;

        if (!isBeforeDeadline || !isNotFull || !isUpcoming) {
            throw new RegistrationClosedException(
                    "Registration is closed for tournament: " + tournament.getName());
        }

        // 3. Duplicate registration guard (UC-08 exception flow)
        boolean alreadyRegistered = registrationRepository
                .findByTournamentId(tournamentId)
                .stream()
                .anyMatch(r -> r.getUserId().equals(userId)
                        && r.getStatus() != RegistrationStatus.CANCELLED);

        if (alreadyRegistered) {
            throw new DuplicateRegistrationException(
                    "User " + userId + " is already registered for tournament: " + tournament.getName());
        }

        // 4. Resolve user email and full name
        String resolvedEmail = email;
        String resolvedName = fullName;
        if (resolvedEmail == null || resolvedEmail.isBlank()) {
            var userOpt = userClient.getUserById(userId);
            if (userOpt.isPresent()) {
                resolvedEmail = userOpt.get().email();
                if (resolvedName == null || resolvedName.isBlank()) {
                    resolvedName = userOpt.get().fullName();
                }
            }
        }

        // 5. Create PENDING_PAYMENT registration
        TournamentRegistration registration = TournamentRegistration.builder()
                .tournament(tournament)
                .userId(userId)
                .userEmail(resolvedEmail)
                .userName(resolvedName)
                .status(RegistrationStatus.PENDING_PAYMENT)
                .build();

        registration = registrationRepository.save(registration);

        // 6. Call Payment Service for entry fee (UC-08: process payment step)
        //    TODO (feature/43, blocked by PM-1/PM-2): replace stub with PaymentClient call
        UUID paymentId = processEntryFeePayment(tournament.getEntryFee(), userId, registration.getId());

        // 7. Payment succeeded — confirm registration and increment participantCount (activity diagram step)
        registration.setPaymentId(paymentId);
        registration.setStatus(RegistrationStatus.CONFIRMED);
        tournament.setParticipantCount(tournament.getParticipantCount() + 1);

        registrationRepository.save(registration);
        tournamentRepository.save(tournament);

        // 8. Notification (feature/45): send tournament-registration-confirmed notification (activity diagram step 11)
        sendRegistrationConfirmedNotification(tournament, resolvedEmail, resolvedName);

        return mapToResponse(registration);
    }

    private void sendRegistrationConfirmedNotification(Tournament tournament, String recipientEmail, String recipientName) {
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            String startDateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(tournament.getStartDate());
            String entryFeeStr = tournament.getEntryFee() != null
                    ? "LKR " + tournament.getEntryFee().toPlainString()
                    : "Free";

            notificationClient.sendTournamentRegistrationConfirmed(
                    new NotificationClient.TournamentRegistrationEmailRequest(
                            recipientEmail,
                            recipientName != null ? recipientName : "Gamer",
                            tournament.getName(),
                            tournament.getGameTitle(),
                            startDateStr,
                            entryFeeStr
                    )
            );
        } else {
            System.err.println("Skipping tournament registration email: no recipient email found for tournament: " + tournament.getName());
        }
    }

    @Override
    public List<RegistrationResponse> getRegistrationsForTournament(UUID tournamentId) {
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new TournamentNotFoundException("Tournament not found: " + tournamentId);
        }
        return registrationRepository.findByTournamentId(tournamentId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Payment Service integration stub.
     * TODO (feature/43): replace with PaymentClient.chargeEntryFee(amount, userId, registrationId)
     * once PM-1/PM-2 (Payment Service checkout) is built.
     * Throws PaymentFailedException on failure so the caller (UC-08 alt flow) is notified correctly.
     */
    private UUID processEntryFeePayment(java.math.BigDecimal amount, UUID userId, UUID registrationId) {
        // Stub: simulate successful payment and return a fake paymentId
        // Real implementation: call payment-service REST endpoint via Feign/RestClient
        return UUID.randomUUID();
    }

    private RegistrationResponse mapToResponse(TournamentRegistration r) {
        return new RegistrationResponse(
                r.getId(),
                r.getTournament().getId(),
                r.getTournament().getName(),
                r.getUserId(),
                r.getStatus(),
                r.getPaymentId(),
                r.getRegisteredAt()
        );
    }
}
