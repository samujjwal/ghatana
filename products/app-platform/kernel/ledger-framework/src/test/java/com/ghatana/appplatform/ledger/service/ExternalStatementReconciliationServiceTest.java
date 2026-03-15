/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.service.ExternalStatementReconciliationService.LedgerEntry;
import com.ghatana.appplatform.ledger.service.ExternalStatementReconciliationService.MatchStatus;
import com.ghatana.appplatform.ledger.service.ExternalStatementReconciliationService.ReconciliationReport;
import com.ghatana.appplatform.ledger.service.ExternalStatementReconciliationService.StatementLine;
import com.ghatana.appplatform.ledger.service.ExternalStatementReconciliationService.StatementMatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ExternalStatementReconciliationService} (STORY-K16-015).
 *
 * <p>Tests cover CSV import, MT940 import, exact/date-tolerance matching,
 * unmatched item flagging, and three-way match scenarios.
 */
@DisplayName("ExternalStatementReconciliationService — K16-015 external statement reconciliation")
class ExternalStatementReconciliationServiceTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    private ExternalStatementReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ExternalStatementReconciliationService();
    }

    // ── AC1: CSV import ───────────────────────────────────────────────────────

    @Test
    @DisplayName("externalRecon_csvImport — parses CSV and returns correct statement lines")
    void externalRecon_csvImport() {
        String csv = """
                lineId,amount,currency,date,reference,description
                LINE-001,1000.00,NPR,2026-03-15,PAY-001,Salary payment
                LINE-002,250.50,NPR,2026-03-14,PAY-002,Bonus
                """;

        List<StatementLine> lines = service.parseCsv(csv);

        assertThat(lines).hasSize(2);

        StatementLine first = lines.get(0);
        assertThat(first.lineId()).isEqualTo("LINE-001");
        assertThat(first.amount()).isEqualByComparingTo("1000.00");
        assertThat(first.currency()).isEqualTo(Currency.NPR);
        assertThat(first.valueDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(first.reference()).isEqualTo("PAY-001");

        StatementLine second = lines.get(1);
        assertThat(second.amount()).isEqualByComparingTo("250.50");
        assertThat(second.reference()).isEqualTo("PAY-002");
    }

    @Test
    @DisplayName("externalRecon_csvImport_autoId — blank lineId auto-generated as UUID")
    void externalRecon_csvImport_autoId() {
        String csv = """
                lineId,amount,currency,date,reference,description
                ,500.00,NPR,2026-03-15,,
                """;

        List<StatementLine> lines = service.parseCsv(csv);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).lineId()).isNotBlank();
    }

    @Test
    @DisplayName("externalRecon_csvImport_missingColumn — missing required column throws")
    void externalRecon_csvImport_missingColumn() {
        String csvBadHeader = "lineId,currency,date\nL1,NPR,2026-03-15";

        assertThatThrownBy(() -> service.parseCsv(csvBadHeader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    // ── AC2: MT940 import ─────────────────────────────────────────────────────

    @Test
    @DisplayName("externalRecon_mt940 — parses MT940 :61: transaction lines")
    void externalRecon_mt940() {
        // :61: format: YYMMDD (6) + MMDD entry date (4) + CR/DR (2) + amount,decimal // REFERENCE
        // YY=26 → 2026, so 260315 = 2026-03-15
        String mt940 = """
                :20:STMT001
                :25:ACCOUNT/1234567890
                :28C:00001/001
                :60F:C260315NPR10000,00
                :61:2603150315CR2500,00//PAY-001
                :61:2603140314DR500,00//REFUND-02
                :62F:C260315NPR12000,00
                """;

        List<StatementLine> lines = service.parseMt940(mt940, ACCOUNT_ID);

        assertThat(lines).hasSize(2);

        StatementLine credit = lines.get(0);
        assertThat(credit.amount()).isEqualByComparingTo("2500.00");
        assertThat(credit.currency()).isEqualTo(Currency.NPR);
        assertThat(credit.valueDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(credit.reference()).isEqualTo("PAY-001");
        assertThat(credit.description()).isEqualTo("CREDIT");

        StatementLine debit = lines.get(1);
        assertThat(debit.amount()).isEqualByComparingTo("500.00");
        assertThat(debit.description()).isEqualTo("DEBIT");
    }

    // ── AC3: matching — exact match ───────────────────────────────────────────

    @Test
    @DisplayName("externalRecon_matching — exact amount+date+reference → MATCHED")
    void externalRecon_matching() {
        StatementLine stmtLine = new StatementLine(
                "L1", new BigDecimal("1000.00"), Currency.NPR, TODAY, "PAY-001", "Salary");

        LedgerEntry ledgerEntry = new LedgerEntry(
                UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("1000.00"), TODAY, "PAY-001");

        ReconciliationReport report = service.reconcile(List.of(stmtLine), List.of(ledgerEntry));

        assertThat(report.matchedCount()).isEqualTo(1);
        assertThat(report.unmatchedStatementCount()).isEqualTo(0);
        assertThat(report.unmatchedLedgerEntries()).isEmpty();
        assertThat(report.matchResults().get(0).status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(report.matchResults().get(0).matchedEntryId()).isNotNull();
    }

    @Test
    @DisplayName("externalRecon_matching_dateTolerance — ±1 day tolerance on value date → MATCHED")
    void externalRecon_matching_dateTolerance() {
        StatementLine stmtLine = new StatementLine(
                "L1", new BigDecimal("500.00"), Currency.NPR,
                TODAY.plusDays(1), "REF-X", null);

        LedgerEntry ledgerEntry = new LedgerEntry(
                UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("500.00"), TODAY, "REF-X");

        ReconciliationReport report = service.reconcile(List.of(stmtLine), List.of(ledgerEntry));

        assertThat(report.matchedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("externalRecon_matching_noReference — no reference on either side → match by amount+date")
    void externalRecon_matching_noReference() {
        StatementLine stmtLine = new StatementLine(
                "L1", new BigDecimal("300.00"), Currency.NPR, TODAY, null, "Payment");

        LedgerEntry ledgerEntry = new LedgerEntry(
                UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("300.00"), TODAY, null);

        ReconciliationReport report = service.reconcile(List.of(stmtLine), List.of(ledgerEntry));

        assertThat(report.matchedCount()).isEqualTo(1);
    }

    // ── AC4: unmatched items flagged ──────────────────────────────────────────

    @Test
    @DisplayName("externalRecon_unmatched_flagged — no matching ledger entry → UNMATCHED")
    void externalRecon_unmatched_flagged() {
        StatementLine stmtLine = new StatementLine(
                "L1", new BigDecimal("999.00"), Currency.NPR, TODAY, "GHOST-REF", null);

        LedgerEntry ledgerEntry = new LedgerEntry(
                UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("123.00"), TODAY, "DIFFERENT");

        ReconciliationReport report = service.reconcile(List.of(stmtLine), List.of(ledgerEntry));

        assertThat(report.unmatchedStatementCount()).isEqualTo(1);
        assertThat(report.unmatchedLedgerEntries()).hasSize(1);

        StatementMatchResult result = report.matchResults().get(0);
        assertThat(result.status()).isEqualTo(MatchStatus.UNMATCHED);
        assertThat(result.matchedEntryId()).isNull();
    }

    @Test
    @DisplayName("externalRecon_unmatched_dateTooFar — date difference > 1 day → UNMATCHED")
    void externalRecon_unmatched_dateTooFar() {
        StatementLine stmtLine = new StatementLine(
                "L1", new BigDecimal("200.00"), Currency.NPR, TODAY, "REF-1", null);

        LedgerEntry ledgerEntry = new LedgerEntry(
                UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("200.00"), TODAY.minusDays(2), "REF-1");

        ReconciliationReport report = service.reconcile(List.of(stmtLine), List.of(ledgerEntry));

        assertThat(report.unmatchedStatementCount()).isEqualTo(1);
    }

    // ── AC5: three-way match ──────────────────────────────────────────────────

    @Test
    @DisplayName("externalRecon_threeWayMatch — multiple statement lines matched to distinct ledger entries")
    void externalRecon_threeWayMatch() {
        StatementLine s1 = new StatementLine("L1", new BigDecimal("1000.00"), Currency.NPR,
                TODAY, "PAY-001", null);
        StatementLine s2 = new StatementLine("L2", new BigDecimal("250.00"), Currency.NPR,
                TODAY, "PAY-002", null);
        StatementLine s3 = new StatementLine("L3", new BigDecimal("750.00"), Currency.NPR,
                TODAY.minusDays(1), "PAY-003", null);

        LedgerEntry e1 = new LedgerEntry(UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("1000.00"), TODAY, "PAY-001");
        LedgerEntry e2 = new LedgerEntry(UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("250.00"), TODAY, "PAY-002");
        LedgerEntry e3 = new LedgerEntry(UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("750.00"), TODAY, "PAY-003");

        ReconciliationReport report = service.reconcile(List.of(s1, s2, s3), List.of(e1, e2, e3));

        assertThat(report.matchedCount()).isEqualTo(3);
        assertThat(report.unmatchedStatementCount()).isEqualTo(0);
        assertThat(report.unmatchedLedgerEntries()).isEmpty();

        // Each statement line should be matched to a distinct ledger entry
        List<UUID> matchedIds = report.matchResults().stream()
                .map(StatementMatchResult::matchedEntryId)
                .toList();
        assertThat(matchedIds).doesNotHaveDuplicates();
        assertThat(matchedIds).containsExactlyInAnyOrder(e1.entryId(), e2.entryId(), e3.entryId());
    }

    @Test
    @DisplayName("externalRecon_threeWayMatch_partialUnmatched — some lines unmatched, some ledger entries orphaned")
    void externalRecon_threeWayMatch_partialUnmatched() {
        StatementLine matched = new StatementLine("L1", new BigDecimal("500.00"), Currency.NPR,
                TODAY, "REF-M", null);
        StatementLine unmatched = new StatementLine("L2", new BigDecimal("999.99"), Currency.NPR,
                TODAY, "REF-X", null);

        LedgerEntry matchedEntry = new LedgerEntry(UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("500.00"), TODAY, "REF-M");
        LedgerEntry orphanEntry = new LedgerEntry(UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("123.00"), TODAY, "REF-O");

        ReconciliationReport report = service.reconcile(
                List.of(matched, unmatched),
                List.of(matchedEntry, orphanEntry));

        assertThat(report.matchedCount()).isEqualTo(1);
        assertThat(report.unmatchedStatementCount()).isEqualTo(1);
        assertThat(report.unmatchedLedgerEntries()).containsExactly(orphanEntry);
    }

    // ── AC6: one-to-one — ledger entry not reused ─────────────────────────────

    @Test
    @DisplayName("externalRecon_oneToOne — same amount appears twice; each match consumes one entry")
    void externalRecon_oneToOne() {
        StatementLine s1 = new StatementLine("L1", new BigDecimal("100.00"), Currency.NPR,
                TODAY, null, null);
        StatementLine s2 = new StatementLine("L2", new BigDecimal("100.00"), Currency.NPR,
                TODAY, null, null);

        LedgerEntry e1 = new LedgerEntry(UUID.randomUUID(), ACCOUNT_ID, Currency.NPR,
                new BigDecimal("100.00"), TODAY, null);
        // Only one ledger entry for two statement lines
        ReconciliationReport report = service.reconcile(List.of(s1, s2), List.of(e1));

        assertThat(report.matchedCount()).isEqualTo(1);
        assertThat(report.unmatchedStatementCount()).isEqualTo(1);
    }
}
