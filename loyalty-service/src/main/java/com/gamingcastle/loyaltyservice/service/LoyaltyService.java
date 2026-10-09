package com.gamingcastle.loyaltyservice.service;

import com.gamingcastle.loyaltyservice.dto.request.AdjustPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.AwardPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.CalculateRedemptionRequest;
import com.gamingcastle.loyaltyservice.dto.request.RedeemPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.UpdateLoyaltyRulesRequest;
import com.gamingcastle.loyaltyservice.dto.response.AdminAdjustmentResponse;
import com.gamingcastle.loyaltyservice.dto.response.AwardPointsResponse;
import com.gamingcastle.loyaltyservice.dto.response.CalculationResponse;
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyAccountResponse;
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyRulesResponse;
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyStatusResponse;
import com.gamingcastle.loyaltyservice.dto.response.RedemptionReservationResponse;
import com.gamingcastle.loyaltyservice.dto.response.ReverseLoyaltyResponse;
import com.gamingcastle.loyaltyservice.dto.response.TransactionHistoryResponse;
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
}
