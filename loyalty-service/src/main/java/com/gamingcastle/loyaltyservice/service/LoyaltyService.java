package com.gamingcastle.loyaltyservice.service;

import com.gamingcastle.loyaltyservice.dto.request.AdjustPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.AwardPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.CalculateRedemptionRequest;
import com.gamingcastle.loyaltyservice.dto.request.RedeemPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.UpdateLoyaltyRulesRequest;
import com.gamingcastle.loyaltyservice.dto.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface LoyaltyService {

    LoyaltyAccountResponse getBalance(UUID userId);

    Page<TransactionHistoryResponse> getHistory(UUID userId, Pageable pageable);

    CalculationResponse calculateRedemption(CalculateRedemptionRequest request);

    LoyaltyRulesResponse getRules();

    LoyaltyRulesResponse updateRules(UpdateLoyaltyRulesRequest request, UUID adminId);

    AdminAdjustmentResponse adjustPoints(AdjustPointsRequest request);

    AwardPointsResponse awardPoints(AwardPointsRequest request);

    RedemptionReservationResponse reserveRedemption(RedeemPointsRequest request);

    LoyaltyStatusResponse confirmRedemption(UUID bookingId);

    LoyaltyStatusResponse releaseRedemption(UUID bookingId, String reason);

    ReverseLoyaltyResponse reverseBooking(UUID bookingId, String reason);

    List<UUID> findExpiredReservationBookingIds();

    AdminLoyaltyStatsResponse getLoyaltyStats();

    Page<LoyaltyAccountResponse> getAllAccounts(Pageable pageable);

    Page<TransactionHistoryResponse> getAllTransactions(Pageable pageable);
}
