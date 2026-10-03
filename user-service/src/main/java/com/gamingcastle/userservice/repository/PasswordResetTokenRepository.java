package com.gamingcastle.userservice.repository;

import com.gamingcastle.userservice.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Most recent unused code for a user — the only one a reset attempt may be checked against. */
    Optional<PasswordResetToken> findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(UUID userId);
}
