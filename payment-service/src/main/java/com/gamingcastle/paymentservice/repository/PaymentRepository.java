package com.gamingcastle.paymentservice.repository;

import com.gamingcastle.paymentservice.entity.Payment;
import com.gamingcastle.paymentservice.entity.PaymentMethod;
import com.gamingcastle.paymentservice.entity.PaymentStatus;
import com.gamingcastle.paymentservice.entity.ReferenceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    List<Payment> findByUserId(UUID userId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    // ── Dashboard: total revenue for a time window (G11) ─────────────────────

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.status = com.gamingcastle.paymentservice.entity.PaymentStatus.SUCCESS
          AND p.createdAt >= :from
          AND p.createdAt <= :to
        """)
    BigDecimal sumRevenueInRange(@Param("from") Instant from, @Param("to") Instant to);

    // ── Dashboard: payment count by status in a time window (G11) ────────────

    @Query("""
        SELECT COUNT(p)
        FROM Payment p
        WHERE p.status = :status
          AND p.createdAt >= :from
          AND p.createdAt <= :to
        """)
    long countByStatusInRange(
            @Param("status") PaymentStatus status,
            @Param("from")   Instant from,
            @Param("to")     Instant to
    );

    // ── Revenue trend: daily totals for the last N days (G11) ────────────────

    @Query(value = """
        SELECT CAST(created_at AT TIME ZONE 'Asia/Colombo' AS DATE) AS day,
               COALESCE(SUM(amount), 0)                              AS revenue
        FROM payments
        WHERE status = 'SUCCESS'
          AND created_at >= :from
        GROUP BY day
        ORDER BY day
        """, nativeQuery = true)
    List<Object[]> dailyRevenueSince(@Param("from") Instant from);
}

