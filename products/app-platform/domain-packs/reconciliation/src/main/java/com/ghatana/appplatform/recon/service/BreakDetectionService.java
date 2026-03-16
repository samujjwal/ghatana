package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose After matching runs complete, unmatched items are classified as reconciliation
 *              breaks. Break types: TIMING_DIFFERENCE, MISSING_ENTRY, UNEXPECTED_ENTRY,
 *              AMOUNT_MISMATCH, DUPLICATE. Severity: LOW/MEDIUM/HIGH/CRITICAL based on amount
 *              and age. Auto-classification based on patterns and heuristics.
 *              Satisfies STORY-D13-010.
 * @doc.layer   Domain
 * @doc.pattern Post-matching classification; amount + age based severity; INSERT-only break
 *              table; ON CONFLICT idempotency.
 */
public class BreakDetectionService {

    private static final Logger log = LoggerFactory.getLogger(BreakDetectionService.class);

    private static final BigDecimal CRITICAL_AMOUNT = new BigDecimal("1000000");
    private static final BigDecimal HIGH_AMOUNT     = new BigDecimal("100000");
    private static final BigDecimal MEDIUM_AMOUNT   = new BigDecimal("10000");

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Counter          breakCounter;

    public BreakDetectionService(HikariDataSource dataSource, Executor executor,
                                 MeterRegistry registry) {
        this.dataSource  = dataSource;
        this.executor    = executor;
        this.breakCounter = registry.counter("recon.breaks.detected");
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ReconBreak(String breakId, String reconRunId, LocalDate detectedDate,
                             String entryId, String entrySource, // STATEMENT or INTERNAL
                             BigDecimal amount, String currency, String breakType,
                             String severity, String clientId) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Called after all matching passes; classifies all remaining unmatched entries as breaks. */
    public Promise<Integer> classifyBreaks(String reconRunId, LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> {
            int total = 0;
            try (Connection conn = dataSource.getConnection()) {
                total += classifyMissingEntries(conn, reconRunId, runDate);
                total += classifyUnexpectedEntries(conn, reconRunId, runDate);
                total += classifyAmountMismatches(conn, reconRunId, runDate);
                total += classifyDuplicates(conn, reconRunId, runDate);
            }
            return total;
        });
    }

    // ─── Private classification methods ───────────────────────────────────────

