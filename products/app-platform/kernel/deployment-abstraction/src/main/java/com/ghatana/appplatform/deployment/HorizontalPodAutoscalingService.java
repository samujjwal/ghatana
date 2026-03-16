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
 * @doc.purpose Manages Horizontal Pod Autoscaling (HPA) rules for each deployment. Stores
 *              the desired HPA policy (min/max replicas, CPU threshold %, memory threshold %,
 *              custom metric thresholds) and evaluates whether a scaling action is needed
 *              based on current metrics. Scale-up/down decisions are recorded as scaling
 *              events for auditing. Satisfies STORY-K10-010.
 * @doc.layer   Kernel
 * @doc.pattern HPA policy CRUD; metrics-driven scaling evaluation; MetricsPort; HpaExecutorPort;
 *              scale_up/scale_down Counters; current_replicas Gauge.
 */
public class HorizontalPodAutoscalingService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final MetricsPort      metricsPort;
    private final HpaExecutorPort  hpaExecutorPort;
    private final Counter          scaleUpCounter;
    private final Counter          scaleDownCounter;
    private final AtomicLong       currentReplicasSample = new AtomicLong(0);

    public HorizontalPodAutoscalingService(HikariDataSource dataSource, Executor executor,
                                            MetricsPort metricsPort, HpaExecutorPort hpaExecutorPort,
                                            MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.metricsPort     = metricsPort;
        this.hpaExecutorPort = hpaExecutorPort;
        this.scaleUpCounter  = Counter.builder("deployment.hpa.scale_up_total").register(registry);
        this.scaleDownCounter = Counter.builder("deployment.hpa.scale_down_total").register(registry);
        Gauge.builder("deployment.hpa.current_replicas_sample", currentReplicasSample,
            AtomicLong::doubleValue).register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface MetricsPort {
        double getCpuUtilizationPercent(String deploymentId);
        double getMemoryUtilizationPercent(String deploymentId);
        int getCurrentReplicas(String deploymentId);
    }

    public interface HpaExecutorPort {
        void setReplicas(String deploymentId, int targetReplicas);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum ScaleDirection { UP, DOWN, NONE }

    public record HpaPolicy(
        String policyId, String deploymentId,
        int minReplicas, int maxReplicas,
        double cpuThresholdPercent, double memoryThresholdPercent,
        double scaleUpCooldownSeconds, double scaleDownCooldownSeconds,
        boolean enabled, Instant updatedAt
    ) {}

    public record ScalingEvent(
        String eventId, String deploymentId, ScaleDirection direction,
        int fromReplicas, int toReplicas, String reason, Instant occurredAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Upsert an HPA policy for a deployment.
     */
    public Promise<Void> upsertPolicy(String deploymentId, int minReplicas, int maxReplicas,
                                       double cpuThresholdPercent, double memoryThresholdPercent,
                                       double scaleUpCooldownSeconds, double scaleDownCooldownSeconds) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO deployment_hpa_policies " +
                     "(policy_id, deployment_id, min_replicas, max_replicas, " +
                     "cpu_threshold_percent, memory_threshold_percent, " +
                     "scale_up_cooldown_seconds, scale_down_cooldown_seconds, enabled, updated_at) " +
                     "VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?, ?, TRUE, NOW()) " +
                     "ON CONFLICT (deployment_id) DO UPDATE SET " +
                     "min_replicas = EXCLUDED.min_replicas, " +
                     "max_replicas = EXCLUDED.max_replicas, " +
                     "cpu_threshold_percent = EXCLUDED.cpu_threshold_percent, " +
                     "memory_threshold_percent = EXCLUDED.memory_threshold_percent, " +
                     "scale_up_cooldown_seconds = EXCLUDED.scale_up_cooldown_seconds, " +
                     "scale_down_cooldown_seconds = EXCLUDED.scale_down_cooldown_seconds, " +
                     "enabled = TRUE, updated_at = NOW()")) {
                ps.setString(1, deploymentId);
                ps.setInt(2, minReplicas);
                ps.setInt(3, maxReplicas);
                ps.setDouble(4, cpuThresholdPercent);
                ps.setDouble(5, memoryThresholdPercent);
                ps.setDouble(6, scaleUpCooldownSeconds);
                ps.setDouble(7, scaleDownCooldownSeconds);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Evaluate scaling needs for a deployment and execute if warranted.
     */
    public Promise<ScaleDirection> evaluateAndScale(String deploymentId) {
        return Promise.ofBlocking(executor, () -> {
            HpaPolicy policy = fetchPolicy(deploymentId);
            if (!policy.enabled()) return ScaleDirection.NONE;

            double cpu    = metricsPort.getCpuUtilizationPercent(deploymentId);
            double memory = metricsPort.getMemoryUtilizationPercent(deploymentId);
            int current   = metricsPort.getCurrentReplicas(deploymentId);
            currentReplicasSample.set(current);

            boolean shouldScaleUp   = (cpu > policy.cpuThresholdPercent()
                || memory > policy.memoryThresholdPercent()) && current < policy.maxReplicas();
            boolean shouldScaleDown = (cpu < policy.cpuThresholdPercent() * 0.5
                && memory < policy.memoryThresholdPercent() * 0.5) && current > policy.minReplicas();

            if (!shouldScaleUp && !shouldScaleDown) return ScaleDirection.NONE;

            if (shouldScaleUp && !isCooldownActive(deploymentId, ScaleDirection.UP, policy.scaleUpCooldownSeconds())) {
                int target = Math.min(current + 1, policy.maxReplicas());
                hpaExecutorPort.setReplicas(deploymentId, target);
                recordScalingEvent(deploymentId, ScaleDirection.UP, current, target,
                    String.format("cpu=%.1f%% mem=%.1f%%", cpu, memory));
                scaleUpCounter.increment();
                return ScaleDirection.UP;
            }

            if (shouldScaleDown && !isCooldownActive(deploymentId, ScaleDirection.DOWN, policy.scaleDownCooldownSeconds())) {
                int target = Math.max(current - 1, policy.minReplicas());
                hpaExecutorPort.setReplicas(deploymentId, target);
                recordScalingEvent(deploymentId, ScaleDirection.DOWN, current, target,
                    String.format("cpu=%.1f%% mem=%.1f%%", cpu, memory));
                scaleDownCounter.increment();
                return ScaleDirection.DOWN;
            }

            return ScaleDirection.NONE;
        });
    }

    /** Retrieve HPA policy for a deployment. */
    public Promise<HpaPolicy> getPolicy(String deploymentId) {
        return Promise.ofBlocking(executor, () -> fetchPolicy(deploymentId));
    }

    /** List recent scaling events for a deployment. */
    public Promise<List<ScalingEvent>> getRecentScalingEvents(String deploymentId, int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<ScalingEvent> events = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT event_id, deployment_id, direction, from_replicas, to_replicas, " +
                     "reason, occurred_at FROM deployment_hpa_events " +
                     "WHERE deployment_id = ? ORDER BY occurred_at DESC LIMIT ?")) {
                ps.setString(1, deploymentId);
                ps.setInt(2, Math.min(limit, 200));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        events.add(new ScalingEvent(
                            rs.getString("event_id"), rs.getString("deployment_id"),
                            ScaleDirection.valueOf(rs.getString("direction")),
                            rs.getInt("from_replicas"), rs.getInt("to_replicas"),
                            rs.getString("reason"),
                            rs.getTimestamp("occurred_at").toInstant()
                        ));
                    }
                }
            }
            return events;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private HpaPolicy fetchPolicy(String deploymentId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT policy_id, deployment_id, min_replicas, max_replicas, " +
                 "cpu_threshold_percent, memory_threshold_percent, " +
                 "scale_up_cooldown_seconds, scale_down_cooldown_seconds, enabled, updated_at " +
                 "FROM deployment_hpa_policies WHERE deployment_id = ?")) {
            ps.setString(1, deploymentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NoSuchElementException("HPA policy not found: " + deploymentId);
                return new HpaPolicy(
                    rs.getString("policy_id"), rs.getString("deployment_id"),
                    rs.getInt("min_replicas"), rs.getInt("max_replicas"),
                    rs.getDouble("cpu_threshold_percent"), rs.getDouble("memory_threshold_percent"),
                    rs.getDouble("scale_up_cooldown_seconds"), rs.getDouble("scale_down_cooldown_seconds"),
                    rs.getBoolean("enabled"), rs.getTimestamp("updated_at").toInstant()
                );
            }
        }
    }

    private boolean isCooldownActive(String deploymentId, ScaleDirection direction,
                                      double cooldownSeconds) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM deployment_hpa_events " +
                 "WHERE deployment_id = ? AND direction = ? " +
                 "AND occurred_at > NOW() - make_interval(secs => ?)")) {
            ps.setString(1, deploymentId);
            ps.setString(2, direction.name());
            ps.setDouble(3, cooldownSeconds);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        }
    }

    private void recordScalingEvent(String deploymentId, ScaleDirection direction,
                                     int from, int to, String reason) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO deployment_hpa_events " +
                 "(event_id, deployment_id, direction, from_replicas, to_replicas, reason, occurred_at) " +
                 "VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, NOW())")) {
            ps.setString(1, deploymentId);
            ps.setString(2, direction.name());
            ps.setInt(3, from);
            ps.setInt(4, to);
            ps.setString(5, reason);
            ps.executeUpdate();
        }
    }
}
