package com.ghatana.appplatform.deployment;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Compares the live configuration of each environment against the committed
 *              IaC baseline. Any deviation (added, removed, or changed config key) is
 *              recorded as a drift item. Operators can acknowledge drift (suppressing alerts
 *              for known intentional changes) or reject + remediate. Scheduled scans run
 *              per-environment on a configurable cadence. Satisfies STORY-K10-008.
 * @doc.layer   Kernel
 * @doc.pattern Live/baseline diffing; drift severity classification; acknowledge workflow;
 *              LiveConfigPort; BaselineConfigPort; drift.open_total Gauge.
 */
public class ConfigurationDriftScannerService {

    private final HikariDataSource   dataSource;
    private final Executor           executor;
    private final LiveConfigPort     liveConfigPort;
    private final BaselineConfigPort baselineConfigPort;
    private final AlertPort          alertPort;
    private final Counter            driftItemsDetectedCounter;
    private final AtomicLong         openDriftCount = new AtomicLong(0);

    public ConfigurationDriftScannerService(HikariDataSource dataSource, Executor executor,
                                             LiveConfigPort liveConfigPort,
                                             BaselineConfigPort baselineConfigPort,
                                             AlertPort alertPort, MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.liveConfigPort          = liveConfigPort;
        this.baselineConfigPort      = baselineConfigPort;
        this.alertPort               = alertPort;
        this.driftItemsDetectedCounter = Counter.builder("deployment.drift.items_detected_total").register(registry);
        Gauge.builder("deployment.drift.open_total", openDriftCount, AtomicLong::doubleValue).register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface LiveConfigPort {
        Map<String, String> fetchLiveConfig(String envId);
    }

    public interface BaselineConfigPort {
        Map<String, String> fetchBaselineConfig(String envId);
    }

    public interface AlertPort {
        void alert(String envId, String summary, String severity);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum DriftType { ADDED, REMOVED, CHANGED }
    public enum DriftSeverity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum DriftStatus { OPEN, ACKNOWLEDGED, REMEDIATED }

    public record DriftItem(
        String driftItemId, String scanId, String envId,
        DriftType driftType, DriftSeverity severity,
        String configKey, String baselineValue, String liveValue,
        DriftStatus status, String acknowledgedBy, String acknowledgeNote,
        Instant detectedAt
    ) {}

    public record ScanSummary(
        String scanId, String envId, int totalDriftItems,
        int criticalCount, int highCount, Instant scannedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Run a configuration drift scan for an environment. Compares live against baseline.
     */
    public Promise<ScanSummary> scanEnvironment(String envId) {
        return Promise.ofBlocking(executor, () -> {
            String scanId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            Map<String, String> live     = liveConfigPort.fetchLiveConfig(envId);
            Map<String, String> baseline = baselineConfigPort.fetchBaselineConfig(envId);

            List<DriftItem> drifts = computeDrift(scanId, envId, baseline, live, now);

            insertScan(scanId, envId, now);
            persistDriftItems(drifts);

            long critical = drifts.stream().filter(d -> d.severity() == DriftSeverity.CRITICAL).count();
            long high     = drifts.stream().filter(d -> d.severity() == DriftSeverity.HIGH).count();

            driftItemsDetectedCounter.increment(drifts.size());
            refreshOpenDriftCount(envId);

            if (critical > 0 || high > 0) {
                alertPort.alert(envId,
                    String.format("Config drift detected: %d CRITICAL, %d HIGH items", critical, high),
                    critical > 0 ? "CRITICAL" : "HIGH");
            }

            return new ScanSummary(scanId, envId, drifts.size(), (int) critical, (int) high, now);
        });
    }

    /** Acknowledge a drift item as intentional, suppressing further alerts. */
    public Promise<Void> acknowledgeDrift(String driftItemId, String acknowledgedBy, String note) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE deployment_config_drift SET status = 'ACKNOWLEDGED', " +
                     "acknowledged_by = ?, acknowledge_note = ? WHERE drift_item_id = ?")) {
                ps.setString(1, acknowledgedBy);
                ps.setString(2, note);
                ps.setString(3, driftItemId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Mark a drift item as remediated (config brought back to baseline). */
    public Promise<Void> markRemediated(String driftItemId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE deployment_config_drift SET status = 'REMEDIATED' " +
                     "WHERE drift_item_id = ?")) {
                ps.setString(1, driftItemId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** List open (unresolved) drift items for an environment. */
    public Promise<List<DriftItem>> listOpenDrift(String envId) {
        return Promise.ofBlocking(executor, () -> {
            List<DriftItem> items = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT drift_item_id, scan_id, env_id, drift_type, severity, " +
                     "config_key, baseline_value, live_value, status, acknowledged_by, " +
                     "acknowledge_note, detected_at FROM deployment_config_drift " +
                     "WHERE env_id = ? AND status = 'OPEN' ORDER BY severity DESC, detected_at ASC")) {
                ps.setString(1, envId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) items.add(mapDrift(rs));
                }
            }
            return items;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private List<DriftItem> computeDrift(String scanId, String envId,
                                          Map<String, String> baseline, Map<String, String> live,
                                          Instant now) {
        List<DriftItem> items = new ArrayList<>();

        for (Map.Entry<String, String> entry : baseline.entrySet()) {
            String key = entry.getKey();
            if (!live.containsKey(key)) {
                items.add(buildItem(scanId, envId, DriftType.REMOVED, key,
                    entry.getValue(), null, now));
            } else if (!entry.getValue().equals(live.get(key))) {
                items.add(buildItem(scanId, envId, DriftType.CHANGED, key,
                    entry.getValue(), live.get(key), now));
            }
        }

        for (String key : live.keySet()) {
            if (!baseline.containsKey(key)) {
                items.add(buildItem(scanId, envId, DriftType.ADDED, key,
                    null, live.get(key), now));
            }
        }

        return items;
    }

    private DriftItem buildItem(String scanId, String envId, DriftType type,
                                 String key, String baseline, String live, Instant now) {
        return new DriftItem(UUID.randomUUID().toString(), scanId, envId, type,
            classifySeverity(key, type), key, baseline, live,
            DriftStatus.OPEN, null, null, now);
    }

    private DriftSeverity classifySeverity(String key, DriftType type) {
        String lk = key.toLowerCase();
        if (lk.contains("secret") || lk.contains("password") || lk.contains("key")) return DriftSeverity.CRITICAL;
        if (lk.contains("database") || lk.contains("db_url") || lk.contains("endpoint")) return DriftSeverity.HIGH;
        if (type == DriftType.REMOVED) return DriftSeverity.HIGH;
        if (lk.contains("replicas") || lk.contains("memory") || lk.contains("cpu")) return DriftSeverity.MEDIUM;
        return DriftSeverity.LOW;
    }

    private void insertScan(String scanId, String envId, Instant now) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO deployment_config_drift_scans (scan_id, env_id, scanned_at) VALUES (?, ?, ?)")) {
            ps.setString(1, scanId);
            ps.setString(2, envId);
            ps.setTimestamp(3, Timestamp.from(now));
            ps.executeUpdate();
        }
    }

    private void persistDriftItems(List<DriftItem> items) throws SQLException {
        if (items.isEmpty()) return;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO deployment_config_drift " +
                 "(drift_item_id, scan_id, env_id, drift_type, severity, config_key, " +
                 "baseline_value, live_value, status, detected_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)")) {
            for (DriftItem d : items) {
                ps.setString(1, d.driftItemId());
                ps.setString(2, d.scanId());
                ps.setString(3, d.envId());
                ps.setString(4, d.driftType().name());
                ps.setString(5, d.severity().name());
                ps.setString(6, d.configKey());
                ps.setString(7, d.baselineValue());
                ps.setString(8, d.liveValue());
                ps.setTimestamp(9, Timestamp.from(d.detectedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void refreshOpenDriftCount(String envId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM deployment_config_drift WHERE env_id = ? AND status = 'OPEN'")) {
            ps.setString(1, envId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) openDriftCount.set(rs.getLong(1));
            }
        }
    }

    private DriftItem mapDrift(ResultSet rs) throws SQLException {
        return new DriftItem(
            rs.getString("drift_item_id"), rs.getString("scan_id"), rs.getString("env_id"),
            DriftType.valueOf(rs.getString("drift_type")),
            DriftSeverity.valueOf(rs.getString("severity")),
            rs.getString("config_key"), rs.getString("baseline_value"), rs.getString("live_value"),
            DriftStatus.valueOf(rs.getString("status")),
            rs.getString("acknowledged_by"), rs.getString("acknowledge_note"),
            rs.getTimestamp("detected_at").toInstant()
        );
    }
}
