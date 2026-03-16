package com.ghatana.appplatform.reporting.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Reconciles submitted trade reports against internal trade records for a given
 *              reporting date. Detects two break types: MISSING_REPORT (trade in internal DB
 *              not submitted) and ORPHAN_REPORT (submitted but no matching internal trade).
 *              Breaks written to trade_report_breaks table. Satisfies STORY-D10-011.
 * @doc.layer   Domain
 * @doc.pattern Reconciliation; anti-join SQL; break detection; Counter metrics.
 */
public class TradeReportReconciliationService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Counter          missingReportCounter;
    private final Counter          orphanReportCounter;

    public TradeReportReconciliationService(HikariDataSource dataSource, Executor executor,
                                             MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.missingReportCounter = Counter.builder("reporting.recon.missing_total").register(registry);
        this.orphanReportCounter  = Counter.builder("reporting.recon.orphan_total").register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum BreakType { MISSING_REPORT, ORPHAN_REPORT }

    public record TradeReportBreak(String breakId, String tradeId, BreakType breakType,
                                    LocalDate reportDate, double tradeValue, LocalDateTime detectedAt) {}

    public record ReconSummary(LocalDate reportDate, long internalTradeCount, long submittedCount,
                                long missingReports, long orphanReports, LocalDateTime runAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ReconSummary> reconcile(LocalDate reportDate) {
        return Promise.ofBlocking(executor, () -> {
            long internal   = countInternalTrades(reportDate);
            long submitted  = countSubmittedReports(reportDate);
            long missing    = detectMissingReports(reportDate);
            long orphans    = detectOrphanReports(reportDate);
            missingReportCounter.increment(missing);
            orphanReportCounter.increment(orphans);
            return new ReconSummary(reportDate, internal, submitted, missing, orphans, LocalDateTime.now());
        });
    }

    public Promise<List<TradeReportBreak>> listBreaks(LocalDate reportDate) {
        return Promise.ofBlocking(executor, () -> loadBreaks(reportDate));
    }

    // ─── Detection ───────────────────────────────────────────────────────────

    private long detectMissingReports(LocalDate reportDate) throws SQLException {
        // Trades in internal DB but NOT in submitted trade_reports for the date
        String sql = """
                INSERT INTO trade_report_breaks
                    (break_id, trade_id, break_type, report_date, trade_value, detected_at)
                SELECT gen_random_uuid()::text, t.trade_id, 'MISSING_REPORT',
                       ?, t.quantity * t.price, NOW()
                FROM trades t
                WHERE t.trade_date = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM trade_reports tr WHERE tr.trade_id = t.trade_id
                  )
                ON CONFLICT (trade_id, report_date, break_type) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, reportDate);
            ps.setObject(2, reportDate);
            return ps.executeUpdate();
        }
    }

    private long detectOrphanReports(LocalDate reportDate) throws SQLException {
        // Trade reports submitted but no matching internal trade
        String sql = """
                INSERT INTO trade_report_breaks
                    (break_id, trade_id, break_type, report_date, trade_value, detected_at)
                SELECT gen_random_uuid()::text, tr.trade_id, 'ORPHAN_REPORT', ?, 0.0, NOW()
                FROM trade_reports tr
                WHERE DATE(tr.reported_at) = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM trades t WHERE t.trade_id = tr.trade_id
                  )
                ON CONFLICT (trade_id, report_date, break_type) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, reportDate);
            ps.setObject(2, reportDate);
            return ps.executeUpdate();
        }
    }

    private long countInternalTrades(LocalDate reportDate) throws SQLException {
        String sql = "SELECT COUNT(*) FROM trades WHERE trade_date=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, reportDate);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private long countSubmittedReports(LocalDate reportDate) throws SQLException {
        String sql = "SELECT COUNT(*) FROM trade_reports WHERE DATE(reported_at)=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, reportDate);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private List<TradeReportBreak> loadBreaks(LocalDate reportDate) throws SQLException {
        String sql = """
                SELECT break_id, trade_id, break_type, report_date, trade_value, detected_at
                FROM trade_report_breaks WHERE report_date=? ORDER BY detected_at DESC
                """;
        List<TradeReportBreak> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, reportDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TradeReportBreak(rs.getString("break_id"), rs.getString("trade_id"),
                            BreakType.valueOf(rs.getString("break_type")),
                            rs.getObject("report_date", LocalDate.class),
                            rs.getDouble("trade_value"),
                            rs.getObject("detected_at", LocalDateTime.class)));
                }
            }
        }
        return result;
    }
}
