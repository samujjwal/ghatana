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
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Runs on unmatched residuals from ExactMatchingService using relaxed tolerances:
 *              ±2 business days on date, ±0.01 on amount (absolute), reference substring match.
 *              Confidence scoring: date+amount exact = 0.99, date exact, amount fuzzy = 0.85,
 *              date fuzzy, amount exact = 0.80, all fuzzy = 0.70. Only matches above
 *              MIN_CONFIDENCE are persisted. Per-account thresholds are configurable via K-02.
 *              Performance target: 10,000 unmatched entries in under 30 seconds.
 * @doc.layer   Domain
 * @doc.pattern Candidate generation via date-window SQL + in-memory scoring; K-02 config port
 *              for per-account threshold overrides; idempotency via ON CONFLICT DO NOTHING.
 */
public class FuzzyMatchingService {

    private static final Logger log = LoggerFactory.getLogger(FuzzyMatchingService.class);

    private static final double DEFAULT_MIN_CONFIDENCE  = 0.70;
    private static final double AMOUNT_TOLERANCE        = 0.01;
    private static final int    DATE_TOLERANCE_DAYS     = 2;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final Counter          fuzzyMatchedCounter;
    private final Counter          residualCounter;
    private final Timer            fuzzyTimer;

    public FuzzyMatchingService(HikariDataSource dataSource, Executor executor,
                                ConfigPort configPort, MeterRegistry registry) {
        this.dataSource         = dataSource;
        this.executor           = executor;
        this.configPort         = configPort;
        this.fuzzyMatchedCounter = registry.counter("recon.fuzzy_match.matched");
        this.residualCounter    = registry.counter("recon.fuzzy_match.residual");
        this.fuzzyTimer         = registry.timer("recon.fuzzy_match.duration");
    }

    // ─── Inner port (K-02) ───────────────────────────────────────────────────

    /**
     * K-02 configuration port — per-account fuzzy threshold overrides.
     */
    public interface ConfigPort {
        double getMinConfidence(String accountCode);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    private record Candidate(
        String  statementEntryId,
        LocalDate stmtDate,
        double  stmtAmount,
        String  stmtCurrency,
        String  stmtReference,
        String  stmtAccountCode,
        String  internalTxId,
        LocalDate txDate,
        double  txAmount,
        String  txCurrency,
        String  txReference
    ) {}

    public record FuzzyMatch(
        String statementEntryId,
        String internalTxId,
        double confidenceScore,
        String matchReason
    ) {}

