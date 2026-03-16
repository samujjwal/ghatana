package com.ghatana.appplatform.deployment;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @doc.type    DomainService
 * @doc.purpose Publishes DORA metrics dashboard for the deployment subsystem, following the
 *              K-06 DashboardPort pattern. Computes the four DORA engineering metrics:
 *              Deployment Frequency, Lead Time for Changes, Mean Time to Restore (MTTR),
 *              and Change Failure Rate. Snapshots are persisted for trending. Exposes all
 *              four metrics as Micrometer Gauges. Satisfies STORY-K10-012.
 * @doc.layer   Kernel
 * @doc.pattern K-06 DashboardPort (inner interface); DORA metrics; four AtomicReference Gauges;
 *              snapshot persistence; publishDashboard() called by scheduler.
 */
public class DeploymentOperationsDashboardService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final DashboardPort    dashboardPort;

    private final AtomicReference<Double> deploymentFrequencyPerDay = new AtomicReference<>(0.0);
    private final AtomicReference<Double> leadTimeHours             = new AtomicReference<>(0.0);
    private final AtomicReference<Double> mttrHours                 = new AtomicReference<>(0.0);
    private final AtomicReference<Double> changeFailureRatePercent  = new AtomicReference<>(0.0);

    public DeploymentOperationsDashboardService(HikariDataSource dataSource, Executor executor,
                                                 DashboardPort dashboardPort, MeterRegistry registry) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.dashboardPort = dashboardPort;

        Gauge.builder("deployment.dora.frequency_per_day",        deploymentFrequencyPerDay, AtomicReference::get).register(registry);
        Gauge.builder("deployment.dora.lead_time_hours",          leadTimeHours,             AtomicReference::get).register(registry);
        Gauge.builder("deployment.dora.mttr_hours",               mttrHours,                 AtomicReference::get).register(registry);
        Gauge.builder("deployment.dora.change_failure_rate_pct",  changeFailureRatePercent,  AtomicReference::get).register(registry);
    }

    // ─── Inner port (K-06 DashboardPort pattern) ─────────────────────────────

    public interface DashboardPort {
        void publish(String dashboardId, String title, Map<String, Object> kpis, String status);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public record DoraSnapshot(
        String snapshotId,
        double deploymentFrequencyPerDay,
        double leadTimeHours,
        double mttrHours,
        double changeFailureRatePercent,
        String overallRating,           // ELITE / HIGH / MEDIUM / LOW
        Instant snapshotAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Compute DORA metrics over the trailing 30 days, update Gauges, persist snapshot,
     * and publish to the K-06 DashboardPort.
     */
    public Promise<DoraSnapshot> publishDashboard() {
        return Promise.ofBlocking(executor, () -> {
            DoraMetricsData data = computeDoraMetrics();

            deploymentFrequencyPerDay.set(data.frequencyPerDay());
            leadTimeHours.set(data.leadTimeHours());
            mttrHours.set(data.mttrHours());
            changeFailureRatePercent.set(data.changeFailureRatePercent());

            String rating = computeRating(data);
            String snapshotId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            persistSnapshot(snapshotId, data, rating, now);

            Map<String, Object> kpis = new LinkedHashMap<>();
            kpis.put("deploymentFrequencyPerDay",  data.frequencyPerDay());
            kpis.put("leadTimeHours",              data.leadTimeHours());
            kpis.put("mttrHours",                  data.mttrHours());
            kpis.put("changeFailureRatePercent",   data.changeFailureRatePercent());
            kpis.put("overallRating",              rating);

            dashboardPort.publish("deployment-operations", "Deployment Operations (DORA)", kpis, rating);

            return new DoraSnapshot(snapshotId, data.frequencyPerDay(), data.leadTimeHours(),
                data.mttrHours(), data.changeFailureRatePercent(), rating, now);
        });
    }

    /** Retrieve the latest DORA snapshot. */
    public Promise<Optional<DoraSnapshot>> getLatestSnapshot() {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT snapshot_id, deployment_frequency_per_day, lead_time_hours, " +
                     "mttr_hours, change_failure_rate_percent, overall_rating, snapshot_at " +
                     "FROM deployment_dora_snapshots ORDER BY snapshot_at DESC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(new DoraSnapshot(
                        rs.getString("snapshot_id"),
                        rs.getDouble("deployment_frequency_per_day"),
                        rs.getDouble("lead_time_hours"),
                        rs.getDouble("mttr_hours"),
                        rs.getDouble("change_failure_rate_percent"),
                        rs.getString("overall_rating"),
                        rs.getTimestamp("snapshot_at").toInstant()
                    ));
                }
            }
        });
    }

    /** List snapshots for trend analysis. */
    public Promise<List<DoraSnapshot>> getSnapshotHistory(int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<DoraSnapshot> results = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT snapshot_id, deployment_frequency_per_day, lead_time_hours, " +
                     "mttr_hours, change_failure_rate_percent, overall_rating, snapshot_at " +
                     "FROM deployment_dora_snapshots ORDER BY snapshot_at DESC LIMIT ?")) {
                ps.setInt(1, Math.min(limit, 365));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new DoraSnapshot(
                            rs.getString("snapshot_id"),
                            rs.getDouble("deployment_frequency_per_day"),
                            rs.getDouble("lead_time_hours"),
                            rs.getDouble("mttr_hours"),
                            rs.getDouble("change_failure_rate_percent"),
                            rs.getString("overall_rating"),
                            rs.getTimestamp("snapshot_at").toInstant()
                        ));
                    }
                }
            }
            return results;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private record DoraMetricsData(double frequencyPerDay, double leadTimeHours,
                                    double mttrHours, double changeFailureRatePercent) {}

    private DoraMetricsData computeDoraMetrics() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            double freqPerDay = queryDeploymentFrequency(c);
            double leadTime   = queryLeadTime(c);
            double mttr       = queryMttr(c);
            double cfr        = queryChangeFailureRate(c);
            return new DoraMetricsData(freqPerDay, leadTime, mttr, cfr);
        }
    }

    private double queryDeploymentFrequency(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT COUNT(*)::float / 30 AS freq_per_day FROM deployment_audit_log " +
            "WHERE event_type = 'DEPLOYED' AND occurred_at > NOW() - INTERVAL '30 days'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private double queryLeadTime(Connection c) throws SQLException {
        // Lead time = time from first commit reference to production deploy
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT AVG(EXTRACT(EPOCH FROM (prod_deploy_at - commit_at)) / 3600) AS avg_lead_hours " +
            "FROM deployment_lead_time_log WHERE prod_deploy_at > NOW() - INTERVAL '30 days'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private double queryMttr(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT AVG(duration_ms)::float / 3600000 AS avg_mttr_hours " +
            "FROM deployment_rollbacks WHERE status = 'SUCCEEDED' " +
            "AND initiated_at > NOW() - INTERVAL '30 days'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private double queryChangeFailureRate(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT " +
            "  COUNT(*) FILTER (WHERE event_type = 'ROLLBACK_INITIATED')::float * 100 / " +
            "  NULLIF(COUNT(*) FILTER (WHERE event_type = 'DEPLOYED'), 0) AS cfr " +
            "FROM deployment_audit_log WHERE occurred_at > NOW() - INTERVAL '30 days'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0.0;
                double v = rs.getDouble(1);
                return rs.wasNull() ? 0.0 : v;
            }
        }
    }

    private String computeRating(DoraMetricsData d) {
        // DORA performance levels (Google SRE benchmarks)
        boolean elite = d.frequencyPerDay() >= 1.0
            && d.leadTimeHours() <= 24
            && d.mttrHours() <= 1
            && d.changeFailureRatePercent() <= 5;
        boolean high = d.frequencyPerDay() >= 0.14  // ~weekly
            && d.leadTimeHours() <= 24 * 7
            && d.mttrHours() <= 24
            && d.changeFailureRatePercent() <= 10;
        boolean medium = d.frequencyPerDay() >= 0.033 // ~monthly
            && d.mttrHours() <= 24 * 7;

        if (elite)  return "ELITE";
        if (high)   return "HIGH";
        if (medium) return "MEDIUM";
        return "LOW";
    }

    private void persistSnapshot(String id, DoraMetricsData d, String rating,
                                  Instant now) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO deployment_dora_snapshots " +
                 "(snapshot_id, deployment_frequency_per_day, lead_time_hours, " +
                 "mttr_hours, change_failure_rate_percent, overall_rating, snapshot_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setDouble(2, d.frequencyPerDay());
            ps.setDouble(3, d.leadTimeHours());
            ps.setDouble(4, d.mttrHours());
            ps.setDouble(5, d.changeFailureRatePercent());
            ps.setString(6, rating);
            ps.setTimestamp(7, Timestamp.from(now));
            ps.executeUpdate();
        }
    }
}
