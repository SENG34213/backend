package com.gamingcastle.loyaltyservice.repository;

import com.gamingcastle.loyaltyservice.entity.LoyaltyAccount;
import com.gamingcastle.loyaltyservice.entity.LoyaltyTier;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, UUID> {
    Optional<LoyaltyAccount> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoyaltyAccount a where a.userId = :userId")
    Optional<LoyaltyAccount> findByUserIdForUpdate(@Param("userId") UUID userId);

    long countByTier(LoyaltyTier tier);

    @Query("select coalesce(sum(a.pointsBalance), 0) from LoyaltyAccount a")
    long sumPointsBalance();

    @Query("select coalesce(sum(a.lifetimeEarned), 0) from LoyaltyAccount a")
    long sumLifetimeEarned();

    Page<LoyaltyAccount> findAll(Pageable pageable);
}
