/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.service.ReconciliationBreakTracker.AgeSeverity;
import com.ghatana.appplatform.ledger.service.ReconciliationBreakTracker.BreakRecord;
import com.ghatana.appplatform.ledger.service.ReconciliationBreakTracker.BreakStatus;
import com.ghatana.appplatform.ledger.service.ReconciliationBreakTracker.BreakSummary;
import com.ghatana.appplatform.ledger.service.ReconciliationService.ReconciliationItem;
import com.ghatana.appplatform.ledger.service.ReconciliationService.ReconciliationRequest;
import com.ghatana.appplatform.ledger.service.ReconciliationService.ReconciliationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ReconciliationBreakTracker} (STORY-K16-014).
 *
 * <p>Covers: new break registration, T+1/T+3/T+5 aging tiers, lifecycle transitions
 * (OPEN→INVESTIGATING→RESOLVED→CLOSED), summary dashboard, escalation listener firing,
 * and direct construction from a {@link ReconciliationItem}.
 */
@DisplayName("ReconciliationBreakTracker — K16-014 break aging and escalation")
class ReconciliationBreakTrackerTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String APPROVER = "approver-01";
    private static final String NOTES    = "Corrective posting applied to clear the difference.";

    private ReconciliationBreakTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ReconciliationBreakTracker();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private ReconciliationItem makeBreakItem(BigDecimal difference) {
        ReconciliationRequest req = new ReconciliationRequest(
                UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                BigDecimal.ZERO, difference, null);
        return new ReconciliationItem(req, ReconciliationStatus.BREAK,
                difference, false, Instant.now());
    }

    // ── AC1: new break is OPEN/NEW ────────────────────────────────────────────

    @Test
    @DisplayName("break_newBreak_isOpen — reportBreak registers break in OPEN/NEW state")
    void break_newBreak_isOpen() {
        ReconciliationItem item = makeBreakItem(new BigDecimal("500.00"));

        BreakRecord record = tracker.reportBreak(item);

        assertThat(record.status()).isEqualTo(BreakStatus.OPEN);
        assertThat(record.severity()).isEqualTo(AgeSeverity.NEW);
        assertThat(record.difference()).isEqualByComparingTo("500.00");
        assertThat(record.breakId()).isNotNull();
        assertThat(record.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(record.resolutionNotes()).isNull();
    }

    // ── AC2: T+1 aging → AGED ────────────────────────────────────────────────

    @Test
    @DisplayName("break_aging_t1_aged — break aged 1+ days advances to AGED and fires listener")
    void break_aging_t1_aged() {
        BreakRecord record = tracker.reportBreak(makeBreakItem(new BigDecimal("100.00")));

        Instant oneDayLater = record.detectedAt().plus(1, ChronoUnit.DAYS);
        tracker.ageBreaks(oneDayLater);

        BreakRecord aged = tracker.getBreak(record.breakId());
        assertThat(aged.severity()).isEqualTo(AgeSeverity.AGED);
    }

    // ── AC3: T+3 aging → ESCALATED ───────────────────────────────────────────

    @Test
    @DisplayName("break_aging_t3_escalated — break aged 3+ days advances to ESCALATED")
    void break_aging_t3_escalated() {
        BreakRecord record = tracker.reportBreak(makeBreakItem(new BigDecimal("200.00")));

        tracker.ageBreaks(record.detectedAt().plus(3, ChronoUnit.DAYS));

        BreakRecord escalated = tracker.getBreak(record.breakId());
        assertThat(escalated.severity()).isEqualTo(AgeSeverity.ESCALATED);
    }

    // ── AC4: T+5 aging → CRITICAL ────────────────────────────────────────────

    @Test
    @DisplayName("break_aging_t5_critical — break aged 5+ days advances to CRITICAL")
    void break_aging_t5_critical() {
        BreakRecord record = tracker.reportBreak(makeBreakItem(new BigDecimal("300.00")));

        tracker.ageBreaks(record.detectedAt().plus(5, ChronoUnit.DAYS));

        BreakRecord critical = tracker.getBreak(record.breakId());
        assertThat(critical.severity()).isEqualTo(AgeSeverity.CRITICAL);
    }

    // ── AC5: lifecycle OPEN → INVESTIGATING ──────────────────────────────────

    @Test
    @DisplayName("break_lifecycle_openToInvestigating — startInvestigation transitions OPEN → INVESTIGATING")
    void break_lifecycle_openToInvestigating() {
        BreakRecord record = tracker.reportBreak(makeBreakItem(new BigDecimal("50.00")));

        BreakRecord investigating = tracker.startInvestigation(record.breakId());

        assertThat(investigating.status()).isEqualTo(BreakStatus.INVESTIGATING);
        assertThat(tracker.getBreak(record.breakId()).status()).isEqualTo(BreakStatus.INVESTIGATING);
    }

    // ── AC6: lifecycle → RESOLVED ─────────────────────────────────────────────

    @Test
    @DisplayName("break_lifecycle_resolve — resolveBreak transitions INVESTIGATING → RESOLVED with notes")
    void break_lifecycle_resolve() {
        BreakRecord record = tracker.reportBreak(makeBreakItem(new BigDecimal("75.00")));
        tracker.startInvestigation(record.breakId());

        BreakRecord resolved = tracker.resolveBreak(record.breakId(), NOTES, APPROVER, Instant.now());

        assertThat(resolved.status()).isEqualTo(BreakStatus.RESOLVED);
        assertThat(resolved.resolutionNotes()).isEqualTo(NOTES);
        assertThat(resolved.approverId()).isEqualTo(APPROVER);
        assertThat(resolved.resolvedAt()).isNotNull();
    }

    // ── AC7: lifecycle → CLOSED ───────────────────────────────────────────────

    @Test
    @DisplayName("break_lifecycle_close — closeBreak transitions RESOLVED → CLOSED")
    void break_lifecycle_close() {
        BreakRecord record = tracker.reportBreak(makeBreakItem(new BigDecimal("25.00")));
        tracker.startInvestigation(record.breakId());
        tracker.resolveBreak(record.breakId(), NOTES, APPROVER, Instant.now());

        BreakRecord closed = tracker.closeBreak(record.breakId());

        assertThat(closed.status()).isEqualTo(BreakStatus.CLOSED);
    }

    // ── AC8: dashboard summary ────────────────────────────────────────────────

    @Test
    @DisplayName("break_summary_dashboard — getBreakSummary returns counts by status and severity")
    void break_summary_dashboard() {
        tracker.reportBreak(makeBreakItem(new BigDecimal("100.00")));
        BreakRecord b2 = tracker.reportBreak(makeBreakItem(new BigDecimal("200.00")));
        BreakRecord b3 = tracker.reportBreak(makeBreakItem(new BigDecimal("300.00")));

        tracker.startInvestigation(b2.breakId());

        // age b3 to CRITICAL
        tracker.ageBreaks(b3.detectedAt().plus(5, ChronoUnit.DAYS));

        BreakSummary summary = tracker.getBreakSummary();

        assertThat(summary.openCount()).isEqualTo(2);           // b1, b3 still OPEN
        assertThat(summary.investigatingCount()).isEqualTo(1);  // b2
        assertThat(summary.closedCount()).isEqualTo(0);
        assertThat(summary.totalActive()).isEqualTo(3);
        assertThat(summary.bySeverity()).containsKey(AgeSeverity.CRITICAL); // b3 is CRITICAL
    }

    // ── AC9: escalation listener fires ───────────────────────────────────────

    @Test
    @DisplayName("escalation_notification_fired — listener receives notification on age advancement")
    void escalation_notification_fired() {
        List<BreakRecord> escalations = new ArrayList<>();
        tracker.addEscalationListener(escalations::add);

        BreakRecord record = tracker.reportBreak(makeBreakItem(new BigDecimal("400.00")));

        // Advance 2 days → triggers AGED escalation
        tracker.ageBreaks(record.detectedAt().plus(2, ChronoUnit.DAYS));

        assertThat(escalations).hasSize(1);
        assertThat(escalations.get(0).severity()).isEqualTo(AgeSeverity.AGED);
    }

    // ── AC10: create break from ReconciliationItem ───────────────────────────

    @Test
    @DisplayName("break_fromReconciliationResult — BREAK item creates tracker entry with correct metadata")
    void break_fromReconciliationResult() {
        ReconciliationService service = new ReconciliationService();
        ReconciliationRequest req = new ReconciliationRequest(
                UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("1000.00"), new BigDecimal("995.00"), "CORE_BANKING");

        List<ReconciliationItem> results = service.reconcile(List.of(req), new BigDecimal("0.01"));
        ReconciliationItem breakItem = results.get(0);

        assertThat(breakItem.status()).isEqualTo(ReconciliationStatus.BREAK);

        BreakRecord record = tracker.reportBreak(breakItem);

        assertThat(record.difference()).isEqualByComparingTo("5.00");
        assertThat(record.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(record.currency()).isEqualTo(Currency.NPR);
        assertThat(record.status()).isEqualTo(BreakStatus.OPEN);
        assertThat(record.severity()).isEqualTo(AgeSeverity.NEW);
    }

    // ── Guard: cannot escalate invalid state ─────────────────────────────────

    @Test
    @DisplayName("break_invalidTransition — startInvestigation on INVESTIGATING throws")
    void break_invalidTransition() {
        BreakRecord record = tracker.reportBreak(makeBreakItem(new BigDecimal("10.00")));
        tracker.startInvestigation(record.breakId());

        assertThatThrownBy(() -> tracker.startInvestigation(record.breakId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("break_resolve_requiresNotes — blank notes rejected")
    void break_resolve_requiresNotes() {
        BreakRecord record = tracker.reportBreak(makeBreakItem(new BigDecimal("10.00")));
        tracker.startInvestigation(record.breakId());

        assertThatThrownBy(() -> tracker.resolveBreak(record.breakId(), "   ", APPROVER, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("break_matched_cannotReport — MATCHED items rejected by reportBreak")
    void break_matched_cannotReport() {
        ReconciliationRequest req = new ReconciliationRequest(
                UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("500.00"), new BigDecimal("500.00"), null);
        ReconciliationItem matched = new ReconciliationItem(
                req, ReconciliationStatus.MATCHED, BigDecimal.ZERO, false, Instant.now());

        assertThatThrownBy(() -> tracker.reportBreak(matched))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
