package com.gamingcastle.bookingservice.repository;

import com.gamingcastle.bookingservice.entity.CompensatingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompensatingTransactionRepository extends JpaRepository<CompensatingTransaction, UUID> {
    List<CompensatingTransaction> findByBookingId(UUID bookingId);
    List<CompensatingTransaction> findByStatus(String status);
}
