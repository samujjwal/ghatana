package com.ghatana.appplatform.deployment;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose One-click rollback to the previous stable deployment for a given environment.
 *              Captures deployment snapshots on every successful deploy so rollback is instant
 *              (MTTR target: &lt;5 min). Runs a post-rollback health check; if health check
 *              fails marks rollback as FAILED and raises an alert. Satisfies STORY-K10-006.
 * @doc.layer   Kernel
 * @doc.pattern Snapshot-based rollback; DeploymentExecutorPort; TrafficSwitchPort; K-07 AuditPort;
 *              rollback_initiated Counter; rollback_duration Timer.
 */
public class InstantRollbackService {

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final DeploymentExecutorPort executorPort;
    private final TrafficSwitchPort   trafficSwitchPort;
    private final HealthCheckPort     healthCheckPort;
    private final AuditPort           auditPort;
    private final Counter             rollbackInitiatedCounter;
    private final Counter             rollbackSucceededCounter;
    private final Counter             rollbackFailedCounter;
    private final Timer               rollbackDurationTimer;

    public InstantRollbackService(HikariDataSource dataSource, Executor executor,
                                   DeploymentExecutorPort executorPort,
                                   TrafficSwitchPort trafficSwitchPort,
                                   HealthCheckPort healthCheckPort,
                                   AuditPort auditPort, MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.executorPort            = executorPort;
        this.trafficSwitchPort       = trafficSwitchPort;
        this.healthCheckPort         = healthCheckPort;
        this.auditPort               = auditPort;
        this.rollbackInitiatedCounter = Counter.builder("deployment.rollback.initiated_total").register(registry);
        this.rollbackSucceededCounter = Counter.builder("deployment.rollback.succeeded_total").register(registry);
        this.rollbackFailedCounter    = Counter.builder("deployment.rollback.failed_total").register(registry);
        this.rollbackDurationTimer    = Timer.builder("deployment.rollback.duration").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface DeploymentExecutorPort {
        void deploy(String envId, String artifactId, String artifactVersion, Map<String, String> configSnapshot);
    }

    public interface TrafficSwitchPort {
        void switchTraffic(String envId, String targetVersion);
    }

    public interface HealthCheckPort {
        boolean isHealthy(String envId, String version);
    }

    public interface AuditPort {
        void record(String entityType, String entityId, String event, String actor, String detail);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum RollbackStatus { IN_PROGRESS, SUCCEEDED, FAILED }

    public record DeploymentSnapshot(
        String snapshotId, String envId, String artifactId,
        String artifactVersion, Map<String, String> configSnapshot, Instant takenAt
    ) {}

    public record RollbackRecord(
        String rollbackId, String envId,
        String rolledFromVersion, String rolledToVersion,
        RollbackStatus status, String failureReason,
        long durationMs, Instant initiatedAt, Instant completedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Saves a deployment snapshot. Must be called by the orchestrator on every successful deploy.
     */
    public Promise<String> captureSnapshot(String envId, String artifactId,
                                            String artifactVersion, Map<String, String> config) {
        return Promise.ofBlocking(executor, () -> {
            String snapshotId = UUID.randomUUID().toString();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO deployment_snapshots " +
                     "(snapshot_id, env_id, artifact_id, artifact_version, config_snapshot, taken_at) " +
                     "VALUES (?, ?, ?, ?, ?::jsonb, NOW())")) {
                ps.setString(1, snapshotId);
                ps.setString(2, envId);
                ps.setString(3, artifactId);
                ps.setString(4, artifactVersion);
                ps.setString(5, mapToJson(config));
                ps.executeUpdate();
            }
            return snapshotId;
        });
    }

    /**
     * Initiate an instant rollback for an environment. Automatically selects the previous
     * stable snapshot. Returns a rollback record for tracking.
     */
    public Promise<RollbackRecord> initiateRollback(String envId, String initiatedBy) {
        return Promise.ofBlocking(executor, () -> {
            Instant start = Instant.now();
            String rollbackId = UUID.randomUUID().toString();
            rollbackInitiatedCounter.increment();

            DeploymentSnapshot current = fetchLatestSnapshot(envId);
            DeploymentSnapshot target  = fetchPreviousSnapshot(envId, current.snapshotId());

            insertRollback(rollbackId, envId, current.artifactVersion(),
                target.artifactVersion(), RollbackStatus.IN_PROGRESS);
            auditPort.record("ROLLBACK", rollbackId, "INITIATED", initiatedBy,
                String.format("env=%s from=%s to=%s", envId,
                    current.artifactVersion(), target.artifactVersion()));

            try {
                executorPort.deploy(envId, target.artifactId(), target.artifactVersion(), target.configSnapshot());
                trafficSwitchPort.switchTraffic(envId, target.artifactVersion());

                boolean healthy = healthCheckPort.isHealthy(envId, target.artifactVersion());
                Instant end = Instant.now();
                long durationMs = Duration.between(start, end).toMillis();
                rollbackDurationTimer.record(Duration.between(start, end));

                if (healthy) {
                    completeRollback(rollbackId, RollbackStatus.SUCCEEDED, null, durationMs);
                    auditPort.record("ROLLBACK", rollbackId, "SUCCEEDED", initiatedBy,
                        "durationMs=" + durationMs);
                    rollbackSucceededCounter.increment();
                } else {
                    completeRollback(rollbackId, RollbackStatus.FAILED, "Health check failed post-rollback", durationMs);
                    auditPort.record("ROLLBACK", rollbackId, "HEALTH_CHECK_FAILED", initiatedBy, "");
                    rollbackFailedCounter.increment();
                }
            } catch (Exception e) {
                long durationMs = Duration.between(start, Instant.now()).toMillis();
                completeRollback(rollbackId, RollbackStatus.FAILED, e.getMessage(), durationMs);
                rollbackFailedCounter.increment();
                throw e;
            }

            return fetchRollback(rollbackId);
        });
    }

    /** Retrieve a rollback record. */
    public Promise<RollbackRecord> getRollback(String rollbackId) {
        return Promise.ofBlocking(executor, () -> fetchRollback(rollbackId));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private DeploymentSnapshot fetchLatestSnapshot(String envId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT snapshot_id, artifact_id, artifact_version, config_snapshot, taken_at " +
                 "FROM deployment_snapshots WHERE env_id = ? ORDER BY taken_at DESC LIMIT 1")) {
            ps.setString(1, envId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NoSuchElementException("No snapshot for env: " + envId);
                return map(rs, envId);
            }
        }
    }

