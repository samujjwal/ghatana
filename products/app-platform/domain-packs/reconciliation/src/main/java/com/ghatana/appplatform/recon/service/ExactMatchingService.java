package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @doc.purpose Executes deterministic exact-match reconciliation between normalized statement
 *              entries and internal transaction records. Match criteria: exact date, exact amount
 *              (no tolerance), exact currency, exact reference. Produces one-to-one matches only.
 *              Performance target: 50,000 entries in under 60 seconds using batch SQL joins.
 *              All matches written to recon_matches with confidence_score = 1.0.
 *              Unmatched entries remain available for FuzzyMatchingService.
 * @doc.layer   Domain
 * @doc.pattern Set-based SQL matching (anti-join for residuals); idempotency via
 *              ON CONFLICT(statement_entry_id, internal_tx_id) DO NOTHING.
 */
public class ExactMatchingService {

    private static final Logger log = LoggerFactory.getLogger(ExactMatchingService.class);

    private static final double EXACT_CONFIDENCE = 1.0;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Counter          matchedCounter;
    private final Counter          unmatchedCounter;
    private final Timer            matchTimer;

    public ExactMatchingService(HikariDataSource dataSource, Executor executor,
                                MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.matchedCounter  = registry.counter("recon.exact_match.matched");
        this.unmatchedCounter = registry.counter("recon.exact_match.unmatched");
        this.matchTimer      = registry.timer("recon.exact_match.duration");
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ExactMatchResult(
        String  matchId,
        String  statementEntryId,
        String  internalTxId,
        double  confidence,
        String  matchedOn       // e.g. "DATE,AMOUNT,CURRENCY,REFERENCE"
    ) {}

    public record MatchRunSummary(
        String    reconRunId,
        LocalDate runDate,
        int       matchedCount,
        int       unmatchedCount,
        long      durationMs
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Run exact matching for all unmatched statement entries up to runDate.
     */
    public Promise<MatchRunSummary> runExactMatching(String reconRunId, LocalDate runDate) {
        return Promise.ofBlocking(executor, () ->
            matchTimer.recordCallable(() -> doExactMatch(reconRunId, runDate))
        );
    }

    /**
     * Return statement entry IDs that are still unmatched after exact matching.
     */
    public Promise<List<String>> loadUnmatchedIds(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> fetchUnmatchedIds(runDate));
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private MatchRunSummary doExactMatch(String reconRunId, LocalDate runDate) throws SQLException {
        long start = System.currentTimeMillis();

        // Single SQL: find all exact matches and insert in one pass
        String matchSql = """
            INSERT INTO recon_matches (
                match_id, recon_run_id, statement_entry_id, internal_tx_id,
                match_type, confidence_score, matched_on, created_at
            )
            SELECT
                gen_random_uuid()::text,
                ?,
                se.entry_id,
                it.tx_id,
                'EXACT',
                1.0,
                'DATE,AMOUNT,CURRENCY,REFERENCE',
                now()
            FROM statement_entries se
            JOIN internal_transactions it
               ON se.value_date_ad = it.value_date_ad
              AND se.amount        = it.amount
              AND se.currency      = it.currency
              AND se.reference     = it.reference
            WHERE se.value_date_ad <= ?
              AND se.entry_id NOT IN (SELECT statement_entry_id FROM recon_matches)
              AND it.tx_id   NOT IN (SELECT internal_tx_id      FROM recon_matches)
            ON CONFLICT (statement_entry_id, internal_tx_id) DO NOTHING
            """;

        int matched;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(matchSql)) {
            ps.setString(1, reconRunId);
            ps.setObject(2, runDate);
            matched = ps.executeUpdate();
        }

        int unmatched = countUnmatched(runDate);
        long duration = System.currentTimeMillis() - start;

        matchedCounter.increment(matched);
        unmatchedCounter.increment(unmatched);
        log.info("Exact match run={} date={} matched={} unmatched={} durationMs={}",
                 reconRunId, runDate, matched, unmatched, duration);

        return new MatchRunSummary(reconRunId, runDate, matched, unmatched, duration);
    }

    private int countUnmatched(LocalDate runDate) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM statement_entries
            WHERE value_date_ad <= ?
              AND entry_id NOT IN (SELECT statement_entry_id FROM recon_matches)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<String> fetchUnmatchedIds(LocalDate runDate) throws SQLException {
        String sql = """
            SELECT entry_id FROM statement_entries
            WHERE value_date_ad <= ?
              AND entry_id NOT IN (SELECT statement_entry_id FROM recon_matches)
            ORDER BY value_date_ad
            """;
        List<String> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("entry_id"));
            }
        }
        return ids;
    }
}
