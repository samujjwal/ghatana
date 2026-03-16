package com.ghatana.appplatform.posttrade.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
 * @doc.purpose Daily reconciliation between internal settlement position records and CSD
 *              (Central Securities Depository) position statements. Fetches CSD position
 *              report via adapter, compares per-instrument per-account quantities, and
 *              generates CsdReconciliationBreak events for unresolved discrepancies.
 *              Satisfies STORY-D09-012.
 * @doc.layer   Domain
 * @doc.pattern Event-driven; Adapter port for CSD (K-05); INSERT-only break audit trail;
 *              reconciliation report generation.
 */
public class CsdReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(CsdReconciliationService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CsdAdapterPort   csdAdapter;
    private final Counter          matchCounter;
    private final Counter          breakCounter;

    public CsdReconciliationService(HikariDataSource dataSource, Executor executor,
                                    CsdAdapterPort csdAdapter, MeterRegistry registry) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.csdAdapter    = csdAdapter;
        this.matchCounter  = registry.counter("posttrade.csd_recon.matches");
        this.breakCounter  = registry.counter("posttrade.csd_recon.breaks");
    }

    // ─── Inner port (K-05 CSD adapter) ──────────────────────────────────────

    public interface CsdAdapterPort {
        List<CsdPosition> fetchPositions(LocalDate reportDate);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record CsdPosition(String accountCode, String instrumentId, long quantity) {}

    public record ReconBreak(String breakId, LocalDate reportDate, String accountCode,
                             String instrumentId, long internalQty, long csdQty,
                             String breakType, String severity) {}

    public record ReconReport(LocalDate reportDate, int totalAccounts, int matched,
                              int qtyMismatch, int missingInternal, int extraInternal,
                              List<ReconBreak> breaks) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ReconReport> runDailyReconciliation(LocalDate reportDate) {
        return Promise.ofBlocking(executor, () -> {
            List<CsdPosition> csdPositions = csdAdapter.fetchPositions(reportDate);
            List<ReconBreak> breaks = new ArrayList<>();
            int matched = 0, qtyMismatch = 0, missingInternal = 0, extraInternal = 0;

            try (Connection conn = dataSource.getConnection()) {
                for (CsdPosition csd : csdPositions) {
                    long internalQty = loadInternalPosition(conn, csd.accountCode(), csd.instrumentId(), reportDate);
                    if (internalQty == -1L) {
                        // CSD has position, internal does not
                        ReconBreak brk = new ReconBreak(UUID.randomUUID().toString(), reportDate,
                                csd.accountCode(), csd.instrumentId(), 0L, csd.quantity(),
                                "EXTRA_INTERNAL", classifySeverity(csd.quantity()));
                        breaks.add(brk);
                        persistBreak(conn, brk);
                        extraInternal++;
                    } else if (internalQty == csd.quantity()) {
                        matched++;
                        matchCounter.increment();
                    } else {
                        long diff = Math.abs(internalQty - csd.quantity());
                        ReconBreak brk = new ReconBreak(UUID.randomUUID().toString(), reportDate,
                                csd.accountCode(), csd.instrumentId(), internalQty, csd.quantity(),
                                "QUANTITY_MISMATCH", classifySeverity(diff));
                        breaks.add(brk);
                        persistBreak(conn, brk);
                        qtyMismatch++;
                        breakCounter.increment();
                    }
                }
                // find positions in internal but missing in CSD
                List<String[]> internalOnly = loadInternalOnlyPositions(conn, reportDate, csdPositions);
                for (String[] pos : internalOnly) {
                    long internalQty = Long.parseLong(pos[2]);
                    ReconBreak brk = new ReconBreak(UUID.randomUUID().toString(), reportDate,
                            pos[0], pos[1], internalQty, 0L,
                            "MISSING_POSITION", classifySeverity(internalQty));
                    breaks.add(brk);
                    persistBreak(conn, brk);
                    missingInternal++;
                    breakCounter.increment();
                }
                int totalAccounts = loadDistinctAccountCount(conn, reportDate);
                return new ReconReport(reportDate, totalAccounts, matched, qtyMismatch, missingInternal, extraInternal, breaks);
            }
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private long loadInternalPosition(Connection conn, String accountCode, String instrumentId,
                                      LocalDate date) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(quantity), -1)
                FROM client_positions
                WHERE account_code = ? AND instrument_id = ? AND position_date = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountCode);
            ps.setString(2, instrumentId);
            ps.setObject(3, date);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long v = rs.getLong(1);
                    return rs.wasNull() ? -1L : v;
                }
                return -1L;
            }
        }
    }

    private List<String[]> loadInternalOnlyPositions(Connection conn, LocalDate date,
                                                     List<CsdPosition> csdPositions) throws SQLException {
        // Build exclusion set in-memory to avoid dynamic SQL injection
        List<String[]> result = new ArrayList<>();
        String sql = """
                SELECT account_code, instrument_id, SUM(quantity) AS qty
                FROM client_positions
                WHERE position_date = ?
                GROUP BY account_code, instrument_id
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String acc = rs.getString("account_code");
                    String ins = rs.getString("instrument_id");
                    long qty = rs.getLong("qty");
                    boolean inCsd = csdPositions.stream()
                            .anyMatch(c -> c.accountCode().equals(acc) && c.instrumentId().equals(ins));
                    if (!inCsd) {
                        result.add(new String[]{acc, ins, String.valueOf(qty)});
                    }
                }
            }
        }
        return result;
    }

    private int loadDistinctAccountCount(Connection conn, LocalDate date) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT account_code) FROM client_positions WHERE position_date = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void persistBreak(Connection conn, ReconBreak brk) throws SQLException {
        String sql = """
                INSERT INTO csd_recon_breaks
                    (break_id, report_date, account_code, instrument_id,
                     internal_qty, csd_qty, break_type, severity, detected_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (report_date, account_code, instrument_id) DO UPDATE
                    SET break_type = EXCLUDED.break_type,
                        internal_qty = EXCLUDED.internal_qty,
                        csd_qty = EXCLUDED.csd_qty,
                        severity = EXCLUDED.severity
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, brk.breakId());
            ps.setObject(2, brk.reportDate());
            ps.setString(3, brk.accountCode());
            ps.setString(4, brk.instrumentId());
            ps.setLong(5, brk.internalQty());
            ps.setLong(6, brk.csdQty());
            ps.setString(7, brk.breakType());
            ps.setString(8, brk.severity());
            ps.executeUpdate();
        }
    }

    private String classifySeverity(long qty) {
        if (qty > 100_000) return "CRITICAL";
        if (qty > 10_000)  return "HIGH";
        if (qty > 1_000)   return "MEDIUM";
        return "LOW";
    }
}
