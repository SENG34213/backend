package com.gamingcastle.loyaltyservice.repository;

import com.gamingcastle.loyaltyservice.entity.LoyaltyRules;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoyaltyRulesRepository extends JpaRepository<LoyaltyRules, UUID> {
    Optional<LoyaltyRules> findTopByOrderByUpdatedAtDesc();
}
