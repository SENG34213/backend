package com.gamingcastle.loyaltyservice.controller;

import com.gamingcastle.loyaltyservice.api.APIEndPoints;
import com.gamingcastle.loyaltyservice.dto.request.CalculateRedemptionRequest;
import com.gamingcastle.loyaltyservice.dto.response.CalculationResponse;
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyAccountResponse;
import com.gamingcastle.loyaltyservice.dto.response.TransactionHistoryResponse;
import com.gamingcastle.loyaltyservice.exception.ForbiddenOperationException;
import com.gamingcastle.loyaltyservice.security.GatewayUserPrincipal;
import com.gamingcastle.loyaltyservice.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(APIEndPoints.baseAPI)
@Tag(name = "Loyalty", description = "Customer loyalty status and redemption calculations")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    public LoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @Operation(summary = "Get a user's loyalty balance")
    @GetMapping(APIEndPoints.balance)
    public ResponseEntity<LoyaltyAccountResponse> getBalance(
            @PathVariable UUID userId,
            @AuthenticationPrincipal GatewayUserPrincipal caller) {
        assertCanAccess(userId, caller);
        return ResponseEntity.ok(loyaltyService.getBalance(userId));
    }

    @Operation(summary = "Get a page of a user's transaction history (newest first)")
    @GetMapping(APIEndPoints.history)
    public ResponseEntity<Page<TransactionHistoryResponse>> getHistory(
            @PathVariable UUID userId,
            @AuthenticationPrincipal GatewayUserPrincipal caller,
            @PageableDefault(size = 20) Pageable pageable) {
        assertCanAccess(userId, caller);
        return ResponseEntity.ok(loyaltyService.getHistory(userId, pageable));
    }

    @Operation(summary = "Preview a redemption discount (changes nothing)")
    @PostMapping(APIEndPoints.calculateRedemption)
    public ResponseEntity<CalculationResponse> calculateRedemption(
            @AuthenticationPrincipal GatewayUserPrincipal caller,
            @Valid @RequestBody CalculateRedemptionRequest request) {
        assertCanAccess(request.userId(), caller);
        return ResponseEntity.ok(loyaltyService.calculateRedemption(request));
    }

    private static void assertCanAccess(UUID userId, GatewayUserPrincipal caller) {
        boolean allowed = caller != null && (caller.isAdmin() || userId.equals(caller.userId()));
        if (!allowed) {
            throw new ForbiddenOperationException("You may only access your own loyalty data");
        }
    }
}