package com.gamingcastle.loyaltyservice.scheduler;

import com.gamingcastle.loyaltyservice.service.LoyaltyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyReservationCleanupSchedulerTest {

    @Mock
    private LoyaltyService loyaltyService;

    private LoyaltyReservationCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LoyaltyReservationCleanupScheduler(loyaltyService);
    }

    @Test
    @DisplayName("Given expired reservation booking IDs exist, when releaseExpiredReservations, then calls releaseRedemption for each")
    void releaseExpiredReservations_whenExpiredFound_shouldReleaseEach() {
        // Arrange
        UUID booking1 = UUID.randomUUID();
        UUID booking2 = UUID.randomUUID();
        when(loyaltyService.findExpiredReservationBookingIds()).thenReturn(List.of(booking1, booking2));

        // Act
        scheduler.releaseExpiredReservations();

        // Assert
        verify(loyaltyService).releaseRedemption(eq(booking1), anyString());
        verify(loyaltyService).releaseRedemption(eq(booking2), anyString());
    }

    @Test
    @DisplayName("Given no expired reservations, when releaseExpiredReservations, then does not call releaseRedemption")
    void releaseExpiredReservations_whenNoneFound_shouldDoNothing() {
        // Arrange
        when(loyaltyService.findExpiredReservationBookingIds()).thenReturn(List.of());

        // Act
        scheduler.releaseExpiredReservations();

        // Assert
        verify(loyaltyService, never()).releaseRedemption(any(), anyString());
    }

    @Test
    @DisplayName("Given lookup throws exception, when releaseExpiredReservations, then handles gracefully without throwing")
    void releaseExpiredReservations_whenLookupThrows_shouldHandleGracefully() {
        // Arrange
        when(loyaltyService.findExpiredReservationBookingIds()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThatCode(() -> scheduler.releaseExpiredReservations()).doesNotThrowAnyException();
        verify(loyaltyService, never()).releaseRedemption(any(), anyString());
    }

    @Test
    @DisplayName("Given single release fails, when releaseExpiredReservations, then continues releasing others")
    void releaseExpiredReservations_whenOneReleaseFails_shouldContinueRemaining() {
        // Arrange
        UUID booking1 = UUID.randomUUID();
        UUID booking2 = UUID.randomUUID();
        when(loyaltyService.findExpiredReservationBookingIds()).thenReturn(List.of(booking1, booking2));
        doThrow(new RuntimeException("Lock conflict"))
                .when(loyaltyService).releaseRedemption(eq(booking1), anyString());

        // Act & Assert
        assertThatCode(() -> scheduler.releaseExpiredReservations()).doesNotThrowAnyException();
        verify(loyaltyService).releaseRedemption(eq(booking1), anyString());
        verify(loyaltyService).releaseRedemption(eq(booking2), anyString());
    }
}
