package com.gamingcastle.loyaltyservice.controller;

import com.gamingcastle.loyaltyservice.api.APIEndPoints;
import com.gamingcastle.loyaltyservice.dto.request.AwardPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.ConfirmRedemptionRequest;
import com.gamingcastle.loyaltyservice.dto.request.RedeemPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.ReleaseRedemptionRequest;
import com.gamingcastle.loyaltyservice.dto.request.ReverseLoyaltyRequest;
import com.gamingcastle.loyaltyservice.dto.response.AwardPointsResponse;
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyStatusResponse;
import com.gamingcastle.loyaltyservice.dto.response.RedemptionReservationResponse;
import com.gamingcastle.loyaltyservice.dto.response.ReverseLoyaltyResponse;
import com.gamingcastle.loyaltyservice.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(APIEndPoints.baseAPI)
@Tag(name = "Loyalty Internal", description = "Service-to-service endpoints for booking and payment integrations")
public class LoyaltyInternalController {

    private final LoyaltyService loyaltyService;

    public LoyaltyInternalController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @Operation(summary = "Award points after a successful payment")
    @PostMapping(APIEndPoints.award)
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<AwardPointsResponse> award(@Valid @RequestBody AwardPointsRequest request) {
        log.info("Award requested: userId={}, bookingId={}", request.userId(), request.bookingId());
        return ResponseEntity.ok(loyaltyService.awardPoints(request));
    }

    @Operation(summary = "Reserve points against a booking")
    @PostMapping(APIEndPoints.redeemReserve)
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<RedemptionReservationResponse> reserve(@Valid @RequestBody RedeemPointsRequest request) {
        log.info("Reserve requested: userId={}, bookingId={}, points={}",
                request.userId(), request.bookingId(), request.points());
        return ResponseEntity.ok(loyaltyService.reserveRedemption(request));
    }

    @Operation(summary = "Confirm a reservation after payment succeeds")
    @PostMapping(APIEndPoints.redeemConfirm)
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<LoyaltyStatusResponse> confirm(@Valid @RequestBody ConfirmRedemptionRequest request) {
        log.info("Confirm requested: bookingId={}", request.bookingId());
        return ResponseEntity.ok(loyaltyService.confirmRedemption(request.bookingId()));
    }

    @Operation(summary = "Release a pending reservation after a failed or cancelled payment")
    @PostMapping(APIEndPoints.redeemRelease)
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<LoyaltyStatusResponse> release(@Valid @RequestBody ReleaseRedemptionRequest request) {
        log.info("Release requested: bookingId={}", request.bookingId());
        return ResponseEntity.ok(loyaltyService.releaseRedemption(request.bookingId(), request.reason()));
    }

    @Operation(summary = "Reverse earned points and restore redeemed points after a booking cancellation")
    @PostMapping(APIEndPoints.reverse)
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<ReverseLoyaltyResponse> reverse(@Valid @RequestBody ReverseLoyaltyRequest request) {
        log.info("Reverse requested: bookingId={}", request.bookingId());
        return ResponseEntity.ok(loyaltyService.reverseBooking(request.bookingId(), request.reason()));
    }
}