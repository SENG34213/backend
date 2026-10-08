package com.gamingcastle.loyaltyservice.controller;

import com.gamingcastle.loyaltyservice.api.APIEndPoints;
import com.gamingcastle.loyaltyservice.dto.request.AdjustPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.UpdateLoyaltyRulesRequest;
import com.gamingcastle.loyaltyservice.dto.response.AdminAdjustmentResponse;
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyRulesResponse;
import com.gamingcastle.loyaltyservice.security.GatewayAuthenticationFilter;
import com.gamingcastle.loyaltyservice.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
}
