package com.gamingcastle.loyaltyservice.controller;

import com.gamingcastle.loyaltyservice.dto.request.AdjustPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.UpdateLoyaltyRulesRequest;
import com.gamingcastle.loyaltyservice.dto.response.*;
import com.gamingcastle.loyaltyservice.service.LoyaltyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyAdminControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private LoyaltyService loyaltyService;

    private LoyaltyAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new LoyaltyAdminController(loyaltyService);
    }

    @Test
    @DisplayName("Given rules requested, when getRules, then returns 200 OK and rules response")
    void getRules_shouldReturnOkAndRules() {
        // Arrange
        LoyaltyRulesResponse expected = new LoyaltyRulesResponse(
                UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("0.50"),
                100L, 10L, 50L, 15L, 1000L, new BigDecimal("1.25"), 3000L,
                new BigDecimal("1.50"), "admin");
        when(loyaltyService.getRules()).thenReturn(expected);

        // Act
        ResponseEntity<LoyaltyRulesResponse> response = controller.getRules();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loyaltyService).getRules();
    }

    @Test
    @DisplayName("Given valid update request, when updateRules, then returns 200 OK and updated rules")
    void updateRules_shouldReturnOkAndUpdatedRules() {
        // Arrange
        UpdateLoyaltyRulesRequest request = new UpdateLoyaltyRulesRequest(
                new BigDecimal("120.00"), new BigDecimal("0.60"), 150L, 15L, 40L, 20L,
                1500L, new BigDecimal("1.30"), 4000L, new BigDecimal("1.60"));
        LoyaltyRulesResponse expected = new LoyaltyRulesResponse(
                UUID.randomUUID(), request.amountPerPoint(), request.pointValueLkr(),
                request.minRedeemPoints(), request.redeemStep(), request.maxDiscountPercent(),
                request.reservationTimeoutMinutes(), request.silverThreshold(), request.silverMultiplier(),
                request.goldThreshold(), request.goldMultiplier(), ADMIN_ID.toString());
        when(loyaltyService.updateRules(request, ADMIN_ID)).thenReturn(expected);

        // Act
        ResponseEntity<LoyaltyRulesResponse> response = controller.updateRules(ADMIN_ID, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loyaltyService).updateRules(request, ADMIN_ID);
    }

    @Test
    @DisplayName("Given valid adjust request, when adjustPoints, then returns 200 OK and adjustment response")
    void adjustPoints_shouldReturnOkAndAdjustmentResponse() {
        // Arrange
        AdjustPointsRequest request = new AdjustPointsRequest(USER_ID, "Manual bonus", 100L);
        AdminAdjustmentResponse expected = new AdminAdjustmentResponse(USER_ID, 100L, 350L, "Manual bonus");
        when(loyaltyService.adjustPoints(request)).thenReturn(expected);

        // Act
        ResponseEntity<AdminAdjustmentResponse> response = controller.adjustPoints(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loyaltyService).adjustPoints(request);
    }

    @Test
    @DisplayName("Given stats requested, when getLoyaltyStats, then returns 200 OK and admin stats")
    void getLoyaltyStats_shouldReturnOkAndStats() {
        // Arrange
        AdminLoyaltyStatsResponse expected = new AdminLoyaltyStatsResponse(
                10L, 2500L, 5000L, 2500L, Map.of("BRONZE", 8L, "SILVER", 2L, "GOLD", 0L));
        when(loyaltyService.getLoyaltyStats()).thenReturn(expected);

        // Act
        ResponseEntity<AdminLoyaltyStatsResponse> response = controller.getLoyaltyStats();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loyaltyService).getLoyaltyStats();
    }

    @Test
    @DisplayName("Given accounts requested, when getAllAccounts, then returns 200 OK and paged accounts")
    void getAllAccounts_shouldReturnOkAndPagedAccounts() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        LoyaltyAccountResponse account = new LoyaltyAccountResponse(
                USER_ID, 100L, 250L, "BRONZE", new BigDecimal("0.50"), new BigDecimal("50.00"));
        Page<LoyaltyAccountResponse> expected = new PageImpl<>(List.of(account));
        when(loyaltyService.getAllAccounts(pageable)).thenReturn(expected);

        // Act
        ResponseEntity<Page<LoyaltyAccountResponse>> response = controller.getAllAccounts(pageable);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loyaltyService).getAllAccounts(pageable);
    }

    @Test
    @DisplayName("Given transactions requested, when getAllTransactions, then returns 200 OK and paged transactions")
    void getAllTransactions_shouldReturnOkAndPagedTransactions() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        TransactionHistoryResponse tx = new TransactionHistoryResponse(
                UUID.randomUUID(), USER_ID, UUID.randomUUID(), "EARNED", "CONFIRMED",
                50L, BigDecimal.ZERO, "Earned points", 50L, Instant.now());
        Page<TransactionHistoryResponse> expected = new PageImpl<>(List.of(tx));
        when(loyaltyService.getAllTransactions(pageable)).thenReturn(expected);

        // Act
        ResponseEntity<Page<TransactionHistoryResponse>> response = controller.getAllTransactions(pageable);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loyaltyService).getAllTransactions(pageable);
    }
}
