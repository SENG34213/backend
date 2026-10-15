package com.gamingcastle.paymentservice.controller;

import com.gamingcastle.paymentservice.dto.request.PaymentRequest;
import com.gamingcastle.paymentservice.dto.response.AdminPaymentStatsResponse;
import com.gamingcastle.paymentservice.dto.response.PaymentResponse;
import com.gamingcastle.paymentservice.entity.PaymentMethod;
import com.gamingcastle.paymentservice.entity.PaymentStatus;
import com.gamingcastle.paymentservice.entity.ReferenceType;
import com.gamingcastle.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final ZoneId SLST = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter SLST_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(SLST);

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        PaymentResponse response = paymentService.processPayment(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/cash")
    public ResponseEntity<PaymentResponse> processCashPayment(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse response = paymentService.processCashPayment(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<PaymentResponse> getPaymentReceipt(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        String role = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .orElse("");

        PaymentResponse response = paymentService.getPaymentReceipt(id, userId, role);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<PaymentResponse>> getMyPayments(
            @RequestHeader("X-User-Id") UUID userId) {

        List<PaymentResponse> payments = paymentService.getMyPayments(userId);
        return ResponseEntity.ok(payments);
    }

    // ── G7: Admin paged payment log ───────────────────────────────────────────

    /**
     * GET /api/payments/admin/all
     * <p>
     * All params are optional filters:
     *   status=SUCCESS|FAILED|PENDING
     *   method=CARD|ONLINE|CASH
     *   referenceType=BOOKING|TOURNAMENT
     *   userId=&lt;uuid&gt;
     *   from / to  ISO-8601 timestamps (e.g. 2026-10-01T00:00:00Z)
     *   page, size, sort  (default: createdAt,desc; max size 100)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/all")
    public ResponseEntity<Page<PaymentResponse>> getAdminPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) ReferenceType referenceType,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        size = Math.min(size, 100);
        String[] parts = sort.split(",");
        Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, parts[0]));

        return ResponseEntity.ok(
                paymentService.getAdminPayments(status, method, referenceType, userId, from, to, pageable));
    }

    // ── G11: Admin dashboard payment stats ───────────────────────────────────

    /**
     * GET /api/payments/admin/stats?trendDays=30
     * <p>
     * Returns: todayRevenue, monthRevenue, totalPaymentsSuccess,
     * totalPaymentsFailed, revenueTrend (daily totals for last trendDays days).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/stats")
    public ResponseEntity<AdminPaymentStatsResponse> getPaymentStats(
            @RequestParam(defaultValue = "30") int trendDays) {

        return ResponseEntity.ok(
                paymentService.getPaymentStats(Math.max(1, Math.min(trendDays, 365))));
    }

    // ── G13: CSV export of payments ──────────────────────────────────────────

    /**
     * GET /api/payments/admin/export.csv
     * <p>
     * Same optional filters as /admin/all. Returns a CSV file, max 10,000 rows.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/admin/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportPaymentsCsv(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) ReferenceType referenceType,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        Pageable pageable = PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentResponse> data = paymentService.getAdminPayments(
                status, method, referenceType, userId, from, to, pageable);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("id,userId,referenceType,referenceId,amount,originalAmount," +
                   "loyaltyPointsUsed,loyaltyDiscount,method,status,receiptNumber,createdAt");

        for (PaymentResponse p : data.getContent()) {
            pw.printf("%s,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s%n",
                    p.getId(), p.getUserId(), p.getReferenceType(), p.getReferenceId(),
                    p.getAmount(), p.getOriginalAmount(),
                    p.getLoyaltyPointsUsed() == null ? 0 : p.getLoyaltyPointsUsed(),
                    p.getLoyaltyDiscount(),
                    p.getMethod(), p.getStatus(), p.getReceiptNumber(),
                    p.getCreatedAt() == null ? "" : SLST_FMT.format(p.getCreatedAt()));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payments-export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(sw.toString());
    }
}