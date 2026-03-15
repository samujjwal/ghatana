/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.domain.MonetaryAmount;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal reconciliation engine comparing ledger balances against source system totals
 * (STORY-K16-013).
 *
 * <h2>Match rules</h2>
 * <ul>
 *   <li><b>MATCHED</b> (exact): {@code |ledgerBalance - sourceBalance| == 0}.</li>
 *   <li><b>MATCHED</b> (tolerance): difference is non-zero but {@code ≤ epsilon};
 *       {@link ReconciliationItem#toleranceUsed()} is {@code true}.</li>
 *   <li><b>BREAK</b>: {@code |difference| > epsilon}. The item carries the absolute
 *       monetary difference for downstream break tracking (K16-014).</li>
 *   <li><b>PENDING_REVIEW</b>: manually set when a break requires human investigation
 *       before it can be resolved or escalated (lifecycle management in K16-014).</li>
 * </ul>
 *
 * <h2>Reconciliation request</h2>
 * <p>Callers supply the ledger-derived balance and the source-system-derived balance for each
 * (accountId, currency) pair. The engine does not fetch balances itself — the balance query
 * layer (K16-003) is a separate port.
 *
 * <h2>Multi-currency</h2>
 * <p>Each {@link ReconciliationRequest} is scoped to a single currency. Callers submit one
 * request per currency per account.
 *
 * @doc.type class
 * @doc.purpose Ledger-vs-source reconciliation engine with tolerance-based matching (K16-013)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ReconciliationService {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs reconciliation for a batch of account/currency pairs with a configurable tolerance.
     *
     * <p>A tolerance of {@code BigDecimal.ZERO} enforces exact-match-only semantics.
     * A positive tolerance (e.g. {@code 0.01}) allows minor rounding differences.
     *
     * @param requests  batch of reconciliation requests (one per account-currency pair); not null
     * @param tolerance maximum absolute difference to still classify as MATCHED; must be ≥ 0
     * @return per-request reconciliation outcomes (same order as input)
     * @throws IllegalArgumentException if tolerance is negative
     */
    public List<ReconciliationItem> reconcile(List<ReconciliationRequest> requests,
                                              BigDecimal tolerance) {
        Objects.requireNonNull(requests, "requests");
        Objects.requireNonNull(tolerance, "tolerance");
        if (tolerance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("tolerance must be >= 0, got: " + tolerance);
        }

        List<ReconciliationItem> results = new ArrayList<>(requests.size());
        for (ReconciliationRequest req : requests) {
            results.add(evaluate(req, tolerance));
        }
        return results;
    }

    /**
     * Runs reconciliation with zero tolerance (exact match only).
     *
     * @param requests batch of reconciliation requests; not null
     * @return per-request reconciliation outcomes
     */
    public List<ReconciliationItem> reconcile(List<ReconciliationRequest> requests) {
        return reconcile(requests, BigDecimal.ZERO);
    }

    // ── Private evaluation ────────────────────────────────────────────────────

    private static ReconciliationItem evaluate(ReconciliationRequest req, BigDecimal tolerance) {
        BigDecimal diff = req.ledgerBalance().subtract(req.sourceBalance()).abs();

        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return new ReconciliationItem(req, ReconciliationStatus.MATCHED, diff, false,
                    Instant.now());
        }
        if (tolerance.compareTo(BigDecimal.ZERO) > 0 && diff.compareTo(tolerance) <= 0) {
            return new ReconciliationItem(req, ReconciliationStatus.MATCHED, diff, true,
                    Instant.now());
        }
        return new ReconciliationItem(req, ReconciliationStatus.BREAK, diff, false, Instant.now());
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /**
     * Reconciliation match outcome.
     */
    public enum ReconciliationStatus {
        /**
         * Ledger balance equals source balance (within tolerance if applicable).
         */
        MATCHED,
        /**
         * Difference exceeds tolerance; downstream break tracking (K16-014) applies.
         */
        BREAK,
        /**
         * Break flagged for manual human review before resolution or escalation.
         * Set during the break lifecycle (K16-014 INVESTIGATING state).
         */
        PENDING_REVIEW
    }

    /**
     * Input request for one (accountId, currency) reconciliation pair.
     *
     * @param reconId       unique identifier for this reconciliation run item
     * @param accountId     ledger account being reconciled
     * @param currency      currency of the balance figures
     * @param ledgerBalance balance as computed by the ledger (K16-003)
     * @param sourceBalance balance as reported by the source system
     * @param reference     optional reference tag for the source (e.g. "CORE_BANKING_EOD")
     */
    public record ReconciliationRequest(
            UUID reconId,
            UUID accountId,
            Currency currency,
            BigDecimal ledgerBalance,
            BigDecimal sourceBalance,
            String reference
    ) {
        public ReconciliationRequest {
            Objects.requireNonNull(reconId, "reconId");
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(ledgerBalance, "ledgerBalance");
            Objects.requireNonNull(sourceBalance, "sourceBalance");
        }

        /** Creates a request with an auto-generated reconId and no reference tag. */
        public static ReconciliationRequest of(UUID accountId, Currency currency,
                                               BigDecimal ledgerBalance,
                                               BigDecimal sourceBalance) {
            return new ReconciliationRequest(UUID.randomUUID(), accountId, currency,
                    ledgerBalance, sourceBalance, null);
        }
    }

    /**
     * Result of reconciling one (accountId, currency) pair.
     *
     * @param request       the original reconciliation request
     * @param status        match outcome — MATCHED, BREAK, or PENDING_REVIEW
     * @param difference    absolute monetary difference between ledger and source balance
     * @param toleranceUsed true when the match was within tolerance (not an exact zero diff)
     * @param reconAt       timestamp when reconciliation was run
     */
    public record ReconciliationItem(
            ReconciliationRequest request,
            ReconciliationStatus status,
            BigDecimal difference,
            boolean toleranceUsed,
            Instant reconAt
    ) {
        public ReconciliationItem {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(difference, "difference");
            Objects.requireNonNull(reconAt, "reconAt");
        }

        /** Convenience: true if the recon outcome is a break requiring investigation. */
        public boolean isBreak() {
            return status == ReconciliationStatus.BREAK
                    || status == ReconciliationStatus.PENDING_REVIEW;
        }
    }
}
