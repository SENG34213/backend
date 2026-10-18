package com.gamingcastle.loyaltyservice.controller;

import com.gamingcastle.loyaltyservice.dto.request.CalculateRedemptionRequest;
import com.gamingcastle.loyaltyservice.dto.response.CalculationResponse;
import com.gamingcastle.loyaltyservice.dto.response.ErrorResponse;
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyAccountResponse;
import com.gamingcastle.loyaltyservice.entity.LoyaltyAccount;
import com.gamingcastle.loyaltyservice.entity.LoyaltyRules;
import com.gamingcastle.loyaltyservice.entity.LoyaltyTier;
import com.gamingcastle.loyaltyservice.entity.LoyaltyTransaction;
import com.gamingcastle.loyaltyservice.entity.LoyaltyTransactionStatus;
import com.gamingcastle.loyaltyservice.entity.LoyaltyTransactionType;
import com.gamingcastle.loyaltyservice.repository.LoyaltyAccountRepository;
import com.gamingcastle.loyaltyservice.repository.LoyaltyRulesRepository;
import com.gamingcastle.loyaltyservice.repository.LoyaltyTransactionRepository;
import com.gamingcastle.loyaltyservice.security.GatewayAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoyaltyServiceCustomerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LoyaltyAccountRepository accountRepository;

    @Autowired
    private LoyaltyTransactionRepository transactionRepository;

    @Autowired
    private LoyaltyRulesRepository rulesRepository;

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        rulesRepository.deleteAll();
        ensureDefaultRulesExist();
    }

    private void ensureDefaultRulesExist() {
        if (rulesRepository.count() == 0) {
            rulesRepository.save(LoyaltyRules.builder()
                    .amountPerPoint(new BigDecimal("100.00"))
                    .pointValueLkr(new BigDecimal("0.50"))
                    .minRedeemPoints(100L)
                    .redeemStep(10L)
                    .maxDiscountPercent(50L)
                    .reservationTimeoutMinutes(15L)
                    .silverThreshold(1000L)
                    .silverMultiplier(new BigDecimal("1.2500"))
                    .goldThreshold(3000L)
                    .goldMultiplier(new BigDecimal("1.5000"))
                    .updatedBy("system")
                    .build());
        }
    }

    private HttpHeaders authorizedHeaders(UUID userId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, gatewaySecret);
        headers.set(GatewayAuthenticationFilter.USER_ID_HEADER, userId.toString());
        headers.set(GatewayAuthenticationFilter.USER_ROLE_HEADER, role);
        return headers;
    }

    // =========================================================================
    // 1. GET /api/loyalty/balance/{userId}
    // =========================================================================

    @Test
    @DisplayName("GET /api/loyalty/balance/{userId} - customer accessing own balance returns 200 OK")
    void getBalance_asCustomerOwnAccount_shouldReturn200AndBalance() {
        UUID userId = UUID.randomUUID();
        accountRepository.save(LoyaltyAccount.builder()
                .userId(userId)
                .pointsBalance(300L)
                .lifetimeEarned(600L)
                .tier(LoyaltyTier.BRONZE)
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(userId, "CUSTOMER"));

        ResponseEntity<LoyaltyAccountResponse> response = restTemplate.exchange(
                "/api/loyalty/balance/" + userId, HttpMethod.GET, entity, LoyaltyAccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo(userId);
        assertThat(response.getBody().pointsBalance()).isEqualTo(300L);
        assertThat(response.getBody().lifetimeEarned()).isEqualTo(600L);
        assertThat(response.getBody().tier()).isEqualTo("BRONZE");
        assertThat(response.getBody().pointValue()).isEqualByComparingTo("0.50");
        assertThat(response.getBody().equivalentDiscount()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("GET /api/loyalty/balance/{userId} - user with no prior account gets default 0 balance")
    void getBalance_whenNoAccountExists_shouldReturnZeroBalance() {
        UUID userId = UUID.randomUUID();
        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(userId, "CUSTOMER"));

        ResponseEntity<LoyaltyAccountResponse> response = restTemplate.exchange(
                "/api/loyalty/balance/" + userId, HttpMethod.GET, entity, LoyaltyAccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().pointsBalance()).isEqualTo(0L);
        assertThat(response.getBody().tier()).isEqualTo("BRONZE");
    }

    @Test
    @DisplayName("GET /api/loyalty/balance/{userId} - customer accessing another user returns 403 FORBIDDEN")
    void getBalance_asCustomerOtherUser_shouldReturn403() {
        UUID callerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(callerId, "CUSTOMER"));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/loyalty/balance/" + otherUserId, HttpMethod.GET, entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GET /api/loyalty/balance/{userId} - admin accessing customer balance returns 200 OK")
    void getBalance_asAdminAccessingCustomer_shouldReturn200() {
        UUID adminId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        accountRepository.save(LoyaltyAccount.builder()
                .userId(customerId)
                .pointsBalance(150L)
                .lifetimeEarned(150L)
                .tier(LoyaltyTier.BRONZE)
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(adminId, "ADMIN"));

        ResponseEntity<LoyaltyAccountResponse> response = restTemplate.exchange(
                "/api/loyalty/balance/" + customerId, HttpMethod.GET, entity, LoyaltyAccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().pointsBalance()).isEqualTo(150L);
    }

    // =========================================================================
    // 2. GET /api/loyalty/history/{userId}
    // =========================================================================

    @Test
    @DisplayName("GET /api/loyalty/history/{userId} - returns paginated history of transactions")
    void getHistory_asCustomerOwnAccount_shouldReturn200AndHistory() {
        UUID userId = UUID.randomUUID();
        accountRepository.save(LoyaltyAccount.builder()
                .userId(userId)
                .pointsBalance(100L)
                .lifetimeEarned(100L)
                .tier(LoyaltyTier.BRONZE)
                .build());

        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(userId)
                .bookingId(UUID.randomUUID())
                .type(LoyaltyTransactionType.EARNED)
                .status(LoyaltyTransactionStatus.CONFIRMED)
                .points(100L)
                .balanceAfter(100L)
                .description("Points earned")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(userId, "CUSTOMER"));

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/loyalty/history/" + userId + "?page=0&size=10",
                HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    @DisplayName("GET /api/loyalty/history/{userId} - customer accessing other user history returns 403")
    void getHistory_asCustomerOtherUser_shouldReturn403() {
        UUID callerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(callerId, "CUSTOMER"));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/loyalty/history/" + otherUserId, HttpMethod.GET, entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 3. POST /api/loyalty/calculate-redemption
    // =========================================================================

    @Test
    @DisplayName("POST /api/loyalty/calculate-redemption - returns calculated discount and payable amount")
    void calculateRedemption_withValidPoints_shouldReturnCalculation() {
        UUID userId = UUID.randomUUID();
        accountRepository.save(LoyaltyAccount.builder()
                .userId(userId)
                .pointsBalance(400L)
                .lifetimeEarned(400L)
                .tier(LoyaltyTier.BRONZE)
                .build());

        CalculateRedemptionRequest request = new CalculateRedemptionRequest(
                userId, new BigDecimal("2000.00"), 200L);
        HttpEntity<CalculateRedemptionRequest> entity = new HttpEntity<>(request, authorizedHeaders(userId, "CUSTOMER"));

        ResponseEntity<CalculationResponse> response = restTemplate.postForEntity(
                "/api/loyalty/calculate-redemption", entity, CalculationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().valid()).isTrue();
        assertThat(response.getBody().discountAmount()).isEqualByComparingTo("100.00"); // 200 * 0.50 LKR
        assertThat(response.getBody().payableAmount()).isEqualByComparingTo("1900.00");
    }

    @Test
    @DisplayName("POST /api/loyalty/calculate-redemption - returns valid=false when points are below minimum")
    void calculateRedemption_belowMinimum_shouldReturnEligibleFalse() {
        UUID userId = UUID.randomUUID();
        accountRepository.save(LoyaltyAccount.builder()
                .userId(userId)
                .pointsBalance(400L)
                .lifetimeEarned(400L)
                .tier(LoyaltyTier.BRONZE)
                .build());

        CalculateRedemptionRequest request = new CalculateRedemptionRequest(
                userId, new BigDecimal("2000.00"), 50L); // min is 100
        HttpEntity<CalculateRedemptionRequest> entity = new HttpEntity<>(request, authorizedHeaders(userId, "CUSTOMER"));

        ResponseEntity<CalculationResponse> response = restTemplate.postForEntity(
                "/api/loyalty/calculate-redemption", entity, CalculationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().valid()).isFalse();
        assertThat(response.getBody().reason()).contains("Minimum redemption is 100 points");
    }

    @Test
    @DisplayName("POST /api/loyalty/calculate-redemption - returns 403 when customer previews for another user")
    void calculateRedemption_asCustomerForAnotherUser_shouldReturn403() {
        UUID callerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        CalculateRedemptionRequest request = new CalculateRedemptionRequest(
                otherUserId, new BigDecimal("2000.00"), 100L);
        HttpEntity<CalculateRedemptionRequest> entity = new HttpEntity<>(request, authorizedHeaders(callerId, "CUSTOMER"));

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/loyalty/calculate-redemption", entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
