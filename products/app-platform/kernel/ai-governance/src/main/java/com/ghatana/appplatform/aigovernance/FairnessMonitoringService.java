package com.ghatana.appplatform.aigovernance;

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
 * @doc.purpose Continuous fairness monitoring in production using sliding windows of 7, 30,
 *              and 90 days. Computes fairness disparity over each window and triggers a
 *              Human-In-The-Loop (HITL) review workflow when fairness degrades beyond
 *              the configured threshold. Works across all protected attributes registered
 *              for the model. Satisfies STORY-K09-008.
 * @doc.layer   Kernel
 * @doc.pattern Sliding-window fairness evaluation; HITL trigger on degradation;
 *              window-based disparity Gauge; FairnessPort for group statistics;
 *              HitlTriggerPort for review initiation.
 */
public class FairnessMonitoringService {

    private static final int[]    WINDOWS_DAYS      = {7, 30, 90};
    private static final double   DEGRADATION_THRESHOLD = 0.1;

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final FairnessPort        fairnessPort;
    private final HitlTriggerPort     hitlTriggerPort;
    private final AtomicReference<Double> latestDisparity7d  = new AtomicReference<>(0.0);
    private final AtomicReference<Double> latestDisparity30d = new AtomicReference<>(0.0);
    private final AtomicReference<Double> latestDisparity90d = new AtomicReference<>(0.0);

    public FairnessMonitoringService(HikariDataSource dataSource, Executor executor,
                                      FairnessPort fairnessPort,
                                      HitlTriggerPort hitlTriggerPort,
                                      MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.fairnessPort    = fairnessPort;
        this.hitlTriggerPort = hitlTriggerPort;

        Gauge.builder("aigovernance.fairness.disparity_7d",  latestDisparity7d,  AtomicReference::get).register(registry);
        Gauge.builder("aigovernance.fairness.disparity_30d", latestDisparity30d, AtomicReference::get).register(registry);
        Gauge.builder("aigovernance.fairness.disparity_90d", latestDisparity90d, AtomicReference::get).register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Fetches per-group prediction stats for a time window from the prediction audit log. */
    public interface FairnessPort {
        List<Map<String, Object>> getGroupStats(String modelId, String version,
                                                 String protectedAttribute,
                                                 Instant from, Instant to);
    }

    /** Triggers a HITL review workflow when fairness degrades. */
    public interface HitlTriggerPort {
        String triggerReview(String modelId, String version, String reason);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public record WindowSnapshot(
        String modelId, String version, String protectedAttribute,
        int windowDays, double disparity, boolean hitlTriggered,
        String hitlReviewId, Instant snapshotAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Run fairness monitoring across all registered protected attributes for a model version.
     * Called on a scheduled basis (e.g. hourly) by the infrastructure scheduler.
     */
    public Promise<List<WindowSnapshot>> monitorFairness(String modelId, String version) {
        return Promise.ofBlocking(executor, () -> {
            List<String> protectedAttrs = fetchProtectedAttributes(modelId);
            List<WindowSnapshot> snapshots = new ArrayList<>();
            Instant now = Instant.now();

            for (String attr : protectedAttrs) {
                for (int days : WINDOWS_DAYS) {
                    Instant from = now.minusSeconds((long) days * 86400);
                    List<Map<String, Object>> groupStats =
                        fairnessPort.getGroupStats(modelId, version, attr, from, now);

                    double disparity = computeDisparityFromStats(groupStats);
                    updateGauge(days, disparity);

                    boolean hitlTriggered = false;
                    String hitlId = null;
                    if (disparity > DEGRADATION_THRESHOLD) {
                        String reason = String.format(
                            "Fairness disparity %.3f (threshold %.2f) in %d-day window for attribute '%s'",
                            disparity, DEGRADATION_THRESHOLD, days, attr);
                        hitlId = hitlTriggerPort.triggerReview(modelId, version, reason);
                        hitlTriggered = true;
                    }

                    WindowSnapshot snap = new WindowSnapshot(modelId, version, attr,
                        days, disparity, hitlTriggered, hitlId, now);
                    snapshots.add(snap);
                    persistSnapshot(snap);
                }
            }
            return snapshots;
        });
    }

    /**
     * Get historical fairness snapshots for a model version.
     */
    public Promise<List<WindowSnapshot>> getHistory(String modelId, String version,
                                                     int windowDays, int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<WindowSnapshot> results = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT protected_attribute, window_days, disparity, " +
                     "hitl_triggered, hitl_review_id, snapshot_at " +
                     "FROM fairness_snapshots WHERE model_id = ? AND model_version = ? " +
                     "AND window_days = ? ORDER BY snapshot_at DESC LIMIT ?")) {
                ps.setString(1, modelId);
                ps.setString(2, version);
                ps.setInt(3, windowDays);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new WindowSnapshot(
                            modelId, version,
                            rs.getString("protected_attribute"),
                            rs.getInt("window_days"),
                            rs.getDouble("disparity"),
                            rs.getBoolean("hitl_triggered"),
                            rs.getString("hitl_review_id"),
                            rs.getTimestamp("snapshot_at").toInstant()
                        ));
                    }
                }
            }
            return results;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private double computeDisparityFromStats(List<Map<String, Object>> groupStats) {
        if (groupStats.size() < 2) return 0.0;
        double max = groupStats.stream()
            .mapToDouble(s -> ((Number) s.getOrDefault("positive_rate", 0.0)).doubleValue())
            .max().orElse(0.0);
        double min = groupStats.stream()
            .mapToDouble(s -> ((Number) s.getOrDefault("positive_rate", 0.0)).doubleValue())
            .min().orElse(0.0);
        return max - min;
    }

    private void updateGauge(int days, double disparity) {
        switch (days) {
            case 7  -> latestDisparity7d.set(disparity);
            case 30 -> latestDisparity30d.set(disparity);
            case 90 -> latestDisparity90d.set(disparity);
        }
    }

    private List<String> fetchProtectedAttributes(String modelId) throws SQLException {
        List<String> attrs = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT DISTINCT attribute_name FROM model_protected_attributes " +
                 "WHERE model_id = ?")) {
            ps.setString(1, modelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) attrs.add(rs.getString("attribute_name"));
            }
        }
        return attrs;
    }

    private void persistSnapshot(WindowSnapshot snap) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO fairness_snapshots " +
                 "(model_id, model_version, protected_attribute, window_days, " +
                 "disparity, hitl_triggered, hitl_review_id, snapshot_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, snap.modelId());
            ps.setString(2, snap.version());
            ps.setString(3, snap.protectedAttribute());
            ps.setInt(4, snap.windowDays());
            ps.setDouble(5, snap.disparity());
            ps.setBoolean(6, snap.hitlTriggered());
            ps.setString(7, snap.hitlReviewId());
            ps.setTimestamp(8, Timestamp.from(snap.snapshotAt()));
            ps.executeUpdate();
        }
    }
}
