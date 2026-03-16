package com.ghatana.appplatform.posttrade.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Generates dashboard metrics and reports for settlement failures. Exports
 *              settlement_success_rate, average_settlement_time, failure_by_reason, and
 *              buy_in_count metrics to K-06 observability. Produces daily settlement report
 *              (attempted, settled, failed, pending) and integrates with D-10 for SDR
 *              (Settlement Discipline Regime) regulatory exports.
 *              Satisfies STORY-D09-016.
 * @doc.layer   Domain
 * @doc.pattern Pull-based reporting; K-06 Micrometer export; SDR export port.
 */
public class SettlementFailureReportingService {

    private static final Logger log = LoggerFactory.getLogger(SettlementFailureReportingService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final AtomicLong       successRateGaugeValue = new AtomicLong(0);
    private final Counter          reportCounter;

    public SettlementFailureReportingService(HikariDataSource dataSource, Executor executor,
                                             MeterRegistry registry) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.reportCounter = registry.counter("posttrade.settlement_report.generated");
        Gauge.builder("posttrade.settlement.success_rate_bps", successRateGaugeValue, AtomicLong::get)
             .description("Settlement success rate in basis points (10000 = 100%)")
             .register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record DailySettlementReport(LocalDate reportDate, int attempted, int settled,
                                        int failed, int pending, double successRatePct,
                                        double avgSettlementTimeHours, List<FailureBreakdown> byReason,
                                        int buyInCount) {}

    public record FailureBreakdown(String failureType, int count, long totalQuantity) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<DailySettlementReport> generateDailyReport(LocalDate reportDate) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection()) {
                int[] counts = loadCounts(conn, reportDate);
                double successRate = counts[0] > 0 ? 100.0 * counts[1] / counts[0] : 0.0;
                successRateGaugeValue.set((long) (successRate * 100)); // store as bps
                double avgHours = loadAvgSettlementHours(conn, reportDate);
                List<FailureBreakdown> byReason = loadFailureByReason(conn, reportDate);
                int buyInCount = loadBuyInCount(conn, reportDate);
                reportCounter.increment();
                return new DailySettlementReport(reportDate, counts[0], counts[1], counts[2],
                        counts[3], successRate, avgHours, byReason, buyInCount);
            }
        });
    }

    /** SDR export: returns CSV lines per failed settlement for regulatory submission. */
    public Promise<List<String>> exportSdrCsv(LocalDate reportDate) {
        return Promise.ofBlocking(executor, () -> {
            List<String> lines = new ArrayList<>();
            lines.add("settlement_id,instrument_id,seller_id,buyer_id,quantity,settlement_date,failure_type,buy_in_id");
            String sql = """
                    SELECT f.settlement_id, f.instrument_id, f.seller_client_id,
                           f.buyer_client_id, f.quantity, f.settlement_date_ad,
                           f.failure_type, COALESCE(f.buy_in_id,'')
                    FROM settlement_failures f
                    WHERE f.detected_date = ?
                    ORDER BY f.settlement_id
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, reportDate);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lines.add(String.join(",",
                                rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.getString(4), rs.getString(5), rs.getString(6),
                                rs.getString(7), rs.getString(8)));
                    }
                }
            }
            return lines;
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Returns int[4]: {attempted, settled, failed, pending}.
     */
    private int[] loadCounts(Connection conn, LocalDate date) throws SQLException {
        String sql = """
                SELECT
                    COUNT(*) FILTER (WHERE settlement_date_ad = ?) AS attempted,
                    COUNT(*) FILTER (WHERE settlement_date_ad = ? AND status = 'SETTLED') AS settled,
                    COUNT(*) FILTER (WHERE settlement_date_ad = ? AND status = 'FAILED') AS failed,
                    COUNT(*) FILTER (WHERE settlement_date_ad = ? AND status NOT IN ('SETTLED','FAILED','CANCELLED')) AS pending
                FROM settlement_instructions
                WHERE settlement_date_ad = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) ps.setObject(i, date);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)};
                }
            }
        }
        return new int[]{0, 0, 0, 0};
    }

    private double loadAvgSettlementHours(Connection conn, LocalDate date) throws SQLException {
        String sql = """
                SELECT AVG(EXTRACT(EPOCH FROM (settled_at - created_at)) / 3600.0)
                FROM settlement_instructions
                WHERE settlement_date_ad = ? AND status = 'SETTLED'
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double v = rs.getDouble(1);
                    return rs.wasNull() ? 0.0 : v;
                }
            }
        }
        return 0.0;
    }

    private List<FailureBreakdown> loadFailureByReason(Connection conn, LocalDate date) throws SQLException {
        List<FailureBreakdown> list = new ArrayList<>();
        String sql = """
                SELECT failure_type, COUNT(*) AS cnt, SUM(quantity) AS total_qty
                FROM settlement_failures
                WHERE detected_date = ?
                GROUP BY failure_type
                ORDER BY cnt DESC
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new FailureBreakdown(rs.getString("failure_type"),
                            rs.getInt("cnt"), rs.getLong("total_qty")));
                }
            }
        }
        return list;
    }

    private int loadBuyInCount(Connection conn, LocalDate date) throws SQLException {
        String sql = "SELECT COUNT(*) FROM settlement_failures WHERE detected_date=? AND buy_in_id IS NOT NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
