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
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyStatusResponse;
import com.gamingcastle.loyaltyservice.dto.response.RedemptionReservationResponse;
import com.gamingcastle.loyaltyservice.dto.response.ReverseLoyaltyResponse;
import com.gamingcastle.loyaltyservice.dto.response.TransactionHistoryResponse;
import com.gamingcastle.loyaltyservice.entity.LoyaltyAccount;
import com.gamingcastle.loyaltyservice.entity.LoyaltyRules;
import com.gamingcastle.loyaltyservice.entity.LoyaltyTier;
import com.gamingcastle.loyaltyservice.entity.LoyaltyTransaction;
import com.gamingcastle.loyaltyservice.entity.LoyaltyTransactionStatus;
import com.gamingcastle.loyaltyservice.entity.LoyaltyTransactionType;
import com.gamingcastle.loyaltyservice.exception.BelowMinimumRedemptionException;
import com.gamingcastle.loyaltyservice.exception.InsufficientPointsException;
import com.gamingcastle.loyaltyservice.exception.InvalidLoyaltyRequestException;
import com.gamingcastle.loyaltyservice.exception.InvalidRedemptionStepException;
import com.gamingcastle.loyaltyservice.exception.LoyaltyRulesNotFoundException;
import com.gamingcastle.loyaltyservice.exception.RedemptionLimitExceededException;
import com.gamingcastle.loyaltyservice.repository.LoyaltyAccountRepository;
import com.gamingcastle.loyaltyservice.repository.LoyaltyRulesRepository;
import com.gamingcastle.loyaltyservice.repository.LoyaltyTransactionRepository;
import com.gamingcastle.loyaltyservice.service.impl.LoyaltyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoyaltyServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();

    @Mock
    private LoyaltyAccountRepository accountRepository;
    @Mock
    private LoyaltyTransactionRepository transactionRepository;
    @Mock
    private LoyaltyRulesRepository rulesRepository;

    private LoyaltyServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new LoyaltyServiceImpl(accountRepository, transactionRepository, rulesRepository);
        when(rulesRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(defaultRules()));
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(LoyaltyTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static LoyaltyRules defaultRules() {
        return LoyaltyRules.builder().build();
    }

    private LoyaltyAccount givenAccount(long balance, long lifetime, LoyaltyTier tier) {
        LoyaltyAccount account = LoyaltyAccount.builder()
                .userId(USER_ID)
                .pointsBalance(balance)
                .lifetimeEarned(lifetime)
                .tier(tier)
                .build();
        when(accountRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(account));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        return account;
    }

    private static LoyaltyTransaction transaction(LoyaltyTransactionType type, LoyaltyTransactionStatus status,
                                                  long points, String discount) {
        return LoyaltyTransaction.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .bookingId(BOOKING_ID)
                .type(type)
                .status(status)
                .points(points)
                .discountAmount(new BigDecimal(discount))
                .createdAt(Instant.now())
                .build();
    }

    private void givenActiveRedemption(LoyaltyTransaction redemption) {
        when(transactionRepository.findFirstByBookingIdAndTypeAndStatusInOrderByCreatedAtDesc(
                eq(BOOKING_ID), eq(LoyaltyTransactionType.REDEEMED), anyCollection()))
                .thenReturn(Optional.of(redemption));
    }

    private void givenEarned(long points) {
        when(transactionRepository.findByBookingIdAndTypeAndStatus(
                BOOKING_ID, LoyaltyTransactionType.EARNED, LoyaltyTransactionStatus.CONFIRMED))
                .thenReturn(Optional.of(transaction(LoyaltyTransactionType.EARNED,
                        LoyaltyTransactionStatus.CONFIRMED, points, "0.00")));
    }

    private static RedeemPointsRequest reserveRequest(String total, long points) {
        return new RedeemPointsRequest(USER_ID, BOOKING_ID, new BigDecimal(total), points);
    }

    private List<LoyaltyTransaction> savedTransactions(int expectedSaves) {
        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository, times(expectedSaves)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void getBalance_shouldReturnZero_whenUserHasNoAccount() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        LoyaltyAccountResponse response = service.getBalance(USER_ID);

        assertThat(response.pointsBalance()).isZero();
        assertThat(response.tier()).isEqualTo("BRONZE");
        assertThat(response.equivalentDiscount()).isEqualByComparingTo("0.00");
    }

    @Test
    void getBalance_shouldShowLkrWorthOfPoints() {
        givenAccount(400, 980, LoyaltyTier.BRONZE);

        LoyaltyAccountResponse response = service.getBalance(USER_ID);

        assertThat(response.pointsBalance()).isEqualTo(400);
        assertThat(response.equivalentDiscount()).isEqualByComparingTo("200.00");
    }

    @Test
    void getRules_shouldThrow_whenRulesAreNotConfigured() {
        when(rulesRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRules()).isInstanceOf(LoyaltyRulesNotFoundException.class);
    }

    @Test
    void getHistory_shouldPageInTheDatabase() {
        LoyaltyTransaction tx = transaction(LoyaltyTransactionType.EARNED, LoyaltyTransactionStatus.CONFIRMED, 12, "0.00");
        when(transactionRepository.findByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));

        Page<TransactionHistoryResponse> page = service.getHistory(USER_ID, Pageable.unpaged());

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).type()).isEqualTo("EARNED");
    }

    @Test
    void awardPoints_shouldAddPointsAndWriteLedgerRow() {
        LoyaltyAccount account = givenAccount(400, 980, LoyaltyTier.BRONZE);

        AwardPointsResponse response = service.awardPoints(
                new AwardPointsRequest(USER_ID, BOOKING_ID, new BigDecimal("1250.00"), UUID.randomUUID()));

        assertThat(response.pointsAwarded()).isEqualTo(12);
        assertThat(response.newBalance()).isEqualTo(412);
        assertThat(response.alreadyProcessed()).isFalse();
        assertThat(account.getLifetimeEarned()).isEqualTo(992);

        LoyaltyTransaction row = savedTransactions(1).get(0);
        assertThat(row.getType()).isEqualTo(LoyaltyTransactionType.EARNED);
        assertThat(row.getPoints()).isEqualTo(12);
        assertThat(row.getBalanceAfter()).isEqualTo(412);
    }

    @Test
    void awardPoints_shouldUseSilverMultiplierFromRules_notAHardCodedValue() {
        LoyaltyRules rules = defaultRules();
        rules.setSilverMultiplier(new BigDecimal("2.00"));
        when(rulesRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(rules));
        givenAccount(0, 1000, LoyaltyTier.SILVER);

        AwardPointsResponse response = service.awardPoints(
                new AwardPointsRequest(USER_ID, BOOKING_ID, new BigDecimal("2000.00"), null));

        assertThat(response.pointsAwarded()).isEqualTo(40);
    }

    @Test
    void awardPoints_shouldUpgradeTier_whenLifetimePointsReachSilverThreshold() {
        LoyaltyAccount account = givenAccount(0, 980, LoyaltyTier.BRONZE);

        AwardPointsResponse response = service.awardPoints(
                new AwardPointsRequest(USER_ID, BOOKING_ID, new BigDecimal("2000.00"), null));

        assertThat(response.pointsAwarded()).isEqualTo(20);
        assertThat(account.getLifetimeEarned()).isEqualTo(1000);
        assertThat(account.getTier()).isEqualTo(LoyaltyTier.SILVER);
        assertThat(response.tier()).isEqualTo("SILVER");
    }

    @Test
    void awardPoints_shouldBeIdempotent_whenBookingAlreadyAwarded() {
        LoyaltyAccount account = givenAccount(412, 992, LoyaltyTier.BRONZE);
        givenEarned(12);

        AwardPointsResponse response = service.awardPoints(
                new AwardPointsRequest(USER_ID, BOOKING_ID, new BigDecimal("1250.00"), null));

        assertThat(response.alreadyProcessed()).isTrue();
        assertThat(account.getPointsBalance()).isEqualTo(412);
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void awardPoints_shouldAddNothing_whenBookingWasAlreadyCancelled() {
        givenAccount(100, 100, LoyaltyTier.BRONZE);
        when(transactionRepository.existsByBookingIdAndType(BOOKING_ID, LoyaltyTransactionType.EARN_REVERSED))
                .thenReturn(true);

        AwardPointsResponse response = service.awardPoints(
                new AwardPointsRequest(USER_ID, BOOKING_ID, new BigDecimal("1000.00"), null));

        assertThat(response.pointsAwarded()).isZero();
        assertThat(response.alreadyProcessed()).isTrue();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void calculateRedemption_shouldReturnDiscountAndPayable_whenValid() {
        givenAccount(400, 980, LoyaltyTier.BRONZE);

        CalculationResponse response = service.calculateRedemption(
                new CalculateRedemptionRequest(USER_ID, new BigDecimal("1200.00"), 400));

        assertThat(response.valid()).isTrue();
        assertThat(response.discountAmount()).isEqualByComparingTo("200.00");
        assertThat(response.payableAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void calculateRedemption_shouldExplainWhyInvalid_whenBelowMinimum() {
        givenAccount(400, 980, LoyaltyTier.BRONZE);

        CalculationResponse response = service.calculateRedemption(
                new CalculateRedemptionRequest(USER_ID, new BigDecimal("1200.00"), 50));

        assertThat(response.valid()).isFalse();
        assertThat(response.reason()).contains("Minimum redemption");
        assertThat(response.payableAmount()).isEqualByComparingTo("1200.00");
    }

    @Test
    void reserveRedemption_shouldHoldPointsAsPending() {
        LoyaltyAccount account = givenAccount(400, 980, LoyaltyTier.BRONZE);

        RedemptionReservationResponse response = service.reserveRedemption(reserveRequest("1200.00", 400));

        assertThat(response.pointsReserved()).isEqualTo(400);
        assertThat(response.discountAmount()).isEqualByComparingTo("200.00");
        assertThat(response.payableAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.newBalance()).isZero();
        assertThat(account.getPointsBalance()).isZero();

        LoyaltyTransaction row = savedTransactions(1).get(0);
        assertThat(row.getType()).isEqualTo(LoyaltyTransactionType.REDEEMED);
        assertThat(row.getStatus()).isEqualTo(LoyaltyTransactionStatus.PENDING);
    }

    @Test
    void reserveRedemption_shouldReturnExistingReservation_evenThoughBalanceIsNowLower() {
        LoyaltyAccount account = givenAccount(0, 980, LoyaltyTier.BRONZE);
        givenActiveRedemption(transaction(LoyaltyTransactionType.REDEEMED,
                LoyaltyTransactionStatus.PENDING, 400, "200.00"));

        RedemptionReservationResponse response = service.reserveRedemption(reserveRequest("1200.00", 400));

        assertThat(response.pointsReserved()).isEqualTo(400);
        assertThat(response.discountAmount()).isEqualByComparingTo("200.00");
        assertThat(response.newBalance()).isZero();
        assertThat(account.getPointsBalance()).isZero();
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void reserveRedemption_shouldReject_whenBelowMinimum() {
        givenAccount(400, 0, LoyaltyTier.BRONZE);

        assertThatThrownBy(() -> service.reserveRedemption(reserveRequest("1200.00", 50)))
                .isInstanceOf(BelowMinimumRedemptionException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void reserveRedemption_shouldReject_whenNotAMultipleOfTheStep() {
        givenAccount(400, 0, LoyaltyTier.BRONZE);

        assertThatThrownBy(() -> service.reserveRedemption(reserveRequest("1200.00", 105)))
                .isInstanceOf(InvalidRedemptionStepException.class);
    }

    @Test
    void reserveRedemption_shouldReject_whenBalanceIsTooLow() {
        givenAccount(50, 0, LoyaltyTier.BRONZE);

        assertThatThrownBy(() -> service.reserveRedemption(reserveRequest("1200.00", 100)))
                .isInstanceOf(InsufficientPointsException.class);
    }

    @Test
    void reserveRedemption_shouldReject_whenDiscountExceedsTheCap() {
        givenAccount(1000, 0, LoyaltyTier.BRONZE);

        // 600 points = LKR 300 but the cap for a LKR 500 booking is LKR 250
        assertThatThrownBy(() -> service.reserveRedemption(reserveRequest("500.00", 600)))
                .isInstanceOf(RedemptionLimitExceededException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void confirmRedemption_shouldMovePendingToConfirmed() {
        LoyaltyTransaction pending = transaction(LoyaltyTransactionType.REDEEMED,
                LoyaltyTransactionStatus.PENDING, 400, "200.00");
        when(transactionRepository.findByBookingIdAndTypeAndStatus(
                BOOKING_ID, LoyaltyTransactionType.REDEEMED, LoyaltyTransactionStatus.PENDING))
                .thenReturn(Optional.of(pending));

        LoyaltyStatusResponse response = service.confirmRedemption(BOOKING_ID);

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(pending.getStatus()).isEqualTo(LoyaltyTransactionStatus.CONFIRMED);
    }

    @Test
    void confirmRedemption_shouldReturnNoAction_whenNothingIsPending() {
        LoyaltyStatusResponse response = service.confirmRedemption(BOOKING_ID);

        assertThat(response.status()).isEqualTo("NO_ACTION");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void releaseRedemption_shouldReturnPointsAndWriteAuditRow() {
        LoyaltyTransaction pending = transaction(LoyaltyTransactionType.REDEEMED,
                LoyaltyTransactionStatus.PENDING, 400, "200.00");
        when(transactionRepository.findByBookingIdAndTypeAndStatus(
                BOOKING_ID, LoyaltyTransactionType.REDEEMED, LoyaltyTransactionStatus.PENDING))
                .thenReturn(Optional.of(pending));
        LoyaltyAccount account = givenAccount(0, 980, LoyaltyTier.BRONZE);

        LoyaltyStatusResponse response = service.releaseRedemption(BOOKING_ID, "Payment failed");

        assertThat(response.status()).isEqualTo("RELEASED");
        assertThat(response.pointsReturned()).isEqualTo(400);
        assertThat(account.getPointsBalance()).isEqualTo(400);
        assertThat(pending.getStatus()).isEqualTo(LoyaltyTransactionStatus.RELEASED);

        List<LoyaltyTransaction> saved = savedTransactions(2);
        assertThat(saved).anyMatch(tx -> tx.getType() == LoyaltyTransactionType.REDEMPTION_RELEASED
                && tx.getStatus() == LoyaltyTransactionStatus.RELEASED);
    }

    @Test
    void releaseRedemption_shouldReturnNoAction_whenNothingIsPending() {
        LoyaltyStatusResponse response = service.releaseRedemption(BOOKING_ID, "Payment failed");

        assertThat(response.status()).isEqualTo("NO_ACTION");
        verify(accountRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    void reverseBooking_shouldRestoreRedeemedPointsAndRemoveEarnedPoints() {
        LoyaltyAccount account = givenAccount(10, 1010, LoyaltyTier.SILVER);
        givenActiveRedemption(transaction(LoyaltyTransactionType.REDEEMED,
                LoyaltyTransactionStatus.CONFIRMED, 400, "200.00"));
        givenEarned(10);

        ReverseLoyaltyResponse response = service.reverseBooking(BOOKING_ID, "Booking cancelled");

        assertThat(response.redeemedPointsRestored()).isEqualTo(400);
        assertThat(response.earnedPointsReversed()).isEqualTo(10);
        assertThat(response.reservationReleased()).isFalse();
        assertThat(account.getPointsBalance()).isEqualTo(400);
        assertThat(account.getLifetimeEarned()).isEqualTo(1000);
    }

    @Test
    void reverseBooking_shouldReleaseAPendingReservation() {
        LoyaltyAccount account = givenAccount(0, 0, LoyaltyTier.BRONZE);
        givenActiveRedemption(transaction(LoyaltyTransactionType.REDEEMED,
                LoyaltyTransactionStatus.PENDING, 200, "100.00"));

        ReverseLoyaltyResponse response = service.reverseBooking(BOOKING_ID, null);

        assertThat(response.reservationReleased()).isTrue();
        assertThat(account.getPointsBalance()).isEqualTo(200);
    }

    @Test
    void reverseBooking_shouldOnlyRemoveWhatIsAvailable_whenPointsWereAlreadySpent() {
        LoyaltyAccount account = givenAccount(5, 12, LoyaltyTier.BRONZE);
        givenEarned(12);

        ReverseLoyaltyResponse response = service.reverseBooking(BOOKING_ID, "Booking cancelled");

        assertThat(response.earnedPointsReversed()).isEqualTo(5);
        assertThat(account.getPointsBalance()).isZero();
        assertThat(account.getLifetimeEarned()).isZero();
    }

    @Test
    void reverseBooking_shouldDoNothing_whenCalledASecondTime() {
        LoyaltyAccount account = givenAccount(400, 1000, LoyaltyTier.SILVER);
        givenEarned(10);
        when(transactionRepository.existsByBookingIdAndType(BOOKING_ID, LoyaltyTransactionType.EARN_REVERSED))
                .thenReturn(true);

        ReverseLoyaltyResponse response = service.reverseBooking(BOOKING_ID, "Booking cancelled");

        assertThat(response.earnedPointsReversed()).isZero();
        assertThat(response.redeemedPointsRestored()).isZero();
        assertThat(response.newBalance()).isEqualTo(400);
        assertThat(account.getPointsBalance()).isEqualTo(400);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void reverseBooking_shouldReturnZeros_whenNothingExistsForTheBooking() {
        ReverseLoyaltyResponse response = service.reverseBooking(BOOKING_ID, "Booking cancelled");

        assertThat(response.earnedPointsReversed()).isZero();
        assertThat(response.redeemedPointsRestored()).isZero();
        assertThat(response.reservationReleased()).isFalse();
        verify(accountRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    void adjustPoints_shouldStoreTheSignedValue() {
        LoyaltyAccount account = givenAccount(100, 100, LoyaltyTier.BRONZE);

        AdminAdjustmentResponse response = service.adjustPoints(new AdjustPointsRequest(USER_ID, "Compensation", 50));

        assertThat(response.newBalance()).isEqualTo(150);
        assertThat(account.getLifetimeEarned()).isEqualTo(100);
        LoyaltyTransaction row = savedTransactions(1).get(0);
        assertThat(row.getType()).isEqualTo(LoyaltyTransactionType.ADJUSTED);
        assertThat(row.getPoints()).isEqualTo(50);
    }

    @Test
    void adjustPoints_shouldRejectABalanceBelowZero() {
        givenAccount(30, 30, LoyaltyTier.BRONZE);

        assertThatThrownBy(() -> service.adjustPoints(new AdjustPointsRequest(USER_ID, "Correction", -50)))
                .isInstanceOf(InsufficientPointsException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void adjustPoints_shouldRejectZero() {
        assertThatThrownBy(() -> service.adjustPoints(new AdjustPointsRequest(USER_ID, "Nothing", 0)))
                .isInstanceOf(InvalidLoyaltyRequestException.class);
    }

    @Test
    void updateRules_shouldSaveNewValuesAndRecordTheAdmin() {
        UUID adminId = UUID.randomUUID();
        when(rulesRepository.save(any(LoyaltyRules.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateRules(rulesRequest(1000, 3000, "1.25", "1.50"), adminId);

        ArgumentCaptor<LoyaltyRules> captor = ArgumentCaptor.forClass(LoyaltyRules.class);
        verify(rulesRepository).save(captor.capture());
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(adminId.toString());
        assertThat(captor.getValue().getSilverMultiplier()).isEqualByComparingTo("1.25");
    }

    @Test
    void updateRules_shouldRejectSilverThresholdNotBelowGold() {
        assertThatThrownBy(() -> service.updateRules(rulesRequest(3000, 1000, "1.25", "1.50"), UUID.randomUUID()))
                .isInstanceOf(InvalidLoyaltyRequestException.class);
        verify(rulesRepository, never()).save(any());
    }

    @Test
    void updateRules_shouldRejectGoldMultiplierBelowSilver() {
        assertThatThrownBy(() -> service.updateRules(rulesRequest(1000, 3000, "1.50", "1.25"), UUID.randomUUID()))
                .isInstanceOf(InvalidLoyaltyRequestException.class);
    }

    private static UpdateLoyaltyRulesRequest rulesRequest(long silver, long gold, String silverMultiplier, String goldMultiplier) {
        return new UpdateLoyaltyRulesRequest(
                new BigDecimal("100.00"), new BigDecimal("0.50"), 100, 10, 50, 15,
                silver, new BigDecimal(silverMultiplier), gold, new BigDecimal(goldMultiplier));
    }

    @Test
    void findExpiredReservationBookingIds_shouldReturnBookingsWithOldPendingReservations() {
        LoyaltyTransaction old = transaction(LoyaltyTransactionType.REDEEMED,
                LoyaltyTransactionStatus.PENDING, 100, "50.00");
        when(transactionRepository.findByTypeAndStatusAndCreatedAtBefore(
                eq(LoyaltyTransactionType.REDEEMED), eq(LoyaltyTransactionStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(old));

        assertThat(service.findExpiredReservationBookingIds()).containsExactly(BOOKING_ID);
    }
}