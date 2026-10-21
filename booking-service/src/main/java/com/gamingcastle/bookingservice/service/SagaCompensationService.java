package com.gamingcastle.bookingservice.service;

import com.gamingcastle.bookingservice.client.LoyaltyClient;
import com.gamingcastle.bookingservice.entity.CompensatingTransaction;
import com.gamingcastle.bookingservice.repository.CompensatingTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates compensating transactions (Saga pattern) across booking,
 * payment, and loyalty services to guarantee eventual consistency and auditability.
 */
@Service
@RequiredArgsConstructor
public class SagaCompensationService {

    private static final Logger log = LoggerFactory.getLogger(SagaCompensationService.class);

    private final CompensatingTransactionRepository compensatingTransactionRepository;
    private final LoyaltyClient loyaltyClient;

    @Transactional
    public CompensatingTransaction recordAction(UUID bookingId, String actionType, String payload) {
        CompensatingTransaction tx = CompensatingTransaction.builder()
                .bookingId(bookingId)
                .actionType(actionType)
                .status("RECORDED")
                .payload(payload)
                .retryCount(0)
                .build();
        return compensatingTransactionRepository.save(tx);
    }

    @Transactional
    public void executeCompensation(UUID bookingId, String actionType, String reason) {
        CompensatingTransaction tx = recordAction(bookingId, actionType, reason);
        try {
            if ("LOYALTY_REVERSE".equalsIgnoreCase(actionType)) {
                log.info("Saga Compensation: Reversing loyalty points for bookingId={}, reason={}", bookingId, reason);
                loyaltyClient.reverse(bookingId, reason);
            }
            tx.setStatus("COMPLETED");
            compensatingTransactionRepository.save(tx);
            log.info("Saga Compensation COMPLETED for bookingId={}, action={}", bookingId, actionType);
        } catch (Exception e) {
            log.error("Saga Compensation FAILED for bookingId={}, action={}: {}", bookingId, actionType, e.getMessage());
            tx.setStatus("FAILED");
            tx.setFailureReason(e.getMessage());
            tx.setRetryCount(tx.getRetryCount() + 1);
            compensatingTransactionRepository.save(tx);
        }
    }
}
