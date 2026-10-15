package com.gamingcastle.loyaltyservice.controller;

import com.gamingcastle.loyaltyservice.api.APIEndPoints;
import com.gamingcastle.loyaltyservice.dto.request.AdjustPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.UpdateLoyaltyRulesRequest;
import com.gamingcastle.loyaltyservice.dto.response.*;
import com.gamingcastle.loyaltyservice.security.GatewayAuthenticationFilter;
import com.gamingcastle.loyaltyservice.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(APIEndPoints.baseAPI)
@Tag(name = "Loyalty Admin", description = "Administrative rule and adjustment endpoints")
public class LoyaltyAdminController {

    private final LoyaltyService loyaltyService;

    public LoyaltyAdminController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @Operation(summary = "Get current loyalty rules")
    @GetMapping(APIEndPoints.adminRules)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoyaltyRulesResponse> getRules() {
        return ResponseEntity.ok(loyaltyService.getRules());
    }

    @Operation(summary = "Update loyalty rules")
    @PutMapping(APIEndPoints.adminRules)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoyaltyRulesResponse> updateRules(
            @RequestHeader(GatewayAuthenticationFilter.USER_ID_HEADER) UUID adminId,
            @Valid @RequestBody UpdateLoyaltyRulesRequest request) {
        return ResponseEntity.ok(loyaltyService.updateRules(request, adminId));
    }

    @Operation(summary = "Adjust a user's point balance")
    @PostMapping(APIEndPoints.adminAdjust)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminAdjustmentResponse> adjustPoints(
            @Valid @RequestBody AdjustPointsRequest request) {
        return ResponseEntity.ok(loyaltyService.adjustPoints(request));
    }

    @Operation(summary = "Get loyalty program stats")
    @GetMapping(APIEndPoints.adminStats)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminLoyaltyStatsResponse> getLoyaltyStats() {
        return ResponseEntity.ok(loyaltyService.getLoyaltyStats());
    }

    @Operation(summary = "List all loyalty accounts with pagination")
    @GetMapping(APIEndPoints.adminAccounts)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<LoyaltyAccountResponse>> getAllAccounts(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(loyaltyService.getAllAccounts(pageable));
    }

    @Operation(summary = "List all loyalty transactions with pagination")
    @GetMapping(APIEndPoints.adminTransactions)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<TransactionHistoryResponse>> getAllTransactions(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(loyaltyService.getAllTransactions(pageable));
    }
}
