package com.gamingcastle.loyaltyservice.controller;

import com.gamingcastle.loyaltyservice.dto.request.CalculateRedemptionRequest;
import com.gamingcastle.loyaltyservice.dto.response.CalculationResponse;
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyAccountResponse;
import com.gamingcastle.loyaltyservice.dto.response.TransactionHistoryResponse;
import com.gamingcastle.loyaltyservice.exception.ForbiddenOperationException;
import com.gamingcastle.loyaltyservice.security.GatewayUserPrincipal;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private LoyaltyService loyaltyService;

    private LoyaltyController controller;

    @BeforeEach
    void setUp() {
        controller = new LoyaltyController(loyaltyService);
    }

    // =========================================================================
    // getBalance Tests
    // =========================================================================

    @Test
    @DisplayName("Given customer accesses own balance, when getBalance, then returns 200 OK and account response")
    void getBalance_givenCustomerAccessesOwnBalance_shouldReturnOk() {
        // Arrange
        GatewayUserPrincipal caller = new GatewayUserPrincipal(USER_ID, "CUSTOMER");
        LoyaltyAccountResponse expected = new LoyaltyAccountResponse(
                USER_ID, 200L, 500L, "BRONZE", new BigDecimal("0.50"), new BigDecimal("100.00"));
        when(loyaltyService.getBalance(USER_ID)).thenReturn(expected);

        // Act
        ResponseEntity<LoyaltyAccountResponse> response = controller.getBalance(USER_ID, caller);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loyaltyService).getBalance(USER_ID);
    }

    @Test
    @DisplayName("Given admin accesses any customer balance, when getBalance, then returns 200 OK")
    void getBalance_givenAdminAccessesCustomerBalance_shouldReturnOk() {
        // Arrange
        GatewayUserPrincipal adminCaller = new GatewayUserPrincipal(USER_ID, "ADMIN");
        LoyaltyAccountResponse expected = new LoyaltyAccountResponse(
                OTHER_USER_ID, 150L, 300L, "BRONZE", new BigDecimal("0.50"), new BigDecimal("75.00"));
        when(loyaltyService.getBalance(OTHER_USER_ID)).thenReturn(expected);

        // Act
        ResponseEntity<LoyaltyAccountResponse> response = controller.getBalance(OTHER_USER_ID, adminCaller);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loyaltyService).getBalance(OTHER_USER_ID);
    }

    @Test
    @DisplayName("Given customer accesses another user balance, when getBalance, then throws ForbiddenOperationException")
    void getBalance_givenCustomerAccessesOtherUserBalance_shouldThrowForbidden() {
        // Arrange
        GatewayUserPrincipal caller = new GatewayUserPrincipal(USER_ID, "CUSTOMER");

        // Act & Assert
        assertThatThrownBy(() -> controller.getBalance(OTHER_USER_ID, caller))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("You may only access your own loyalty data");
    }

    @Test
    @DisplayName("Given caller is null, when getBalance, then throws ForbiddenOperationException")
    void getBalance_givenNullCaller_shouldThrowForbidden() {
        // Act & Assert
        assertThatThrownBy(() -> controller.getBalance(USER_ID, null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("You may only access your own loyalty data");
    }

    // =========================================================================
    // getHistory Tests
    // =========================================================================

    @Test
    @DisplayName("Given customer accesses own history, when getHistory, then returns 200 OK and paged history")
    void getHistory_givenCustomerAccessesOwnHistory_shouldReturnOk() {
        // Arrange
        GatewayUserPrincipal caller = new GatewayUserPrincipal(USER_ID, "CUSTOMER");
        Pageable pageable = PageRequest.of(0, 10);
        TransactionHistoryResponse tx = new TransactionHistoryResponse(
                UUID.randomUUID(), USER_ID, UUID.randomUUID(), "EARNED", "CONFIRMED",
                50L, BigDecimal.ZERO, "Earned points", 50L, Instant.now());
        Page<TransactionHistoryResponse> page = new PageImpl<>(List.of(tx));
        when(loyaltyService.getHistory(USER_ID, pageable)).thenReturn(page);

        // Act
        ResponseEntity<Page<TransactionHistoryResponse>> response = controller.getHistory(USER_ID, caller, pageable);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(tx);
        verify(loyaltyService).getHistory(USER_ID, pageable);
    }

    @Test
    @DisplayName("Given admin accesses customer history, when getHistory, then returns 200 OK")
    void getHistory_givenAdminAccessesCustomerHistory_shouldReturnOk() {
        // Arrange
        GatewayUserPrincipal adminCaller = new GatewayUserPrincipal(USER_ID, "ADMIN");
        Pageable pageable = PageRequest.of(0, 10);
        Page<TransactionHistoryResponse> page = new PageImpl<>(List.of());
        when(loyaltyService.getHistory(OTHER_USER_ID, pageable)).thenReturn(page);

        // Act
        ResponseEntity<Page<TransactionHistoryResponse>> response = controller.getHistory(OTHER_USER_ID, adminCaller, pageable);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(loyaltyService).getHistory(OTHER_USER_ID, pageable);
    }

    @Test
    @DisplayName("Given customer accesses another user history, when getHistory, then throws ForbiddenOperationException")
    void getHistory_givenCustomerAccessesOtherUserHistory_shouldThrowForbidden() {
        // Arrange
        GatewayUserPrincipal caller = new GatewayUserPrincipal(USER_ID, "CUSTOMER");
        Pageable pageable = PageRequest.of(0, 10);

        // Act & Assert
        assertThatThrownBy(() -> controller.getHistory(OTHER_USER_ID, caller, pageable))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("You may only access your own loyalty data");
    }

    // =========================================================================
    // calculateRedemption Tests
    // =========================================================================

    @Test
    @DisplayName("Given customer calculates own redemption, when calculateRedemption, then returns 200 OK")
    void calculateRedemption_givenCustomerCalculatesOwnRedemption_shouldReturnOk() {
        // Arrange
        GatewayUserPrincipal caller = new GatewayUserPrincipal(USER_ID, "CUSTOMER");
        CalculateRedemptionRequest request = new CalculateRedemptionRequest(
                USER_ID, new BigDecimal("1000.00"), 200L);
        CalculationResponse expected = new CalculationResponse(
                USER_ID, new BigDecimal("1000.00"), 200L, new BigDecimal("100.00"),
                new BigDecimal("900.00"), true, null);
        when(loyaltyService.calculateRedemption(request)).thenReturn(expected);

        // Act
        ResponseEntity<CalculationResponse> response = controller.calculateRedemption(caller, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loyaltyService).calculateRedemption(request);
    }

    @Test
    @DisplayName("Given admin calculates redemption for customer, when calculateRedemption, then returns 200 OK")
    void calculateRedemption_givenAdminCalculatesCustomerRedemption_shouldReturnOk() {
        // Arrange
        GatewayUserPrincipal adminCaller = new GatewayUserPrincipal(USER_ID, "ADMIN");
        CalculateRedemptionRequest request = new CalculateRedemptionRequest(
                OTHER_USER_ID, new BigDecimal("500.00"), 100L);
        CalculationResponse expected = new CalculationResponse(
                OTHER_USER_ID, new BigDecimal("500.00"), 100L, new BigDecimal("50.00"),
                new BigDecimal("450.00"), true, null);
        when(loyaltyService.calculateRedemption(request)).thenReturn(expected);

        // Act
        ResponseEntity<CalculationResponse> response = controller.calculateRedemption(adminCaller, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(loyaltyService).calculateRedemption(request);
    }

    @Test
    @DisplayName("Given customer calculates redemption for another user, when calculateRedemption, then throws ForbiddenOperationException")
    void calculateRedemption_givenCustomerCalculatesOtherUserRedemption_shouldThrowForbidden() {
        // Arrange
        GatewayUserPrincipal caller = new GatewayUserPrincipal(USER_ID, "CUSTOMER");
        CalculateRedemptionRequest request = new CalculateRedemptionRequest(
                OTHER_USER_ID, new BigDecimal("1000.00"), 200L);

        // Act & Assert
        assertThatThrownBy(() -> controller.calculateRedemption(caller, request))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("You may only access your own loyalty data");
    }
}
