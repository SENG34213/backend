package com.gamingcastle.loyaltyservice.service.impl;

import com.gamingcastle.loyaltyservice.dto.request.AdjustPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.AwardPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.CalculateRedemptionRequest;
import com.gamingcastle.loyaltyservice.dto.request.RedeemPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.UpdateLoyaltyRulesRequest;
import com.gamingcastle.loyaltyservice.dto.response.AdminAdjustmentResponse;
import com.gamingcastle.loyaltyservice.dto.response.AwardPointsResponse;
import com.gamingcastle.loyaltyservice.dto.response.CalculationResponse;
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyAccountResponse;
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyRulesResponse;
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
import com.gamingcastle.loyaltyservice.exception.RedemptionRejectedException;
import com.gamingcastle.loyaltyservice.repository.LoyaltyAccountRepository;
import com.gamingcastle.loyaltyservice.repository.LoyaltyRulesRepository;
import com.gamingcastle.loyaltyservice.repository.LoyaltyTransactionRepository;
import com.gamingcastle.loyaltyservice.service.LoyaltyService;
import com.gamingcastle.loyaltyservice.util.PointsCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class LoyaltyServiceImpl implements LoyaltyService {

    private static final List<LoyaltyTransactionStatus> ACTIVE_STATUSES =
            List.of(LoyaltyTransactionStatus.PENDING, LoyaltyTransactionStatus.CONFIRMED);

    private static final int DESCRIPTION_MAX_LENGTH = 255;

    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final LoyaltyRulesRepository rulesRepository;

    public LoyaltyServiceImpl(LoyaltyAccountRepository accountRepository,
                              LoyaltyTransactionRepository transactionRepository,
                              LoyaltyRulesRepository rulesRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.rulesRepository = rulesRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public LoyaltyAccountResponse getBalance(UUID userId) {
        LoyaltyRules rules = getRulesOrThrow();
        Optional<LoyaltyAccount> account = accountRepository.findByUserId(userId);

        long balance = account.map(LoyaltyAccount::getPointsBalance).orElse(0L);
        long lifetime = account.map(LoyaltyAccount::getLifetimeEarned).orElse(0L);
        LoyaltyTier tier = account.map(LoyaltyAccount::getTier).orElse(LoyaltyTier.BRONZE);

        return new LoyaltyAccountResponse(
                userId,
                balance,
                lifetime,
                tier.name(),
                rules.getPointValueLkr(),
                PointsCalculator.calculateDiscount(balance, rules.getPointValueLkr()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionHistoryResponse> getHistory(UUID userId, Pageable pageable) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable effective;
        if (!pageable.isPaged()) {
            effective = PageRequest.of(0, 20, newestFirst);
        } else if (pageable.getSort().isUnsorted()) {
            effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), newestFirst);
        } else {
            effective = pageable;
        }
        return transactionRepository.findByUserId(userId, effective).map(this::toHistoryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CalculationResponse calculateRedemption(CalculateRedemptionRequest request) {
        BigDecimal bookingTotal = request.bookingTotal();
        if (bookingTotal.signum() <= 0) {
            throw new InvalidLoyaltyRequestException("bookingTotal must be greater than zero");
        }

        LoyaltyRules rules = getRulesOrThrow();
        long balance = accountRepository.findByUserId(request.userId())
                .map(LoyaltyAccount::getPointsBalance)
                .orElse(0L);
        long points = request.pointsToRedeem();

        try {
            BigDecimal discount = validateRedemption(rules, points, bookingTotal, balance);
            return new CalculationResponse(request.userId(), bookingTotal, points, discount,
                    PointsCalculator.calculatePayableAmount(bookingTotal, discount), true, null);
        } catch (RedemptionRejectedException | InsufficientPointsException ex) {
            return new CalculationResponse(request.userId(), bookingTotal, points, BigDecimal.ZERO,
                    bookingTotal, false, ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LoyaltyRulesResponse getRules() {
        return toRulesResponse(getRulesOrThrow());
    }

    @Override
    @Transactional
    public LoyaltyRulesResponse updateRules(UpdateLoyaltyRulesRequest request, UUID adminId) {
        validateRulesRequest(request);

        LoyaltyRules rules = rulesRepository.findTopByOrderByUpdatedAtDesc().orElseGet(LoyaltyRules::new);
        rules.setAmountPerPoint(request.amountPerPoint());
        rules.setPointValueLkr(request.pointValueLkr());
        rules.setMinRedeemPoints(request.minRedeemPoints());
        rules.setRedeemStep(request.redeemStep());
        rules.setMaxDiscountPercent(request.maxDiscountPercent());
        rules.setReservationTimeoutMinutes(request.reservationTimeoutMinutes());
        rules.setSilverThreshold(request.silverThreshold());
        rules.setSilverMultiplier(request.silverMultiplier());
        rules.setGoldThreshold(request.goldThreshold());
        rules.setGoldMultiplier(request.goldMultiplier());
        rules.setUpdatedBy(adminId == null ? null : adminId.toString());

        LoyaltyRules saved = rulesRepository.save(rules);
        log.info("Loyalty rules updated: adminId={}", adminId);
        return toRulesResponse(saved);
    }

    @Override
    @Transactional
    public AdminAdjustmentResponse adjustPoints(AdjustPointsRequest request) {
        if (request.points() == 0) {
            throw new InvalidLoyaltyRequestException("points must not be zero");
        }

        LoyaltyAccount account = lockOrCreateAccount(request.userId());
        long newBalance = account.getPointsBalance() + request.points();
        if (newBalance < 0) {
            throw new InsufficientPointsException("Adjustment would make the balance negative (current balance: "
                    + account.getPointsBalance() + ")");
        }

        account.setPointsBalance(newBalance);
        accountRepository.save(account);
        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(request.userId())
                .type(LoyaltyTransactionType.ADJUSTED)
                .status(LoyaltyTransactionStatus.CONFIRMED)
                .points(request.points())
                .balanceAfter(newBalance)
                .description(truncate("Admin adjustment: " + request.reason().trim()))
                .build());

        log.info("Points adjusted: userId={}, points={}, newBalance={}", request.userId(), request.points(), newBalance);
        return new AdminAdjustmentResponse(request.userId(), request.points(), newBalance, request.reason());
    }

    @Override
    @Transactional
    public AwardPointsResponse awardPoints(AwardPointsRequest request) {
        LoyaltyRules rules = getRulesOrThrow();
        LoyaltyAccount account = lockOrCreateAccount(request.userId());

        Optional<LoyaltyTransaction> existing = transactionRepository.findByBookingIdAndTypeAndStatus(
                request.bookingId(), LoyaltyTransactionType.EARNED, LoyaltyTransactionStatus.CONFIRMED);
        if (existing.isPresent()) {
            log.info("Award already processed: userId={}, bookingId={}", request.userId(), request.bookingId());
            return new AwardPointsResponse(existing.get().getPoints(), account.getPointsBalance(),
                    account.getTier().name(), true);
        }

        if (transactionRepository.existsByBookingIdAndType(request.bookingId(), LoyaltyTransactionType.EARN_REVERSED)) {
            log.warn("Award ignored because the booking was already cancelled: userId={}, bookingId={}",
                    request.userId(), request.bookingId());
            return new AwardPointsResponse(0, account.getPointsBalance(), account.getTier().name(), true);
        }

        BigDecimal multiplier = PointsCalculator.tierMultiplier(
                account.getTier(), rules.getSilverMultiplier(), rules.getGoldMultiplier());
        long points = PointsCalculator.calculateEarnedPoints(
                request.amountPaid(), rules.getAmountPerPoint(), multiplier);

        long newBalance = account.getPointsBalance() + points;
        account.setPointsBalance(newBalance);
        account.setLifetimeEarned(account.getLifetimeEarned() + points);
        account.setTier(resolveTier(account.getLifetimeEarned(), rules));
        accountRepository.save(account);

        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(request.userId())
                .bookingId(request.bookingId())
                .referencePaymentId(request.paymentId())
                .type(LoyaltyTransactionType.EARNED)
                .status(LoyaltyTransactionStatus.CONFIRMED)
                .points(points)
                .balanceAfter(newBalance)
                .description("Points earned for booking")
                .build());

        log.info("Points awarded: userId={}, bookingId={}, points={}, newBalance={}, tier={}",
                request.userId(), request.bookingId(), points, newBalance, account.getTier());
        return new AwardPointsResponse(points, newBalance, account.getTier().name(), false);
    }

    @Override
    @Transactional
    public RedemptionReservationResponse reserveRedemption(RedeemPointsRequest request) {
        LoyaltyRules rules = getRulesOrThrow();
        LoyaltyAccount account = lockOrCreateAccount(request.userId());

        Optional<LoyaltyTransaction> active = transactionRepository
                .findFirstByBookingIdAndTypeAndStatusInOrderByCreatedAtDesc(
                        request.bookingId(), LoyaltyTransactionType.REDEEMED, ACTIVE_STATUSES);
        if (active.isPresent()) {
            LoyaltyTransaction existing = active.get();
            if (!existing.getUserId().equals(request.userId())) {
                throw new InvalidLoyaltyRequestException("This booking already has a redemption for another user");
            }
            log.info("Reservation already exists: userId={}, bookingId={}", request.userId(), request.bookingId());
            return new RedemptionReservationResponse(
                    existing.getId(),
                    existing.getPoints(),
                    existing.getDiscountAmount(),
                    PointsCalculator.calculatePayableAmount(request.bookingTotal(), existing.getDiscountAmount()),
                    account.getPointsBalance());
        }

        BigDecimal discount = validateRedemption(rules, request.points(), request.bookingTotal(),
                account.getPointsBalance());

        long newBalance = account.getPointsBalance() - request.points();
        account.setPointsBalance(newBalance);
        accountRepository.save(account);

        LoyaltyTransaction reservation = transactionRepository.save(LoyaltyTransaction.builder()
                .userId(request.userId())
                .bookingId(request.bookingId())
                .type(LoyaltyTransactionType.REDEEMED)
                .status(LoyaltyTransactionStatus.PENDING)
                .points(request.points())
                .discountAmount(discount)
                .balanceAfter(newBalance)
                .description("Points reserved for booking payment")
                .build());

        log.info("Points reserved: userId={}, bookingId={}, points={}, discount={}, newBalance={}",
                request.userId(), request.bookingId(), request.points(), discount, newBalance);
        return new RedemptionReservationResponse(
                reservation.getId(),
                request.points(),
                discount,
                PointsCalculator.calculatePayableAmount(request.bookingTotal(), discount),
                newBalance);
    }

    @Override
    @Transactional
    public LoyaltyStatusResponse confirmRedemption(UUID bookingId) {
        Optional<LoyaltyTransaction> pending = transactionRepository.findByBookingIdAndTypeAndStatus(
                bookingId, LoyaltyTransactionType.REDEEMED, LoyaltyTransactionStatus.PENDING);
        if (pending.isPresent()) {
            LoyaltyTransaction reservation = pending.get();
            reservation.setStatus(LoyaltyTransactionStatus.CONFIRMED);
            transactionRepository.save(reservation);
            log.info("Reservation confirmed: userId={}, bookingId={}, points={}",
                    reservation.getUserId(), bookingId, reservation.getPoints());
            return new LoyaltyStatusResponse("CONFIRMED", reservation.getPoints(), 0, currentBalance(reservation.getUserId()));
        }

        Optional<LoyaltyTransaction> confirmed = transactionRepository.findByBookingIdAndTypeAndStatus(
                bookingId, LoyaltyTransactionType.REDEEMED, LoyaltyTransactionStatus.CONFIRMED);
        if (confirmed.isPresent()) {
            LoyaltyTransaction reservation = confirmed.get();
            return new LoyaltyStatusResponse("CONFIRMED", reservation.getPoints(), 0, currentBalance(reservation.getUserId()));
        }

        log.warn("Confirm found no pending reservation: bookingId={}", bookingId);
        return new LoyaltyStatusResponse("NO_ACTION", 0, 0, 0);
    }

    @Override
    @Transactional
    public LoyaltyStatusResponse releaseRedemption(UUID bookingId, String reason) {
        Optional<LoyaltyTransaction> pending = transactionRepository.findByBookingIdAndTypeAndStatus(
                bookingId, LoyaltyTransactionType.REDEEMED, LoyaltyTransactionStatus.PENDING);
        if (pending.isEmpty()) {
            log.info("Release found no pending reservation: bookingId={}", bookingId);
            return new LoyaltyStatusResponse("NO_ACTION", 0, 0, 0);
        }

        LoyaltyTransaction reservation = pending.get();
        LoyaltyAccount account = lockOrCreateAccount(reservation.getUserId());
        giveReservedPointsBack(account, reservation, normalizeReason(reason, "Reservation released"));

        log.info("Reservation released: userId={}, bookingId={}, points={}, newBalance={}",
                reservation.getUserId(), bookingId, reservation.getPoints(), account.getPointsBalance());
        return new LoyaltyStatusResponse("RELEASED", 0, reservation.getPoints(), account.getPointsBalance());
    }

    @Override
    @Transactional
    public ReverseLoyaltyResponse reverseBooking(UUID bookingId, String reason) {
        String description = normalizeReason(reason, "Booking cancelled");

        Optional<LoyaltyTransaction> redemption = transactionRepository
                .findFirstByBookingIdAndTypeAndStatusInOrderByCreatedAtDesc(
                        bookingId, LoyaltyTransactionType.REDEEMED, ACTIVE_STATUSES);
        Optional<LoyaltyTransaction> earned = transactionRepository.findByBookingIdAndTypeAndStatus(
                bookingId, LoyaltyTransactionType.EARNED, LoyaltyTransactionStatus.CONFIRMED);

        UUID userId = redemption.map(LoyaltyTransaction::getUserId)
                .or(() -> earned.map(LoyaltyTransaction::getUserId))
                .orElse(null);
        if (userId == null) {
            log.info("Reverse found nothing to do: bookingId={}", bookingId);
            return new ReverseLoyaltyResponse(0, 0, false, 0);
        }

        LoyaltyAccount account = lockOrCreateAccount(userId);

        long restored = 0;
        boolean released = false;
        if (redemption.isPresent()) {
            LoyaltyTransaction reservation = redemption.get();
            if (reservation.getStatus() == LoyaltyTransactionStatus.PENDING) {
                giveReservedPointsBack(account, reservation, description);
                released = true;
            } else {
                restored = restoreConfirmedRedemption(account, reservation, description);
            }
        }

        long reversed = 0;
        boolean alreadyReversed = transactionRepository.existsByBookingIdAndType(
                bookingId, LoyaltyTransactionType.EARN_REVERSED);
        if (earned.isPresent() && !alreadyReversed) {
            reversed = reverseEarnedPoints(account, earned.get(), description, getRulesOrThrow());
        }

        log.info("Booking reversed: userId={}, bookingId={}, earnedReversed={}, redeemedRestored={}, reservationReleased={}",
                userId, bookingId, reversed, restored, released);
        return new ReverseLoyaltyResponse(reversed, restored, released, account.getPointsBalance());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findExpiredReservationBookingIds() {
        LoyaltyRules rules = getRulesOrThrow();
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(rules.getReservationTimeoutMinutes()));
        return transactionRepository
                .findByTypeAndStatusAndCreatedAtBefore(
                        LoyaltyTransactionType.REDEEMED, LoyaltyTransactionStatus.PENDING, cutoff)
                .stream()
                .map(LoyaltyTransaction::getBookingId)
                .filter(Objects::nonNull)
                .toList();
    }

    private BigDecimal validateRedemption(LoyaltyRules rules, long points, BigDecimal bookingTotal, long balance) {
        if (points < rules.getMinRedeemPoints()) {
            throw new BelowMinimumRedemptionException(rules.getMinRedeemPoints());
        }
        if (!PointsCalculator.isMultipleOfStep(points, rules.getRedeemStep())) {
            throw new InvalidRedemptionStepException(rules.getRedeemStep());
        }
        if (points > balance) {
            throw new InsufficientPointsException(
                    "Insufficient points: you have " + balance + " but tried to redeem " + points);
        }

        BigDecimal discount = PointsCalculator.calculateDiscount(points, rules.getPointValueLkr());
        BigDecimal maxDiscount = PointsCalculator.maxDiscount(bookingTotal, rules.getMaxDiscountPercent());
        if (discount.compareTo(maxDiscount) > 0) {
            throw new RedemptionLimitExceededException(maxDiscount, rules.getMaxDiscountPercent());
        }
        return discount;
    }

    private void validateRulesRequest(UpdateLoyaltyRulesRequest request) {
        if (request.silverThreshold() >= request.goldThreshold()) {
            throw new InvalidLoyaltyRequestException("silverThreshold must be lower than goldThreshold");
        }
        if (request.goldMultiplier().compareTo(request.silverMultiplier()) < 0) {
            throw new InvalidLoyaltyRequestException(
                    "goldMultiplier must be greater than or equal to silverMultiplier");
        }
    }

    private void giveReservedPointsBack(LoyaltyAccount account, LoyaltyTransaction reservation, String description) {
        long newBalance = account.getPointsBalance() + reservation.getPoints();
        account.setPointsBalance(newBalance);
        accountRepository.save(account);

        reservation.setStatus(LoyaltyTransactionStatus.RELEASED);
        transactionRepository.save(reservation);

        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(reservation.getUserId())
                .bookingId(reservation.getBookingId())
                .type(LoyaltyTransactionType.REDEMPTION_RELEASED)
                .status(LoyaltyTransactionStatus.RELEASED)
                .points(reservation.getPoints())
                .discountAmount(reservation.getDiscountAmount())
                .balanceAfter(newBalance)
                .description(truncate(description))
                .build());
    }

    private long restoreConfirmedRedemption(LoyaltyAccount account, LoyaltyTransaction redemption, String description) {
        long newBalance = account.getPointsBalance() + redemption.getPoints();
        account.setPointsBalance(newBalance);
        accountRepository.save(account);

        redemption.setStatus(LoyaltyTransactionStatus.RELEASED);
        transactionRepository.save(redemption);

        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(redemption.getUserId())
                .bookingId(redemption.getBookingId())
                .type(LoyaltyTransactionType.REDEEM_RESTORED)
                .status(LoyaltyTransactionStatus.CONFIRMED)
                .points(redemption.getPoints())
                .discountAmount(redemption.getDiscountAmount())
                .balanceAfter(newBalance)
                .description(truncate(description))
                .build());
        return redemption.getPoints();
    }

    private long reverseEarnedPoints(LoyaltyAccount account, LoyaltyTransaction earned,
                                     String description, LoyaltyRules rules) {
        long toRemove = Math.min(earned.getPoints(), account.getPointsBalance());
        long shortfall = earned.getPoints() - toRemove;
        if (shortfall > 0) {
            log.warn("Earned points only partly reversed (already spent): userId={}, bookingId={}, shortfall={}",
                    earned.getUserId(), earned.getBookingId(), shortfall);
        }

        long newBalance = account.getPointsBalance() - toRemove;
        account.setPointsBalance(newBalance);
        account.setLifetimeEarned(Math.max(0, account.getLifetimeEarned() - earned.getPoints()));
        account.setTier(resolveTier(account.getLifetimeEarned(), rules));
        accountRepository.save(account);

        String note = shortfall > 0 ? description + " (shortfall " + shortfall + " points already spent)" : description;
        transactionRepository.save(LoyaltyTransaction.builder()
                .userId(earned.getUserId())
                .bookingId(earned.getBookingId())
                .type(LoyaltyTransactionType.EARN_REVERSED)
                .status(LoyaltyTransactionStatus.CONFIRMED)
                .points(toRemove)
                .balanceAfter(newBalance)
                .description(truncate(note))
                .build());
        return toRemove;
    }

    private LoyaltyTier resolveTier(long lifetimeEarned, LoyaltyRules rules) {
        if (lifetimeEarned >= rules.getGoldThreshold()) {
            return LoyaltyTier.GOLD;
        }
        if (lifetimeEarned >= rules.getSilverThreshold()) {
            return LoyaltyTier.SILVER;
        }
        return LoyaltyTier.BRONZE;
    }

    private LoyaltyAccount lockOrCreateAccount(UUID userId) {
        return accountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> accountRepository.save(LoyaltyAccount.builder().userId(userId).build()));
    }

    private LoyaltyRules getRulesOrThrow() {
        return rulesRepository.findTopByOrderByUpdatedAtDesc().orElseThrow(LoyaltyRulesNotFoundException::new);
    }

    private long currentBalance(UUID userId) {
        return accountRepository.findByUserId(userId).map(LoyaltyAccount::getPointsBalance).orElse(0L);
    }

    private String normalizeReason(String reason, String fallback) {
        return truncate(reason == null || reason.isBlank() ? fallback : reason.trim());
    }

    private String truncate(String text) {
        if (text == null || text.length() <= DESCRIPTION_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, DESCRIPTION_MAX_LENGTH);
    }

    private TransactionHistoryResponse toHistoryResponse(LoyaltyTransaction tx) {
        return new TransactionHistoryResponse(
                tx.getId(),
                tx.getUserId(),
                tx.getBookingId(),
                tx.getType().name(),
                tx.getStatus().name(),
                tx.getPoints(),
                tx.getDiscountAmount(),
                tx.getDescription(),
                tx.getBalanceAfter() == null ? 0L : tx.getBalanceAfter(),
                tx.getCreatedAt());
    }

    private LoyaltyRulesResponse toRulesResponse(LoyaltyRules rules) {
        return new LoyaltyRulesResponse(
                rules.getId(),
                rules.getAmountPerPoint(),
                rules.getPointValueLkr(),
                rules.getMinRedeemPoints(),
                rules.getRedeemStep(),
                rules.getMaxDiscountPercent(),
                rules.getReservationTimeoutMinutes(),
                rules.getSilverThreshold(),
                rules.getSilverMultiplier(),
                rules.getGoldThreshold(),
                rules.getGoldMultiplier(),
                rules.getUpdatedBy());
    }
}