    public record FuzzyRunSummary(
        String    reconRunId,
        int       fuzzyMatchedCount,
        int       residualCount,
        long      durationMs
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Run fuzzy matching on all unmatched entries up to runDate.
     */
    public Promise<FuzzyRunSummary> runFuzzyMatching(String reconRunId, LocalDate runDate) {
        return Promise.ofBlocking(executor, () ->
            fuzzyTimer.recordCallable(() -> doFuzzyMatch(reconRunId, runDate))
        );
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private FuzzyRunSummary doFuzzyMatch(String reconRunId, LocalDate runDate) throws SQLException {
        long start = System.currentTimeMillis();

        // Load unmatched statement entries
        List<UnmatchedEntry> unmatched = loadUnmatchedEntries(runDate);
        int fuzzyMatched = 0;
        int residual     = 0;

        for (UnmatchedEntry se : unmatched) {
            double threshold = configPort.getMinConfidence(se.accountCode());
            List<Candidate> candidates = fetchCandidates(se, DATE_TOLERANCE_DAYS);

            FuzzyMatch best = null;
            for (Candidate c : candidates) {
                FuzzyMatch fm = score(se, c);
                if (fm.confidenceScore() >= threshold) {
                    if (best == null || fm.confidenceScore() > best.confidenceScore()) {
                        best = fm;
                    }
                }
            }

            if (best != null) {
                persistFuzzyMatch(reconRunId, best);
                fuzzyMatched++;
            } else {
                residual++;
            }
        }

        fuzzyMatchedCounter.increment(fuzzyMatched);
        residualCounter.increment(residual);
        long duration = System.currentTimeMillis() - start;
        log.info("Fuzzy match run={} date={} matched={} residual={} durationMs={}",
                 reconRunId, runDate, fuzzyMatched, residual, duration);
        return new FuzzyRunSummary(reconRunId, fuzzyMatched, residual, duration);
    }

    private FuzzyMatch score(UnmatchedEntry se, Candidate c) {
        boolean dateExact   = se.valueDateAd().equals(c.txDate());
        boolean amountExact = Math.abs(se.amount() - c.txAmount()) < 0.001;
        boolean amountFuzzy = Math.abs(se.amount() - c.txAmount()) <= AMOUNT_TOLERANCE;
        boolean refMatch    = c.txReference() != null &&
                              (c.txReference().contains(se.reference()) ||
                               se.reference().contains(c.txReference()));

        double score;
        String reason;
        if (dateExact && amountExact && refMatch) {
            score  = 0.99;
            reason = "DATE_EXACT,AMOUNT_EXACT,REF_MATCH";
        } else if (dateExact && amountFuzzy && refMatch) {
            score  = 0.90;
            reason = "DATE_EXACT,AMOUNT_FUZZY,REF_MATCH";
        } else if (dateExact && amountExact) {
            score  = 0.88;
            reason = "DATE_EXACT,AMOUNT_EXACT";
        } else if (dateExact && amountFuzzy) {
            score  = 0.85;
            reason = "DATE_EXACT,AMOUNT_FUZZY";
        } else if (amountExact && refMatch) {
            score  = 0.80;
            reason = "DATE_FUZZY,AMOUNT_EXACT,REF_MATCH";
        } else if (amountFuzzy && refMatch) {
            score  = 0.75;
            reason = "DATE_FUZZY,AMOUNT_FUZZY,REF_MATCH";
        } else if (amountFuzzy) {
            score  = 0.70;
            reason = "DATE_FUZZY,AMOUNT_FUZZY";
        } else {
            score  = 0.0;
            reason = "NO_MATCH";
        }

        return new FuzzyMatch(se.entryId(), c.internalTxId(), score, reason);
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private record UnmatchedEntry(
        String entryId, String accountCode, LocalDate valueDateAd,
        double amount, String currency, String reference
    ) {}

    private List<UnmatchedEntry> loadUnmatchedEntries(LocalDate runDate) throws SQLException {
        String sql = """
            SELECT se.entry_id, se.account_code, se.value_date_ad, se.amount, se.currency, se.reference
            FROM statement_entries se
            WHERE se.value_date_ad <= ?
              AND se.entry_id NOT IN (SELECT statement_entry_id FROM recon_matches)
            ORDER BY se.value_date_ad
            """;
        List<UnmatchedEntry> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new UnmatchedEntry(
                        rs.getString("entry_id"),
                        rs.getString("account_code"),
                        rs.getObject("value_date_ad", LocalDate.class),
                        rs.getDouble("amount"),
                        rs.getString("currency"),
                        rs.getString("reference")
                    ));
                }
            }
        }
        return list;
    }

    private List<Candidate> fetchCandidates(UnmatchedEntry se, int dayTolerance) throws SQLException {
        String sql = """
            SELECT it.tx_id, it.value_date_ad, it.amount, it.currency, it.reference
            FROM internal_transactions it
            WHERE it.value_date_ad BETWEEN ? AND ?
              AND it.currency = ?
              AND ABS(it.amount - ?) <= ?
              AND it.tx_id NOT IN (SELECT internal_tx_id FROM recon_matches)
            ORDER BY ABS(it.value_date_ad - ?) ASC, ABS(it.amount - ?) ASC
            LIMIT 10
            """;
        List<Candidate> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            LocalDate lo = se.valueDateAd().minusDays(dayTolerance);
            LocalDate hi = se.valueDateAd().plusDays(dayTolerance);
            ps.setObject(1, lo);
            ps.setObject(2, hi);
            ps.setString(3, se.currency());
            ps.setDouble(4, se.amount());
            ps.setDouble(5, Math.abs(se.amount()) * 0.10); // 10% window for candidate gen
            ps.setObject(6, se.valueDateAd());
            ps.setDouble(7, se.amount());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Candidate(
                        se.entryId(), se.valueDateAd(), se.amount(), se.currency(), se.reference(),
                        se.accountCode(),
                        rs.getString("tx_id"),
                        rs.getObject("value_date_ad", LocalDate.class),
                        rs.getDouble("amount"),
                        rs.getString("currency"),
                        rs.getString("reference")
                    ));
                }
            }
        }
        return list;
    }

    private void persistFuzzyMatch(String reconRunId, FuzzyMatch match) throws SQLException {
        String sql = """
            INSERT INTO recon_matches (
                match_id, recon_run_id, statement_entry_id, internal_tx_id,
                match_type, confidence_score, matched_on, created_at
            ) VALUES (gen_random_uuid()::text, ?, ?, ?, 'FUZZY', ?, ?, now())
            ON CONFLICT (statement_entry_id, internal_tx_id) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reconRunId);
            ps.setString(2, match.statementEntryId());
            ps.setString(3, match.internalTxId());
            ps.setDouble(4, match.confidenceScore());
            ps.setString(5, match.matchReason());
            ps.executeUpdate();
        }
    }
}
