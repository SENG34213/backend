package com.gamingcastle.loyaltyservice.controller;

import com.gamingcastle.loyaltyservice.api.APIEndPoints;
import com.gamingcastle.loyaltyservice.dto.request.*;
import com.gamingcastle.loyaltyservice.dto.response.*;
import com.gamingcastle.loyaltyservice.entity.*;
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
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoyaltyServiceInternalIntegrationTest {

    private static final String AWARD_URL = "/api" + APIEndPoints.award;
    private static final String RESERVE_URL = "/api" + APIEndPoints.redeemReserve;
    private static final String CONFIRM_URL = "/api" + APIEndPoints.redeemConfirm;
    private static final String RELEASE_URL = "/api" + APIEndPoints.redeemRelease;
    private static final String REVERSE_URL = "/api" + APIEndPoints.reverse;

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

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(GatewayAuthenticationFilter.GATEWAY_SECRET_HEADER, gatewaySecret);
        return headers;
    }

    private LoyaltyAccount createAccount(UUID userId, long balance, long lifetime, LoyaltyTier tier) {
        return accountRepository.save(LoyaltyAccount.builder()
                .userId(userId)
                .pointsBalance(balance)
                .lifetimeEarned(lifetime)
                .tier(tier)
                .build());
    }

    // =========================================================================
    // 1. POST /api/internal/loyalty/award (Award points on payment)
    // =========================================================================

    @Test
    @DisplayName("POST /api/internal/loyalty/award - awards points for booking payment and updates balance & tier")
    void award_shouldAwardPointsAndUpdateBalanceAndTier() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createAccount(userId, 100L, 950L, LoyaltyTier.BRONZE);

        AwardPointsRequest request = new AwardPointsRequest(userId, bookingId, new BigDecimal("10000.00"), paymentId);
        HttpEntity<AwardPointsRequest> entity = new HttpEntity<>(request, internalHeaders());

        ResponseEntity<AwardPointsResponse> response = restTemplate.postForEntity(
                AWARD_URL, entity, AwardPointsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // 10000 paid / 100 per point * 1.0 multiplier (BRONZE) = 100 points
        assertThat(response.getBody().pointsAwarded()).isEqualTo(100L);
        assertThat(response.getBody().newBalance()).isEqualTo(200L);
        assertThat(response.getBody().tier()).isEqualTo("SILVER"); // 950 + 100 = 1050 >= 1000 threshold
        assertThat(response.getBody().alreadyProcessed()).isFalse();

        // Verify DB state
        LoyaltyAccount updated = accountRepository.findByUserId(userId).orElseThrow();
        assertThat(updated.getPointsBalance()).isEqualTo(200L);
        assertThat(updated.getLifetimeEarned()).isEqualTo(1050L);
        assertThat(updated.getTier()).isEqualTo(LoyaltyTier.SILVER);

        List<LoyaltyTransaction> txs = transactionRepository.findAll();
        assertThat(txs).hasSize(1);
        assertThat(txs.get(0).getType()).isEqualTo(LoyaltyTransactionType.EARNED);
        assertThat(txs.get(0).getStatus()).isEqualTo(LoyaltyTransactionStatus.CONFIRMED);
    }

    @Test
    @DisplayName("POST /api/internal/loyalty/award - is idempotent when called again for same booking")
    void award_shouldBeIdempotentOnDuplicateCall() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createAccount(userId, 50L, 50L, LoyaltyTier.BRONZE);

        AwardPointsRequest request = new AwardPointsRequest(userId, bookingId, new BigDecimal("2000.00"), paymentId);
        HttpEntity<AwardPointsRequest> entity = new HttpEntity<>(request, internalHeaders());

        // First call
        restTemplate.postForEntity(AWARD_URL, entity, AwardPointsResponse.class);

        // Second call
        ResponseEntity<AwardPointsResponse> response = restTemplate.postForEntity(
                AWARD_URL, entity, AwardPointsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().alreadyProcessed()).isTrue();
        assertThat(response.getBody().pointsAwarded()).isEqualTo(20L);

        // Account balance was not doubled
        LoyaltyAccount updated = accountRepository.findByUserId(userId).orElseThrow();
        assertThat(updated.getPointsBalance()).isEqualTo(70L);
    }

    // =========================================================================
    // 2. POST /api/internal/loyalty/redeem/reserve (Reserve points)
    // =========================================================================

    @Test
    @DisplayName("POST /api/internal/loyalty/redeem/reserve - holds points as PENDING and returns discount")
    void reserve_shouldHoldPointsAsPending() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        createAccount(userId, 500L, 500L, LoyaltyTier.BRONZE);

        RedeemPointsRequest request = new RedeemPointsRequest(userId, bookingId, new BigDecimal("2000.00"), 200L);
        HttpEntity<RedeemPointsRequest> entity = new HttpEntity<>(request, internalHeaders());

        ResponseEntity<RedemptionReservationResponse> response = restTemplate.postForEntity(
                RESERVE_URL, entity, RedemptionReservationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().pointsReserved()).isEqualTo(200L);
        assertThat(response.getBody().discountAmount()).isEqualByComparingTo("100.00"); // 200 * 0.50 LKR
        assertThat(response.getBody().payableAmount()).isEqualByComparingTo("1900.00");
        assertThat(response.getBody().newBalance()).isEqualTo(300L);

        // Verify DB state
        LoyaltyAccount account = accountRepository.findByUserId(userId).orElseThrow();
        assertThat(account.getPointsBalance()).isEqualTo(300L);

        List<LoyaltyTransaction> txs = transactionRepository.findAll();
        assertThat(txs).hasSize(1);
        assertThat(txs.get(0).getType()).isEqualTo(LoyaltyTransactionType.REDEEMED);
        assertThat(txs.get(0).getStatus()).isEqualTo(LoyaltyTransactionStatus.PENDING);
    }

    @Test
    @DisplayName("POST /api/internal/loyalty/redeem/reserve - returns 422 when balance is insufficient")
    void reserve_shouldReturn422WhenInsufficientPoints() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        createAccount(userId, 50L, 50L, LoyaltyTier.BRONZE);

        RedeemPointsRequest request = new RedeemPointsRequest(userId, bookingId, new BigDecimal("2000.00"), 200L);
        HttpEntity<RedeemPointsRequest> entity = new HttpEntity<>(request, internalHeaders());

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                RESERVE_URL, entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INSUFFICIENT_POINTS");
    }

    // =========================================================================
    // 3. POST /api/internal/loyalty/redeem/confirm (Confirm redemption)
    // =========================================================================

    @Test
    @DisplayName("POST /api/internal/loyalty/redeem/confirm - moves reservation from PENDING to CONFIRMED")
    void confirm_shouldMovePendingToConfirmed() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        createAccount(userId, 300L, 500L, LoyaltyTier.BRONZE);

        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(userId)
                .bookingId(bookingId)
                .type(LoyaltyTransactionType.REDEEMED)
                .status(LoyaltyTransactionStatus.PENDING)
                .points(200L)
                .discountAmount(new BigDecimal("100.00"))
                .balanceAfter(300L)
                .build());

        ConfirmRedemptionRequest request = new ConfirmRedemptionRequest(bookingId);
        HttpEntity<ConfirmRedemptionRequest> entity = new HttpEntity<>(request, internalHeaders());

        ResponseEntity<LoyaltyStatusResponse> response = restTemplate.postForEntity(
                CONFIRM_URL, entity, LoyaltyStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("CONFIRMED");
        assertThat(response.getBody().pointsRedeemed()).isEqualTo(200L);

        // Verify DB
        LoyaltyTransaction tx = transactionRepository.findByBookingIdAndTypeAndStatus(
                bookingId, LoyaltyTransactionType.REDEEMED, LoyaltyTransactionStatus.CONFIRMED).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(LoyaltyTransactionStatus.CONFIRMED);
    }

    // =========================================================================
    // 4. POST /api/internal/loyalty/redeem/release (Release redemption)
    // =========================================================================

    @Test
    @DisplayName("POST /api/internal/loyalty/redeem/release - returns reserved points and sets status to RELEASED")
    void release_shouldReturnReservedPoints() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        createAccount(userId, 300L, 500L, LoyaltyTier.BRONZE);

        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(userId)
                .bookingId(bookingId)
                .type(LoyaltyTransactionType.REDEEMED)
                .status(LoyaltyTransactionStatus.PENDING)
                .points(200L)
                .discountAmount(new BigDecimal("100.00"))
                .balanceAfter(300L)
                .build());

        ReleaseRedemptionRequest request = new ReleaseRedemptionRequest(bookingId, "Payment cancelled by user");
        HttpEntity<ReleaseRedemptionRequest> entity = new HttpEntity<>(request, internalHeaders());

        ResponseEntity<LoyaltyStatusResponse> response = restTemplate.postForEntity(
                RELEASE_URL, entity, LoyaltyStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("RELEASED");
        assertThat(response.getBody().pointsReturned()).isEqualTo(200L);
        assertThat(response.getBody().newBalance()).isEqualTo(500L);

        // Verify balance restored in DB
        LoyaltyAccount account = accountRepository.findByUserId(userId).orElseThrow();
        assertThat(account.getPointsBalance()).isEqualTo(500L);
    }

    // =========================================================================
    // 5. POST /api/internal/loyalty/reverse (Reverse booking)
    // =========================================================================

    @Test
    @DisplayName("POST /api/internal/loyalty/reverse - reverses earned points and restores confirmed redeemed points")
    void reverse_shouldReverseEarnedAndRestoreRedeemedPoints() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        createAccount(userId, 350L, 800L, LoyaltyTier.BRONZE);

        // Earned 50 points
        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(userId)
                .bookingId(bookingId)
                .type(LoyaltyTransactionType.EARNED)
                .status(LoyaltyTransactionStatus.CONFIRMED)
                .points(50L)
                .balanceAfter(350L)
                .build());

        // Confirmed redemption of 200 points
        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(userId)
                .bookingId(bookingId)
                .type(LoyaltyTransactionType.REDEEMED)
                .status(LoyaltyTransactionStatus.CONFIRMED)
                .points(200L)
                .discountAmount(new BigDecimal("100.00"))
                .balanceAfter(150L)
                .build());

        ReverseLoyaltyRequest request = new ReverseLoyaltyRequest(bookingId, "Customer refund");
        HttpEntity<ReverseLoyaltyRequest> entity = new HttpEntity<>(request, internalHeaders());

        ResponseEntity<ReverseLoyaltyResponse> response = restTemplate.postForEntity(
                REVERSE_URL, entity, ReverseLoyaltyResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().earnedPointsReversed()).isEqualTo(50L);
        assertThat(response.getBody().redeemedPointsRestored()).isEqualTo(200L);
        // new balance: 350 - 50 + 200 = 500
        assertThat(response.getBody().newBalance()).isEqualTo(500L);

        LoyaltyAccount account = accountRepository.findByUserId(userId).orElseThrow();
        assertThat(account.getPointsBalance()).isEqualTo(500L);
        assertThat(account.getLifetimeEarned()).isEqualTo(750L); // 800 - 50
    }

    // =========================================================================
    // 6. Security tests for internal endpoints
    // =========================================================================

    @Test
    @DisplayName("POST /api/internal/loyalty/award - returns 401 UNAUTHORIZED when gateway secret is missing")
    void internalEndpoint_withoutGatewaySecret_shouldReturn401() {
        AwardPointsRequest request = new AwardPointsRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"), UUID.randomUUID());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AwardPointsRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                AWARD_URL, entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /api/internal/loyalty/award - returns 403 FORBIDDEN when user header is sent to internal endpoint")
    void internalEndpoint_withUserHeader_shouldReturn403() {
        AwardPointsRequest request = new AwardPointsRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"), UUID.randomUUID());
        HttpHeaders headers = internalHeaders();
        headers.set(GatewayAuthenticationFilter.USER_ID_HEADER, UUID.randomUUID().toString());
        HttpEntity<AwardPointsRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                AWARD_URL, entity, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
