package com.gamingcastle.userservice.repository;

import com.gamingcastle.userservice.entity.PasswordResetToken;
import com.gamingcastle.userservice.entity.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findFirstByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(
            UUID userId, VerificationPurpose purpose);
}
