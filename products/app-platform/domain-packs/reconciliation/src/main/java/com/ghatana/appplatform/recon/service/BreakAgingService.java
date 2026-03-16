package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
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
 * @doc.purpose Tracks break aging from detection date. Aging tiers: 0-1 days (FRESH),
 *              2-3 days (PENDING), 4-7 days (AGING), 8-14 days (OVERDUE), 15+ days (CRITICAL).
 *              Daily batch recalculates age, escalates breaks crossing thresholds.
 *              Historical trend: break count over time, resolution rate, average resolution time.
 *              Exports dashboard metrics to K-06.
 *              Satisfies STORY-D13-011.
 * @doc.layer   Domain
 * @doc.pattern Scheduled aging; escalation on threshold; Gauge metrics for K-06 dashboard.
 */
public class BreakAgingService {

    private static final Logger log = LoggerFactory.getLogger(BreakAgingService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final AtomicLong       criticalBreakCount = new AtomicLong(0);
    private final AtomicLong       overdueBreakCount  = new AtomicLong(0);

    public BreakAgingService(HikariDataSource dataSource, Executor executor,
                             MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor   = executor;
        Gauge.builder("recon.breaks.critical", criticalBreakCount, AtomicLong::get)
             .description("Number of CRITICAL age breaks (15+ days)").register(registry);
        Gauge.builder("recon.breaks.overdue", overdueBreakCount, AtomicLong::get)
             .description("Number of OVERDUE age breaks (8-14 days)").register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record AgingTierSummary(String tier, int count, long totalBreakAmountCents) {}

    public record TrendPoint(LocalDate date, int openBreaks, int resolvedBreaks,
                             double avgResolutionDays) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** EOD batch: recalculate age tier for all open breaks and escalate as needed. */
    public Promise<Void> runDailyAging(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection()) {
                updateAgingTiers(conn, runDate);
                escalateBreaches(conn, runDate);
                refreshGauges(conn);
            }
            return null;
        });
    }

    public Promise<List<AgingTierSummary>> getAgingSummary() {
        return Promise.ofBlocking(executor, () -> {
            List<AgingTierSummary> result = new ArrayList<>();
            String sql = """
                    SELECT aging_tier, COUNT(*) AS cnt, SUM(amount * 100)::bigint AS amt_cents
                    FROM recon_breaks
                    WHERE status = 'OPEN'
                    GROUP BY aging_tier
                    ORDER BY CASE aging_tier
                        WHEN 'CRITICAL' THEN 1 WHEN 'OVERDUE' THEN 2
                        WHEN 'AGING' THEN 3 WHEN 'PENDING' THEN 4
                        ELSE 5 END
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new AgingTierSummary(rs.getString("aging_tier"),
                            rs.getInt("cnt"), rs.getLong("amt_cents")));
                }
            }
            return result;
        });
    }

    public Promise<List<TrendPoint>> getTrend30Days(LocalDate asOf) {
        return Promise.ofBlocking(executor, () -> {
            List<TrendPoint> trend = new ArrayList<>();
            String sql = """
                    SELECT
                        d.dt AS date,
                        COUNT(*) FILTER (WHERE rb.detected_date <= d.dt AND
                                               (rb.resolved_date IS NULL OR rb.resolved_date > d.dt)) AS open_breaks,
                        COUNT(*) FILTER (WHERE rb.resolved_date = d.dt) AS resolved_breaks,
                        AVG(CASE WHEN rb.resolved_date IS NOT NULL
                                 THEN rb.resolved_date - rb.detected_date END) AS avg_resolution_days
                    FROM generate_series(?, ?, '1 day'::interval) d(dt)
                    LEFT JOIN recon_breaks rb ON TRUE
                    GROUP BY d.dt
                    ORDER BY d.dt
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, asOf.minusDays(30));
                ps.setObject(2, asOf);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        trend.add(new TrendPoint(
                                rs.getObject("date", LocalDate.class),
                                rs.getInt("open_breaks"),
                                rs.getInt("resolved_breaks"),
                                rs.getDouble("avg_resolution_days")));
                    }
                }
            }
            return trend;
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void updateAgingTiers(Connection conn, LocalDate runDate) throws SQLException {
        String sql = """
                UPDATE recon_breaks SET
                    aging_tier = CASE
                        WHEN ? - detected_date <= 1 THEN 'FRESH'
                        WHEN ? - detected_date <= 3 THEN 'PENDING'
                        WHEN ? - detected_date <= 7 THEN 'AGING'
                        WHEN ? - detected_date <= 14 THEN 'OVERDUE'
                        ELSE 'CRITICAL'
                    END,
                    age_days = ? - detected_date
                WHERE status = 'OPEN'
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) ps.setObject(i, runDate);
            ps.executeUpdate();
        }
    }

    private void escalateBreaches(Connection conn, LocalDate runDate) throws SQLException {
        // Insert escalation events for newly critical/overdue breaks
        String sql = """
                INSERT INTO break_escalation_events
                    (event_id, break_id, escalation_date, new_tier)
                SELECT gen_random_uuid(), break_id, ?, aging_tier
                FROM recon_breaks
                WHERE status = 'OPEN'
                  AND aging_tier IN ('CRITICAL','OVERDUE')
                  AND NOT EXISTS (
                    SELECT 1 FROM break_escalation_events e
                    WHERE e.break_id = recon_breaks.break_id
                      AND e.escalation_date = ?
                  )
                ON CONFLICT DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            ps.setObject(2, runDate);
            ps.executeUpdate();
        }
    }

    private void refreshGauges(Connection conn) throws SQLException {
        String sql = "SELECT aging_tier, COUNT(*) FROM recon_breaks WHERE status='OPEN' GROUP BY aging_tier";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            long critical = 0, overdue = 0;
            while (rs.next()) {
                String tier = rs.getString(1);
                long cnt = rs.getLong(2);
                if ("CRITICAL".equals(tier)) critical = cnt;
                if ("OVERDUE".equals(tier)) overdue = cnt;
            }
            criticalBreakCount.set(critical);
            overdueBreakCount.set(overdue);
        }
    }
}
