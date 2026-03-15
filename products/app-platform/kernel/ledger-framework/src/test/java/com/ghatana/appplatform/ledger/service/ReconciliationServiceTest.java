/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.service.ReconciliationService.ReconciliationItem;
import com.ghatana.appplatform.ledger.service.ReconciliationService.ReconciliationRequest;
import com.ghatana.appplatform.ledger.service.ReconciliationService.ReconciliationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ReconciliationService} (STORY-K16-013).
 *
 * <p>Tests verify exact match, tolerance match, break detection, multi-currency,
 * and multi-account reconciliation.
 */
@DisplayName("ReconciliationService — K16-013 internal reconciliation engine")
class ReconciliationServiceTest {

    private static final UUID CASH_ACC   = UUID.randomUUID();
    private static final UUID EQUITY_ACC = UUID.randomUUID();
    private static final UUID SEC_ACC    = UUID.randomUUID();

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService();
    }

    // ── AC1: Exact match → MATCHED ────────────────────────────────────────────

    @Test
    @DisplayName("recon_exactMatch — Ledger and source identical → MATCHED, no tolerance flag")
    void recon_exactMatch() {
        var req = ReconciliationRequest.of(CASH_ACC, Currency.NPR,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"));

        List<ReconciliationItem> results = service.reconcile(List.of(req));

        assertThat(results).hasSize(1);
        ReconciliationItem item = results.get(0);
        assertThat(item.status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(item.toleranceUsed()).isFalse();
        assertThat(item.difference()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("recon_exactMatch_zeroBalance — Both zero → MATCHED")
    void recon_exactMatch_zeroBalance() {
        var req = ReconciliationRequest.of(CASH_ACC, Currency.USD,
                BigDecimal.ZERO, BigDecimal.ZERO);

        ReconciliationItem item = service.reconcile(List.of(req)).get(0);

        assertThat(item.status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(item.toleranceUsed()).isFalse();
    }

    // ── AC2: Within tolerance → MATCHED with toleranceUsed=true ──────────────

    @Test
    @DisplayName("recon_toleranceMatch — Difference ≤ ε=0.01 → MATCHED with tolerance_used")
    void recon_toleranceMatch() {
        var req = ReconciliationRequest.of(CASH_ACC, Currency.NPR,
                new BigDecimal("1000.01"), new BigDecimal("1000.00"));

        List<ReconciliationItem> results = service.reconcile(
                List.of(req), new BigDecimal("0.01"));

        ReconciliationItem item = results.get(0);
        assertThat(item.status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(item.toleranceUsed()).isTrue();
        assertThat(item.difference()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("recon_toleranceMatch_exactlyAtBoundary — Diff == ε → MATCHED")
    void recon_toleranceMatch_exactlyAtBoundary() {
        var req = ReconciliationRequest.of(CASH_ACC, Currency.NPR,
                new BigDecimal("500.50"), new BigDecimal("500.00"));

        ReconciliationItem item = service.reconcile(
                List.of(req), new BigDecimal("0.50")).get(0);

        assertThat(item.status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(item.toleranceUsed()).isTrue();
    }

    // ── AC3: Exceeds tolerance → BREAK ────────────────────────────────────────

    @Test
    @DisplayName("recon_break_detected — Difference > ε → BREAK with amount difference")
    void recon_break_detected() {
        var req = ReconciliationRequest.of(CASH_ACC, Currency.NPR,
                new BigDecimal("1005.00"), new BigDecimal("1000.00"));

        List<ReconciliationItem> results = service.reconcile(
                List.of(req), new BigDecimal("0.01"));

        ReconciliationItem item = results.get(0);
        assertThat(item.status()).isEqualTo(ReconciliationStatus.BREAK);
        assertThat(item.toleranceUsed()).isFalse();
        assertThat(item.difference()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(item.isBreak()).isTrue();
    }

    @Test
    @DisplayName("recon_break_noToleranceConfigured — Non-zero diff with ε=0 → BREAK")
    void recon_break_noToleranceConfigured() {
        var req = ReconciliationRequest.of(CASH_ACC, Currency.USD,
                new BigDecimal("100.01"), new BigDecimal("100.00"));

        ReconciliationItem item = service.reconcile(List.of(req)).get(0);

        assertThat(item.status()).isEqualTo(ReconciliationStatus.BREAK);
        assertThat(item.difference()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    // ── Multi-currency reconciliation ─────────────────────────────────────────

    @Test
    @DisplayName("recon_multiCurrency — NPR and USD pairs reconciled independently")
    void recon_multiCurrency() {
        var nprReq = ReconciliationRequest.of(CASH_ACC, Currency.NPR,
                new BigDecimal("50000.00"), new BigDecimal("50000.00"));
        var usdReq = ReconciliationRequest.of(CASH_ACC, Currency.USD,
                new BigDecimal("375.01"), new BigDecimal("375.00"));

        List<ReconciliationItem> results = service.reconcile(
                List.of(nprReq, usdReq), new BigDecimal("0.01"));

        assertThat(results).hasSize(2);
        // NPR: exact match
        assertThat(results.get(0).status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(results.get(0).toleranceUsed()).isFalse();
        // USD: within 0.01 tolerance → MATCHED with tolerance
        assertThat(results.get(1).status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(results.get(1).toleranceUsed()).isTrue();
    }

    // ── Multi-account reconciliation ──────────────────────────────────────────

    @Test
    @DisplayName("recon_multiAccount — Multiple accounts show mixed MATCHED/BREAK results")
    void recon_multiAccount() {
        var cashMatch = ReconciliationRequest.of(CASH_ACC, Currency.NPR,
                new BigDecimal("100000.00"), new BigDecimal("100000.00"));
        var equityBreak = ReconciliationRequest.of(EQUITY_ACC, Currency.NPR,
                new BigDecimal("200000.00"), new BigDecimal("199900.00")); // 100 difference
        var secMatch = ReconciliationRequest.of(SEC_ACC, Currency.NPR,
                new BigDecimal("50000.00"), new BigDecimal("50000.00"));

        List<ReconciliationItem> results = service.reconcile(
                List.of(cashMatch, equityBreak, secMatch), new BigDecimal("0.01"));

        assertThat(results).hasSize(3);
        assertThat(results.get(0).status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(results.get(1).status()).isEqualTo(ReconciliationStatus.BREAK);
        assertThat(results.get(1).difference()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(results.get(2).status()).isEqualTo(ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("recon_multiAccount_allBreaks — All accounts mismatched → all BREAK")
    void recon_multiAccount_allBreaks() {
        var req1 = ReconciliationRequest.of(CASH_ACC, Currency.NPR,
                new BigDecimal("1000.00"), new BigDecimal("900.00"));
        var req2 = ReconciliationRequest.of(EQUITY_ACC, Currency.NPR,
                new BigDecimal("2000.00"), new BigDecimal("1500.00"));

        List<ReconciliationItem> results = service.reconcile(List.of(req1, req2));

        assertThat(results).allMatch(ReconciliationItem::isBreak);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recon_negativeTolerance_rejected — Negative tolerance throws")
    void recon_negativeTolerance_rejected() {
        var req = ReconciliationRequest.of(CASH_ACC, Currency.NPR,
                BigDecimal.ONE, BigDecimal.ONE);

        assertThatThrownBy(() -> service.reconcile(List.of(req), new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tolerance must be >= 0");
    }

    @Test
    @DisplayName("recon_pendingReview_isBreak — PENDING_REVIEW treated as break")
    void recon_pendingReview_isBreak() {
        // PENDING_REVIEW is set externally (lifecycle management K16-014)
        // Verify the ReconciliationItem.isBreak() helper covers PENDING_REVIEW
        var req = ReconciliationRequest.of(CASH_ACC, Currency.NPR, BigDecimal.ZERO, BigDecimal.ZERO);
        var item = new ReconciliationItem(req, ReconciliationStatus.PENDING_REVIEW,
                BigDecimal.ZERO, false, java.time.Instant.now());

        assertThat(item.isBreak()).isTrue();
    }
}
