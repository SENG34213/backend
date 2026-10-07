package com.gamingcastle.userservice.repository;

import com.gamingcastle.userservice.entity.PasswordResetToken;
import com.gamingcastle.userservice.entity.VerificationPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findFirstByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
            UUID userId, VerificationPurpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(UUID userId, VerificationPurpose purpose, Instant after);
}
