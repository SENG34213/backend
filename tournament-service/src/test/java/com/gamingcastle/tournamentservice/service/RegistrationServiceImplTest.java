package com.gamingcastle.tournamentservice.service;

import com.gamingcastle.tournamentservice.client.NotificationClient;
import com.gamingcastle.tournamentservice.client.PaymentClient;
import com.gamingcastle.tournamentservice.client.UserClient;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private TournamentRegistrationRepository registrationRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private UserClient userClient;

    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    private UUID tournamentId;
    private UUID userId;
    private Tournament tournament;

    @BeforeEach
    void setUp() {
        tournamentId = UUID.randomUUID();
        userId = UUID.randomUUID();

        tournament = Tournament.builder()
                .id(tournamentId)
                .name("Apex Arena")
                .gameTitle("Apex Legends")
                .startDate(Instant.now().plus(3, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(4, ChronoUnit.DAYS))
                .entryFee(new BigDecimal("2500.00"))
                .maxParticipants(20)
                .participantCount(5)
                .registrationDeadline(Instant.now().plus(2, ChronoUnit.DAYS))
                .status(TournamentStatus.UPCOMING)
                .createdByAdminId(UUID.randomUUID())
                .createdAt(Instant.now())
                .reminderSent(false)
                .build();
    }

    // ── registerForTournament ───────────────────────────────────────────────

    @Test
    @DisplayName("registerForTournament two-arg method should delegate to four-arg method and complete registration")
    void registerForTournament_twoArgOverload_shouldDelegate() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());
        when(userClient.getUserById(userId)).thenReturn(Optional.of(
                new UserClient.UserSummaryDto(userId, "apexking", "apex@test.com", "Apex King", "CUSTOMER")
        ));

        UUID paymentId = UUID.randomUUID();
        when(paymentClient.chargeEntryFee(eq(tournament.getEntryFee()), eq(userId), any())).thenReturn(paymentId);

        when(registrationRepository.save(any(TournamentRegistration.class))).thenAnswer(i -> {
            TournamentRegistration r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setRegisteredAt(Instant.now());
            return r;
        });

        RegistrationResponse response = registrationService.registerForTournament(tournamentId, userId);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(response.paymentId()).isEqualTo(paymentId);
        assertThat(tournament.getParticipantCount()).isEqualTo(6);
        verify(notificationClient).sendTournamentRegistrationConfirmed(any());
    }

    @Test
    @DisplayName("registerForTournament should throw TournamentNotFoundException when tournament does not exist")
    void registerForTournament_shouldThrowNotFound_whenTournamentMissing() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.registerForTournament(tournamentId, userId, "user@test.com", "User"))
                .isInstanceOf(TournamentNotFoundException.class)
                .hasMessageContaining(tournamentId.toString());
    }

    @Test
    @DisplayName("registerForTournament should throw RegistrationClosedException when past deadline (BR-06)")
    void registerForTournament_shouldThrowRegistrationClosed_whenPastDeadline() {
        tournament.setRegistrationDeadline(Instant.now().minus(10, ChronoUnit.MINUTES));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        assertThatThrownBy(() -> registrationService.registerForTournament(tournamentId, userId, "user@test.com", "User"))
                .isInstanceOf(RegistrationClosedException.class)
                .hasMessageContaining("Registration is closed");
    }

    @Test
    @DisplayName("registerForTournament should throw RegistrationClosedException when at capacity (BR-06)")
    void registerForTournament_shouldThrowRegistrationClosed_whenFull() {
        tournament.setParticipantCount(20);
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        assertThatThrownBy(() -> registrationService.registerForTournament(tournamentId, userId, "user@test.com", "User"))
                .isInstanceOf(RegistrationClosedException.class);
    }

    @Test
    @DisplayName("registerForTournament should throw RegistrationClosedException when tournament status is not UPCOMING")
    void registerForTournament_shouldThrowRegistrationClosed_whenNotUpcoming() {
        tournament.setStatus(TournamentStatus.ONGOING);
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        assertThatThrownBy(() -> registrationService.registerForTournament(tournamentId, userId, "user@test.com", "User"))
                .isInstanceOf(RegistrationClosedException.class);
    }

    @Test
    @DisplayName("registerForTournament should throw DuplicateRegistrationException when user already registered (UC-08)")
    void registerForTournament_shouldThrowDuplicate_whenAlreadyRegistered() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        TournamentRegistration existingReg = TournamentRegistration.builder()
                .id(UUID.randomUUID())
                .tournament(tournament)
                .userId(userId)
                .status(RegistrationStatus.CONFIRMED)
                .build();

        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(existingReg));

        assertThatThrownBy(() -> registrationService.registerForTournament(tournamentId, userId, "user@test.com", "User"))
                .isInstanceOf(DuplicateRegistrationException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("registerForTournament should permit registration if previous registration was CANCELLED")
    void registerForTournament_shouldAllowRegistration_whenPreviousWasCancelled() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));

        TournamentRegistration cancelledReg = TournamentRegistration.builder()
                .id(UUID.randomUUID())
                .tournament(tournament)
                .userId(userId)
                .status(RegistrationStatus.CANCELLED)
                .build();

        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(cancelledReg));

        UUID paymentId = UUID.randomUUID();
        when(paymentClient.chargeEntryFee(eq(tournament.getEntryFee()), eq(userId), any())).thenReturn(paymentId);
        when(registrationRepository.save(any(TournamentRegistration.class))).thenAnswer(i -> {
            TournamentRegistration r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setRegisteredAt(Instant.now());
            return r;
        });

        RegistrationResponse response = registrationService.registerForTournament(tournamentId, userId, "user@test.com", "User");

        assertThat(response.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(response.paymentId()).isEqualTo(paymentId);
    }

    @Test
    @DisplayName("registerForTournament should cancel registration and rethrow PaymentFailedException on payment error")
    void registerForTournament_shouldCancelAndRethrow_whenPaymentFails() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        TournamentRegistration pendingReg = TournamentRegistration.builder()
                .id(UUID.randomUUID())
                .tournament(tournament)
                .userId(userId)
                .status(RegistrationStatus.PENDING_PAYMENT)
                .build();

        when(registrationRepository.save(any(TournamentRegistration.class))).thenReturn(pendingReg);
        when(paymentClient.chargeEntryFee(any(), eq(userId), any()))
                .thenThrow(new PaymentFailedException("Card declined"));

        assertThatThrownBy(() -> registrationService.registerForTournament(tournamentId, userId, "user@test.com", "User"))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("Card declined");

        assertThat(pendingReg.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        verify(registrationRepository).save(any(TournamentRegistration.class));
        verify(registrationRepository).saveAndFlush(pendingReg);
        verify(notificationClient, never()).sendTournamentRegistrationConfirmed(any());
    }

    @Test
    @DisplayName("registerForTournament should catch DataIntegrityViolationException and rethrow DuplicateRegistrationException")
    void registerForTournament_shouldCatchDataIntegrityViolation_andThrowDuplicate() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        when(registrationRepository.save(any(TournamentRegistration.class))).thenAnswer(i -> {
            TournamentRegistration r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(paymentClient.chargeEntryFee(any(), eq(userId), any())).thenReturn(UUID.randomUUID());

        doThrow(new DataIntegrityViolationException("unique constraint violated"))
                .when(registrationRepository).saveAndFlush(any());

        assertThatThrownBy(() -> registrationService.registerForTournament(tournamentId, userId, "user@test.com", "User"))
                .isInstanceOf(DuplicateRegistrationException.class);
    }

    @Test
    @DisplayName("registerForTournament should format Free entry fee when entryFee is null")
    void registerForTournament_shouldHandleFreeEntryFee() {
        tournament.setEntryFee(null);
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        when(registrationRepository.save(any(TournamentRegistration.class))).thenAnswer(i -> {
            TournamentRegistration r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(paymentClient.chargeEntryFee(isNull(), eq(userId), any())).thenReturn(null);

        RegistrationResponse response = registrationService.registerForTournament(tournamentId, userId, "free@test.com", "Free Player");

        assertThat(response.status()).isEqualTo(RegistrationStatus.CONFIRMED);

        ArgumentCaptor<NotificationClient.TournamentRegistrationEmailRequest> captor =
                ArgumentCaptor.forClass(NotificationClient.TournamentRegistrationEmailRequest.class);
        verify(notificationClient).sendTournamentRegistrationConfirmed(captor.capture());
        assertThat(captor.getValue().entryFee()).isEqualTo("Free");
    }

    @Test
    @DisplayName("registerForTournament should skip confirmation email when resolved email is blank")
    void registerForTournament_shouldSkipEmail_whenResolvedEmailIsBlank() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());
        when(userClient.getUserById(userId)).thenReturn(Optional.empty());

        when(registrationRepository.save(any(TournamentRegistration.class))).thenAnswer(i -> {
            TournamentRegistration r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(paymentClient.chargeEntryFee(any(), eq(userId), any())).thenReturn(UUID.randomUUID());

        RegistrationResponse response = registrationService.registerForTournament(tournamentId, userId, "", "");

        assertThat(response.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        verify(notificationClient, never()).sendTournamentRegistrationConfirmed(any());
    }

    // ── getRegistrationsForTournament ───────────────────────────────────────

    @Test
    @DisplayName("getRegistrationsForTournament should return registrations when tournament exists")
    void getRegistrationsForTournament_shouldReturnList_whenFound() {
        when(tournamentRepository.existsById(tournamentId)).thenReturn(true);

        TournamentRegistration reg = TournamentRegistration.builder()
                .id(UUID.randomUUID())
                .tournament(tournament)
                .userId(userId)
                .status(RegistrationStatus.CONFIRMED)
                .paymentId(UUID.randomUUID())
                .registeredAt(Instant.now())
                .build();

        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(reg));

        List<RegistrationResponse> list = registrationService.getRegistrationsForTournament(tournamentId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("getRegistrationsForTournament should throw TournamentNotFoundException when tournament missing")
    void getRegistrationsForTournament_shouldThrowNotFound_whenMissing() {
        when(tournamentRepository.existsById(tournamentId)).thenReturn(false);

        assertThatThrownBy(() -> registrationService.getRegistrationsForTournament(tournamentId))
                .isInstanceOf(TournamentNotFoundException.class);
    }
}
