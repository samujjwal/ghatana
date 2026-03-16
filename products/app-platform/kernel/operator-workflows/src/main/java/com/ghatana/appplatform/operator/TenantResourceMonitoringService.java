package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @doc.type    Service
 * @doc.purpose Track per-tenant resource consumption and enforce quotas.
 *              Collects: API requests/sec, event throughput, storage bytes, active users.
 *              Alert at 80% of quota, throttle at 100%.
 *              Exports usage summaries per billing period.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-O01-003: Tenant resource usage monitoring
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS tenant_resource_usage (
 *   usage_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   tenant_id      TEXT NOT NULL,
 *   metric_type    TEXT NOT NULL,   -- API_RPS | EVENT_THROUGHPUT | STORAGE_BYTES | ACTIVE_USERS
 *   value          NUMERIC NOT NULL,
 *   recorded_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE INDEX IF NOT EXISTS idx_tru_tenant_time ON tenant_resource_usage(tenant_id, recorded_at DESC);
 * CREATE TABLE IF NOT EXISTS tenant_usage_alerts (
 *   alert_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   tenant_id      TEXT NOT NULL,
 *   metric_type    TEXT NOT NULL,
 *   threshold_pct  INT NOT NULL,
 *   fired_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   resolved_at    TIMESTAMPTZ
 * );
 * </pre>
 */
public class TenantResourceMonitoringService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface UsageCollectorPort {
        /** Current value of a given metric for a tenant. */
        double currentValue(String tenantId, String metricType) throws Exception;
    }

    public interface ThrottlePort {
        /** Signal that a tenant should be throttled (HTTP 429). */
        void throttle(String tenantId, String metricType) throws Exception;
        /** Lift throttle if tenant has dropped below limit. */
        void liftThrottle(String tenantId, String metricType) throws Exception;
    }

    public interface AlertPort {
        void fireAlert(String tenantId, String metricType, int thresholdPct, double current, double quota) throws Exception;
        void resolveAlert(String tenantId, String metricType) throws Exception;
    }

    public interface QuotaPort {
        /** Return quota for metric (e.g. API_RPS → 1000). */
        double getQuota(String tenantId, String metricType) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record UsageSample(String tenantId, String metricType, double value, Instant recordedAt) {}

    public record BillingPeriodSummary(
        String tenantId, Instant periodStart, Instant periodEnd,
        Map<String, Double> peakValues, Map<String, Double> avgValues, Map<String, Double> quotas
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private static final double ALERT_THRESHOLD = 0.80;
    private static final double THROTTLE_THRESHOLD = 1.00;
    private static final List<String> METRIC_TYPES =
        List.of("API_RPS", "EVENT_THROUGHPUT", "STORAGE_BYTES", "ACTIVE_USERS");

    private final javax.sql.DataSource ds;
    private final UsageCollectorPort collector;
    private final ThrottlePort throttle;
    private final AlertPort alertPort;
    private final QuotaPort quotaPort;
    private final Executor executor;
    private final Counter throttleEvents;
    private final Counter alertFires;
    private final MeterRegistry registry;

    public TenantResourceMonitoringService(
        javax.sql.DataSource ds,
        UsageCollectorPort collector,
        ThrottlePort throttle,
        AlertPort alertPort,
        QuotaPort quotaPort,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds           = ds;
        this.collector    = collector;
        this.throttle     = throttle;
        this.alertPort    = alertPort;
        this.quotaPort    = quotaPort;
        this.executor     = executor;
        this.registry     = registry;
        this.throttleEvents = Counter.builder("operator.resource.throttle_events").register(registry);
        this.alertFires     = Counter.builder("operator.resource.alert_fires").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Collect current usage for a tenant and persist sample. Evaluates quota thresholds.
     * Called on a scheduled tick (e.g. every 10 s).
     */
    public Promise<Void> collectAndEvaluate(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            for (String metricType : METRIC_TYPES) {
                double current = collector.currentValue(tenantId, metricType);
                persistSample(tenantId, metricType, current);

                double quota = quotaPort.getQuota(tenantId, metricType);
                if (quota <= 0) continue;
                double ratio = current / quota;

                if (ratio >= THROTTLE_THRESHOLD) {
                    throttle.throttle(tenantId, metricType);
                    throttleEvents.increment();
                    ensureAlertFired(tenantId, metricType, 100, current, quota);
                } else if (ratio >= ALERT_THRESHOLD) {
                    throttle.liftThrottle(tenantId, metricType);
                    ensureAlertFired(tenantId, metricType, 80, current, quota);
                } else {
                    throttle.liftThrottle(tenantId, metricType);
                    resolveAlert(tenantId, metricType);
                }
            }
            return null;
        });
    }

    /**
     * Retrieve time-series samples for a tenant within a time window.
     */
    public Promise<List<UsageSample>> getSamples(String tenantId, Instant from, Instant to) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT metric_type, value, recorded_at FROM tenant_resource_usage " +
                     "WHERE tenant_id=? AND recorded_at BETWEEN ? AND ? ORDER BY recorded_at"
                 )) {
                ps.setString(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(from));
                ps.setTimestamp(3, Timestamp.from(to));
                List<UsageSample> samples = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        samples.add(new UsageSample(tenantId,
                            rs.getString("metric_type"),
                            rs.getDouble("value"),
                            rs.getTimestamp("recorded_at").toInstant()));
                    }
                }
                return samples;
            }
        });
    }

    /**
     * Produce a billing-period usage summary with peak and average per metric.
     */
    public Promise<BillingPeriodSummary> billingPeriodSummary(String tenantId, Instant from, Instant to) {
        return Promise.ofBlocking(executor, () -> {
            Map<String, Double> peakValues = new LinkedHashMap<>();
            Map<String, Double> avgValues  = new LinkedHashMap<>();
            Map<String, Double> quotas     = new LinkedHashMap<>();

            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT metric_type, MAX(value) AS peak, AVG(value) AS avg " +
                     "FROM tenant_resource_usage " +
                     "WHERE tenant_id=? AND recorded_at BETWEEN ? AND ? GROUP BY metric_type"
                 )) {
                ps.setString(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(from));
                ps.setTimestamp(3, Timestamp.from(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String m = rs.getString("metric_type");
                        peakValues.put(m, rs.getDouble("peak"));
                        avgValues.put(m, rs.getDouble("avg"));
                    }
                }
            }
            for (String m : METRIC_TYPES) {
                quotas.put(m, quotaPort.getQuota(tenantId, m));
            }
            return new BillingPeriodSummary(tenantId, from, to, peakValues, avgValues, quotas);
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void persistSample(String tenantId, String metricType, double value) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO tenant_resource_usage (tenant_id, metric_type, value) VALUES (?,?,?)"
             )) {
            ps.setString(1, tenantId); ps.setString(2, metricType); ps.setDouble(3, value);
            ps.executeUpdate();
        }
    }

    private void ensureAlertFired(String tenantId, String metricType, int pct, double current, double quota) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM tenant_usage_alerts " +
                 "WHERE tenant_id=? AND metric_type=? AND resolved_at IS NULL"
             )) {
            ps.setString(1, tenantId); ps.setString(2, metricType);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getLong(1) == 0) {
                    alertPort.fireAlert(tenantId, metricType, pct, current, quota);
                    alertFires.increment();
                    try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO tenant_usage_alerts (tenant_id, metric_type, threshold_pct) VALUES (?,?,?)"
                    )) {
                        ins.setString(1, tenantId); ins.setString(2, metricType); ins.setInt(3, pct);
                        ins.executeUpdate();
                    }
                }
            }
        }
    }

    private void resolveAlert(String tenantId, String metricType) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE tenant_usage_alerts SET resolved_at=NOW() " +
                 "WHERE tenant_id=? AND metric_type=? AND resolved_at IS NULL"
             )) {
            ps.setString(1, tenantId); ps.setString(2, metricType);
            int updated = ps.executeUpdate();
            if (updated > 0) alertPort.resolveAlert(tenantId, metricType);
        }
    }
}