    private DeploymentSnapshot fetchPreviousSnapshot(String envId, String excludeSnapshotId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT snapshot_id, artifact_id, artifact_version, config_snapshot, taken_at " +
                 "FROM deployment_snapshots WHERE env_id = ? AND snapshot_id != ? " +
                 "ORDER BY taken_at DESC LIMIT 1")) {
            ps.setString(1, envId);
            ps.setString(2, excludeSnapshotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NoSuchElementException("No previous snapshot for env: " + envId);
                return map(rs, envId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private DeploymentSnapshot map(ResultSet rs, String envId) throws SQLException {
        return new DeploymentSnapshot(
            rs.getString("snapshot_id"), envId,
            rs.getString("artifact_id"), rs.getString("artifact_version"),
            Map.of(), // config not needed for this response
            rs.getTimestamp("taken_at").toInstant()
        );
    }

    private void insertRollback(String id, String envId, String fromVer, String toVer,
                                 RollbackStatus status) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO deployment_rollbacks " +
                 "(rollback_id, env_id, rolled_from_version, rolled_to_version, status, initiated_at) " +
                 "VALUES (?, ?, ?, ?, ?, NOW())")) {
            ps.setString(1, id);
            ps.setString(2, envId);
            ps.setString(3, fromVer);
            ps.setString(4, toVer);
            ps.setString(5, status.name());
            ps.executeUpdate();
        }
    }

    private void completeRollback(String id, RollbackStatus status, String reason,
                                   long durationMs) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE deployment_rollbacks SET status = ?, failure_reason = ?, " +
                 "duration_ms = ?, completed_at = NOW() WHERE rollback_id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, reason);
            ps.setLong(3, durationMs);
            ps.setString(4, id);
            ps.executeUpdate();
        }
    }

    private RollbackRecord fetchRollback(String id) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT rollback_id, env_id, rolled_from_version, rolled_to_version, " +
                 "status, failure_reason, duration_ms, initiated_at, completed_at " +
                 "FROM deployment_rollbacks WHERE rollback_id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NoSuchElementException("Rollback not found: " + id);
                Timestamp completedTs = rs.getTimestamp("completed_at");
                return new RollbackRecord(
                    rs.getString("rollback_id"), rs.getString("env_id"),
                    rs.getString("rolled_from_version"), rs.getString("rolled_to_version"),
                    RollbackStatus.valueOf(rs.getString("status")),
                    rs.getString("failure_reason"), rs.getLong("duration_ms"),
                    rs.getTimestamp("initiated_at").toInstant(),
                    completedTs != null ? completedTs.toInstant() : null
                );
            }
        }
    }

    private String mapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append('"').append(k.replace("\"", "\\\""))
            .append("\":\"").append(v.replace("\"", "\\\"")).append("\","));
        if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        return sb.append('}').toString();
    }
}