    /** Internal present, external absent → MISSING_ENTRY */
    private int classifyMissingEntries(Connection conn, String reconRunId, LocalDate runDate)
            throws SQLException {
        String sql = """
                INSERT INTO recon_breaks
                    (break_id, recon_run_id, detected_date, entry_id, entry_source,
                     amount, currency, break_type, severity, client_id)
                SELECT gen_random_uuid(), ?, ?, it.tx_id, 'INTERNAL',
                       it.amount, it.currency,
                       CASE WHEN it.transaction_date < ? - INTERVAL '1 day'
                            THEN 'TIMING_DIFFERENCE' ELSE 'MISSING_ENTRY' END,
                       CASE WHEN it.amount >= ? THEN 'CRITICAL'
                            WHEN it.amount >= ? THEN 'HIGH'
                            WHEN it.amount >= ? THEN 'MEDIUM'
                            ELSE 'LOW' END,
                       it.client_id
                FROM internal_transactions it
                WHERE it.recon_match_id IS NULL
                  AND it.transaction_date BETWEEN ? AND ?
                ON CONFLICT (recon_run_id, entry_id, entry_source) DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reconRunId);
            ps.setObject(2, runDate);
            ps.setObject(3, runDate);
            ps.setBigDecimal(4, CRITICAL_AMOUNT);
            ps.setBigDecimal(5, HIGH_AMOUNT);
            ps.setBigDecimal(6, MEDIUM_AMOUNT);
            ps.setObject(7, runDate.minusDays(3));
            ps.setObject(8, runDate.plusDays(1));
            int rows = ps.executeUpdate();
            breakCounter.increment(rows);
            return rows;
        }
    }

    /** External present, internal absent → UNEXPECTED_ENTRY */
    private int classifyUnexpectedEntries(Connection conn, String reconRunId, LocalDate runDate)
            throws SQLException {
        String sql = """
                INSERT INTO recon_breaks
                    (break_id, recon_run_id, detected_date, entry_id, entry_source,
                     amount, currency, break_type, severity, client_id)
                SELECT gen_random_uuid(), ?, ?, se.entry_id, 'STATEMENT',
                       se.amount, se.currency, 'UNEXPECTED_ENTRY',
                       CASE WHEN se.amount >= ? THEN 'CRITICAL'
                            WHEN se.amount >= ? THEN 'HIGH'
                            WHEN se.amount >= ? THEN 'MEDIUM'
                            ELSE 'LOW' END,
                       se.client_id
                FROM statement_entries se
                WHERE se.recon_run_id IS NULL
                  AND se.transaction_date BETWEEN ? AND ?
                ON CONFLICT (recon_run_id, entry_id, entry_source) DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reconRunId);
            ps.setObject(2, runDate);
            ps.setBigDecimal(3, CRITICAL_AMOUNT);
            ps.setBigDecimal(4, HIGH_AMOUNT);
            ps.setBigDecimal(5, MEDIUM_AMOUNT);
            ps.setObject(6, runDate.minusDays(3));
            ps.setObject(7, runDate.plusDays(1));
            int rows = ps.executeUpdate();
            breakCounter.increment(rows);
            return rows;
        }
    }

    /** Fuzzy-matched pairs with amount discrepancy → AMOUNT_MISMATCH */
    private int classifyAmountMismatches(Connection conn, String reconRunId, LocalDate runDate)
            throws SQLException {
        String sql = """
                INSERT INTO recon_breaks
                    (break_id, recon_run_id, detected_date, entry_id, entry_source,
                     amount, currency, break_type, severity, client_id)
                SELECT gen_random_uuid(), ?, ?, rm.statement_entry_id, 'STATEMENT',
                       ABS(se.amount - it.amount), se.currency, 'AMOUNT_MISMATCH',
                       CASE WHEN ABS(se.amount - it.amount) >= ? THEN 'CRITICAL'
                            WHEN ABS(se.amount - it.amount) >= ? THEN 'HIGH'
                            WHEN ABS(se.amount - it.amount) >= ? THEN 'MEDIUM'
                            ELSE 'LOW' END,
                       se.client_id
                FROM recon_matches rm
                JOIN statement_entries se ON se.entry_id = rm.statement_entry_id
                JOIN internal_transactions it ON it.tx_id = rm.internal_tx_id
                WHERE rm.recon_run_id = ?
                  AND rm.confidence_score < 1.0
                  AND ABS(se.amount - it.amount) > 0.01
                ON CONFLICT (recon_run_id, entry_id, entry_source) DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reconRunId);
            ps.setObject(2, runDate);
            ps.setBigDecimal(3, CRITICAL_AMOUNT);
            ps.setBigDecimal(4, HIGH_AMOUNT);
            ps.setBigDecimal(5, MEDIUM_AMOUNT);
            ps.setString(6, reconRunId);
            int rows = ps.executeUpdate();
            breakCounter.increment(rows);
            return rows;
        }
    }

    /** Duplicate patterns → DUPLICATE */
    private int classifyDuplicates(Connection conn, String reconRunId, LocalDate runDate)
            throws SQLException {
        String sql = """
                INSERT INTO recon_breaks
                    (break_id, recon_run_id, detected_date, entry_id, entry_source,
                     amount, currency, break_type, severity, client_id)
                SELECT gen_random_uuid(), ?, ?, se.entry_id, 'STATEMENT',
                       se.amount, se.currency, 'DUPLICATE', 'MEDIUM', se.client_id
                FROM statement_entries se
                WHERE se.transaction_date BETWEEN ? AND ?
                  AND EXISTS (
                    SELECT 1 FROM statement_entries se2
                    WHERE se2.client_id = se.client_id
                      AND se2.amount = se.amount
                      AND se2.currency = se.currency
                      AND se2.transaction_date = se.transaction_date
                      AND se2.entry_id <> se.entry_id
                  )
                ON CONFLICT (recon_run_id, entry_id, entry_source) DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reconRunId);
            ps.setObject(2, runDate.minusDays(3));
            ps.setObject(3, runDate.plusDays(1));
            int rows = ps.executeUpdate();
            breakCounter.increment(rows);
            return rows;
        }
    }
}
