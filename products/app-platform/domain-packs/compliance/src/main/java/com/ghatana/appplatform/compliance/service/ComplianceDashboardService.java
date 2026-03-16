package com.ghatana.appplatform.compliance.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * @doc.type      Service
 * @doc.purpose   Aggregates compliance metrics for the dashboard API. Provides real-time counts
 *                of check outcomes, open EDD cases, pending attestations, restricted list stats,
 *                AML risk distribution, and beneficial ownership disclosure status.
 * @doc.layer     Application
 * @doc.pattern   Reporting / read model aggregation
 *
 * Story: D07-014
 */
public class ComplianceDashboardService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceDashboardService.class);

    private final DataSource    dataSource;
    private final Timer         queryTimer;

    public ComplianceDashboardService(DataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.queryTimer = meterRegistry.timer("compliance.dashboard.query.duration");
    }

    /**
     * Returns the compliance dashboard snapshot (real-time counts across all sub-domains).
     */
    public DashboardSnapshot getSnapshot() {
        return queryTimer.record(() -> {
            try (Connection c = dataSource.getConnection()) {
                return DashboardSnapshot.of(
                        loadCheckStats(c),
                        loadEddStats(c),
                        loadAttestationStats(c),
                        loadAmlStats(c),
                        loadDisclosureStats(c),
                        loadRestrictedListStats(c)
                );
            } catch (SQLException e) {
                log.error("Dashboard snapshot query failed", e);
                return DashboardSnapshot.empty();
            }
        });
    }

    /**
     * Returns a daily summary report for a given date.
     *
     * @param date  report date in ISO-8601 (yyyy-MM-dd)
     */
    public DailySummaryReport getDailySummary(String date) {
        LocalDate reportDate = LocalDate.parse(date);
        try (Connection c = dataSource.getConnection()) {
            long checksRan    = countForDate(c, "compliance_check_log", "checked_at", reportDate);
            long checksFailed = countForDateAndStatus(c, "compliance_check_log", "checked_at",
                                                       "outcome", "FAIL", reportDate);
            long eddCreated   = countForDate(c, "edd_cases", "created_at", reportDate);
            long strTriggered = countForDate(c, "suspicious_transaction_reports", "created_at", reportDate);
            return new DailySummaryReport(date, checksRan, checksFailed, eddCreated, strTriggered);
        } catch (SQLException e) {
            log.error("Daily summary query failed date={}", date, e);
            return new DailySummaryReport(date, 0, 0, 0, 0);
        }
    }

    // ─── stat loaders ─────────────────────────────────────────────────────────

    private CheckStats loadCheckStats(Connection c) throws SQLException {
        String sql = "SELECT outcome, COUNT(*) as cnt FROM compliance_check_log "
                   + "WHERE checked_at >= NOW() - INTERVAL '24 hours' GROUP BY outcome";
        long pass = 0, fail = 0, review = 0;
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String outcome = rs.getString("outcome");
                long cnt = rs.getLong("cnt");
                switch (outcome) {
                    case "PASS"   -> pass   = cnt;
                    case "FAIL"   -> fail   = cnt;
                    case "REVIEW" -> review = cnt;
                }
            }
        }
        return new CheckStats(pass, fail, review);
    }

    private EddStats loadEddStats(Connection c) throws SQLException {
        String sql = "SELECT status, COUNT(*) as cnt FROM edd_cases GROUP BY status";
        long open = 0, resolved = 0;
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if ("OPEN".equals(rs.getString("status"))) open     = rs.getLong("cnt");
                else                                        resolved = rs.getLong("cnt");
            }
        }
        return new EddStats(open, resolved);
    }

    private AttestationStats loadAttestationStats(Connection c) throws SQLException {
        String sql = "SELECT status, COUNT(*) as cnt FROM compliance_attestations GROUP BY status";
        long pending = 0, signed = 0, overdue = 0;
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String s = rs.getString("status");
                long cnt = rs.getLong("cnt");
                switch (s) {
                    case "PENDING"  -> pending = cnt;
                    case "SIGNED"   -> signed  = cnt;
                    case "EXPIRED"  -> overdue = cnt;
                }
            }
        }
        return new AttestationStats(pending, signed, overdue);
    }

    private AmlStats loadAmlStats(Connection c) throws SQLException {
        String sql = "SELECT risk_level, COUNT(*) as cnt FROM client_aml_risk GROUP BY risk_level";
        long low = 0, medium = 0, high = 0, critical = 0;
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String lvl = rs.getString("risk_level");
                long cnt = rs.getLong("cnt");
                switch (lvl) {
                    case "LOW"      -> low      = cnt;
                    case "MEDIUM"   -> medium   = cnt;
                    case "HIGH"     -> high     = cnt;
                    case "CRITICAL" -> critical = cnt;
                }
            }
        }
        return new AmlStats(low, medium, high, critical);
    }

    private DisclosureStats loadDisclosureStats(Connection c) throws SQLException {
        String sql = "SELECT status, COUNT(*) as cnt FROM beneficial_ownership_disclosures GROUP BY status";
        long pending = 0, submitted = 0;
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if ("PENDING".equals(rs.getString("status"))) pending   = rs.getLong("cnt");
                else                                           submitted = rs.getLong("cnt");
            }
        }
        return new DisclosureStats(pending, submitted);
    }

    private RestrictedListStats loadRestrictedListStats(Connection c) throws SQLException {
        String sql = "SELECT COUNT(*) as cnt FROM restricted_list WHERE active = TRUE";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return new RestrictedListStats(rs.getLong("cnt"));
        }
        return new RestrictedListStats(0);
    }

    private long countForDate(Connection c, String table, String tsColumn,
                               LocalDate date) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + tsColumn + "::date = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private long countForDateAndStatus(Connection c, String table, String tsColumn,
                                        String statusColumn, String status,
                                        LocalDate date) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table
                   + " WHERE " + tsColumn + "::date = ? AND " + statusColumn + " = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record CheckStats(long pass, long fail, long review) {}
    public record EddStats(long open, long resolved) {}
    public record AttestationStats(long pending, long signed, long overdue) {}
    public record AmlStats(long low, long medium, long high, long critical) {}
    public record DisclosureStats(long pending, long submitted) {}
    public record RestrictedListStats(long active) {}

    public record DashboardSnapshot(CheckStats checks, EddStats edd, AttestationStats attestations,
                                     AmlStats aml, DisclosureStats disclosures,
                                     RestrictedListStats restrictedList,
                                     Instant generatedAt) {
        public static DashboardSnapshot of(CheckStats c, EddStats e, AttestationStats a,
                                            AmlStats m, DisclosureStats d, RestrictedListStats r) {
            return new DashboardSnapshot(c, e, a, m, d, r, Instant.now());
        }
        public static DashboardSnapshot empty() {
            return new DashboardSnapshot(new CheckStats(0,0,0), new EddStats(0,0),
                    new AttestationStats(0,0,0), new AmlStats(0,0,0,0), new DisclosureStats(0,0),
                    new RestrictedListStats(0), Instant.now());
        }
    }

    public record DailySummaryReport(String date, long checksRan, long checksFailed,
                                      long eddCreated, long strTriggered) {}
}
