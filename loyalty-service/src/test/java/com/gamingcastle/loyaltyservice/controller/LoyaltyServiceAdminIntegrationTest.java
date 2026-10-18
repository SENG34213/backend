package com.gamingcastle.loyaltyservice.controller;

import com.gamingcastle.loyaltyservice.api.APIEndPoints;
import com.gamingcastle.loyaltyservice.dto.request.AdjustPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.UpdateLoyaltyRulesRequest;
import com.gamingcastle.loyaltyservice.dto.response.*;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoyaltyServiceAdminIntegrationTest {

    private static final String ADMIN_RULES_URL = "/api/" + APIEndPoints.adminRules;
    private static final String ADMIN_ADJUST_URL = "/api/" + APIEndPoints.adminAdjust;
    private static final String ADMIN_STATS_URL = "/api/" + APIEndPoints.adminStats;
    private static final String ADMIN_ACCOUNTS_URL = "/api/" + APIEndPoints.adminAccounts;
    private static final String ADMIN_TRANSACTIONS_URL = "/api/" + APIEndPoints.adminTransactions;

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
    // 1. GET /api/loyalty/admin/rules
    // =========================================================================

    @Test
    @DisplayName("GET /api/loyalty/admin/rules - admin caller returns 200 OK and current rules")
    void getRules_asAdmin_shouldReturn200AndRules() {
        UUID adminId = UUID.randomUUID();
        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(adminId, "ADMIN"));

        ResponseEntity<LoyaltyRulesResponse> response = restTemplate.exchange(
                ADMIN_RULES_URL, HttpMethod.GET, entity, LoyaltyRulesResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().amountPerPoint()).isEqualByComparingTo("100.00");
        assertThat(response.getBody().pointValueLkr()).isEqualByComparingTo("0.50");
        assertThat(response.getBody().minRedeemPoints()).isEqualTo(100L);
    }

    @Test
    @DisplayName("GET /api/loyalty/admin/rules - customer caller returns 403 FORBIDDEN")
    void getRules_asCustomer_shouldReturn403() {
        UUID customerId = UUID.randomUUID();
        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(customerId, "CUSTOMER"));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                ADMIN_RULES_URL, HttpMethod.GET, entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 2. PUT /api/loyalty/admin/rules
    // =========================================================================

    @Test
    @DisplayName("PUT /api/loyalty/admin/rules - admin updates rules and persists new values")
    void updateRules_asAdmin_shouldUpdateRulesInDatabase() {
        UUID adminId = UUID.randomUUID();
        UpdateLoyaltyRulesRequest request = new UpdateLoyaltyRulesRequest(
                new BigDecimal("150.00"), new BigDecimal("0.75"), 200L, 20L, 60L, 30L,
                1500L, new BigDecimal("1.3000"), 4000L, new BigDecimal("1.6000"));

        HttpEntity<UpdateLoyaltyRulesRequest> entity = new HttpEntity<>(request, authorizedHeaders(adminId, "ADMIN"));

        ResponseEntity<LoyaltyRulesResponse> response = restTemplate.exchange(
                ADMIN_RULES_URL, HttpMethod.PUT, entity, LoyaltyRulesResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().amountPerPoint()).isEqualByComparingTo("150.00");
        assertThat(response.getBody().pointValueLkr()).isEqualByComparingTo("0.75");
        assertThat(response.getBody().minRedeemPoints()).isEqualTo(200L);
        assertThat(response.getBody().updatedBy()).isEqualTo(adminId.toString());

        // Verify in DB
        LoyaltyRules rulesInDb = rulesRepository.findTopByOrderByUpdatedAtDesc().orElseThrow();
        assertThat(rulesInDb.getAmountPerPoint()).isEqualByComparingTo("150.00");
        assertThat(rulesInDb.getUpdatedBy()).isEqualTo(adminId.toString());
    }

    @Test
    @DisplayName("PUT /api/loyalty/admin/rules - customer caller returns 403 FORBIDDEN")
    void updateRules_asCustomer_shouldReturn403() {
        UUID customerId = UUID.randomUUID();
        UpdateLoyaltyRulesRequest request = new UpdateLoyaltyRulesRequest(
                new BigDecimal("150.00"), new BigDecimal("0.75"), 200L, 20L, 60L, 30L,
                1500L, new BigDecimal("1.3000"), 4000L, new BigDecimal("1.6000"));

        HttpEntity<UpdateLoyaltyRulesRequest> entity = new HttpEntity<>(request, authorizedHeaders(customerId, "CUSTOMER"));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                ADMIN_RULES_URL, HttpMethod.PUT, entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 3. POST /api/loyalty/admin/adjust
    // =========================================================================

    @Test
    @DisplayName("POST /api/loyalty/admin/adjust - admin adjusts points positively, balance updates, ledger saved")
    void adjustPoints_asAdminPositive_shouldUpdateBalanceAndSaveLedger() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        accountRepository.save(LoyaltyAccount.builder()
                .userId(userId)
                .pointsBalance(100L)
                .lifetimeEarned(100L)
                .tier(LoyaltyTier.BRONZE)
                .build());

        AdjustPointsRequest request = new AdjustPointsRequest(userId, "Promotional bonus", 50L);
        HttpEntity<AdjustPointsRequest> entity = new HttpEntity<>(request, authorizedHeaders(adminId, "ADMIN"));

        ResponseEntity<AdminAdjustmentResponse> response = restTemplate.postForEntity(
                ADMIN_ADJUST_URL, entity, AdminAdjustmentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().pointsAdjusted()).isEqualTo(50L);
        assertThat(response.getBody().newBalance()).isEqualTo(150L);

        // Verify account in DB
        LoyaltyAccount account = accountRepository.findByUserId(userId).orElseThrow();
        assertThat(account.getPointsBalance()).isEqualTo(150L);

        // Verify ledger
        List<LoyaltyTransaction> txs = transactionRepository.findAll();
        assertThat(txs).hasSize(1);
        assertThat(txs.get(0).getType()).isEqualTo(LoyaltyTransactionType.ADJUSTED);
        assertThat(txs.get(0).getStatus()).isEqualTo(LoyaltyTransactionStatus.CONFIRMED);
        assertThat(txs.get(0).getPoints()).isEqualTo(50L);
    }

    @Test
    @DisplayName("POST /api/loyalty/admin/adjust - adjustment that makes balance negative returns 422")
    void adjustPoints_negativeBalance_shouldReturn422() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        accountRepository.save(LoyaltyAccount.builder()
                .userId(userId)
                .pointsBalance(30L)
                .lifetimeEarned(30L)
                .tier(LoyaltyTier.BRONZE)
                .build());

        AdjustPointsRequest request = new AdjustPointsRequest(userId, "Penalty", -50L);
        HttpEntity<AdjustPointsRequest> entity = new HttpEntity<>(request, authorizedHeaders(adminId, "ADMIN"));

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                ADMIN_ADJUST_URL, entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INSUFFICIENT_POINTS");
    }

    // =========================================================================
    // 4. GET /api/loyalty/admin/stats
    // =========================================================================

    @Test
    @DisplayName("GET /api/loyalty/admin/stats - returns aggregated loyalty stats")
    void getLoyaltyStats_asAdmin_shouldReturnAggregatedStats() {
        UUID adminId = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        accountRepository.save(LoyaltyAccount.builder()
                .userId(u1).pointsBalance(200L).lifetimeEarned(500L).tier(LoyaltyTier.BRONZE).build());
        accountRepository.save(LoyaltyAccount.builder()
                .userId(u2).pointsBalance(800L).lifetimeEarned(1200L).tier(LoyaltyTier.SILVER).build());

        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(u1).bookingId(UUID.randomUUID()).type(LoyaltyTransactionType.REDEEMED)
                .status(LoyaltyTransactionStatus.CONFIRMED).points(100L).build());

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(adminId, "ADMIN"));

        ResponseEntity<AdminLoyaltyStatsResponse> response = restTemplate.exchange(
                ADMIN_STATS_URL, HttpMethod.GET, entity, AdminLoyaltyStatsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalAccounts()).isEqualTo(2L);
        assertThat(response.getBody().totalPointsInCirculation()).isEqualTo(1000L);
        assertThat(response.getBody().totalPointsAwardedAllTime()).isEqualTo(1700L);
        assertThat(response.getBody().totalPointsRedeemedAllTime()).isEqualTo(100L);
        assertThat(response.getBody().accountsByTier()).containsEntry("BRONZE", 1L);
        assertThat(response.getBody().accountsByTier()).containsEntry("SILVER", 1L);
    }

    // =========================================================================
    // 5. GET /api/loyalty/admin/accounts & /transactions
    // =========================================================================

    @Test
    @DisplayName("GET /api/loyalty/admin/accounts - admin lists all accounts with pagination")
    void getAllAccounts_asAdmin_shouldReturnPagedAccounts() {
        UUID adminId = UUID.randomUUID();
        accountRepository.save(LoyaltyAccount.builder()
                .userId(UUID.randomUUID()).pointsBalance(100L).lifetimeEarned(100L).tier(LoyaltyTier.BRONZE).build());

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(adminId, "ADMIN"));

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                ADMIN_ACCOUNTS_URL + "?page=0&size=10", HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    @DisplayName("GET /api/loyalty/admin/transactions - admin lists all transactions with pagination")
    void getAllTransactions_asAdmin_shouldReturnPagedTransactions() {
        UUID adminId = UUID.randomUUID();
        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(UUID.randomUUID()).type(LoyaltyTransactionType.ADJUSTED)
                .status(LoyaltyTransactionStatus.CONFIRMED).points(50L).build());

        HttpEntity<Void> entity = new HttpEntity<>(authorizedHeaders(adminId, "ADMIN"));

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                ADMIN_TRANSACTIONS_URL + "?page=0&size=10", HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("content");
    }
}
