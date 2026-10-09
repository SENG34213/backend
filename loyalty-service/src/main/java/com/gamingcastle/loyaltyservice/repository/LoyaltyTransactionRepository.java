package com.gamingcastle.loyaltyservice.repository;

import com.gamingcastle.loyaltyservice.entity.LoyaltyTransaction;
import com.gamingcastle.loyaltyservice.entity.LoyaltyTransactionStatus;
import com.gamingcastle.loyaltyservice.entity.LoyaltyTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, UUID> {

    Page<LoyaltyTransaction> findByUserId(UUID userId, Pageable pageable);

    Optional<LoyaltyTransaction> findByBookingIdAndTypeAndStatus(
            UUID bookingId,
            LoyaltyTransactionType type,
            LoyaltyTransactionStatus status
    );

    Optional<LoyaltyTransaction> findFirstByBookingIdAndTypeAndStatusInOrderByCreatedAtDesc(
            UUID bookingId,
            LoyaltyTransactionType type,
            Collection<LoyaltyTransactionStatus> statuses
    );

    boolean existsByBookingIdAndType(UUID bookingId, LoyaltyTransactionType type);

    List<LoyaltyTransaction> findByTypeAndStatusAndCreatedAtBefore(
            LoyaltyTransactionType type,
            LoyaltyTransactionStatus status,
            Instant cutoff
    );
}