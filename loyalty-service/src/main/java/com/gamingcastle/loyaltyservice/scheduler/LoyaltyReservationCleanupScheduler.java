package com.gamingcastle.loyaltyservice.scheduler;

import com.gamingcastle.loyaltyservice.service.LoyaltyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class LoyaltyReservationCleanupScheduler {

    private final LoyaltyService loyaltyService;

    public LoyaltyReservationCleanupScheduler(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @Scheduled(fixedRateString = "${loyalty.reservation-cleanup-ms:60000}")
    public void releaseExpiredReservations() {
        List<UUID> bookingIds;
        try {
            bookingIds = loyaltyService.findExpiredReservationBookingIds();
        } catch (RuntimeException ex) {
            log.error("Could not look up expired loyalty reservations: {}", ex.getMessage(), ex);
            return;
        }

        int released = 0;
        for (UUID bookingId : bookingIds) {
            try {
                loyaltyService.releaseRedemption(bookingId, "Reservation expired: payment was not completed in time");
                released++;
            } catch (RuntimeException ex) {
                log.error("Failed to release expired reservation: bookingId={}", bookingId, ex);
            }
        }
        if (released > 0) {
            log.info("Released expired loyalty reservations: count={}", released);
        }
    }
}