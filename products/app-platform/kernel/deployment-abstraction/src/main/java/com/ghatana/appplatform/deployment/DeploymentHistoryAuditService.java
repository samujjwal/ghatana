package com.ghatana.appplatform.deployment;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Immutable, append-only audit trail for all deployment lifecycle events.
 *              Records who deployed what to which environment and when, including rollbacks,
 *              promotion decisions, and approval outcomes. Satisfies K-07 audit compliance
 *              requirements for the deployment subsystem. Satisfies STORY-K10-007.
 * @doc.layer   Kernel
 * @doc.pattern Append-only audit log; K-07 AuditPort pattern (inner interface);
 *              query by env/artifact/time-range; deployment.audit.events_recorded_total Counter.
 */
public class DeploymentHistoryAuditService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Counter          eventsRecordedCounter;

    public DeploymentHistoryAuditService(HikariDataSource dataSource, Executor executor,
                                          MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.eventsRecordedCounter = Counter.builder("deployment.audit.events_recorded_total").register(registry);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public record AuditEvent(
        String eventId, String envId, String artifactId, String artifactVersion,
        String eventType, String actor, String detail, Instant occurredAt
    ) {}

    public record AuditQuery(
        String envId, String artifactId, String artifactVersion,
        String eventType, Instant from, Instant to, int limit
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Record a deployment lifecycle event. Immutable — no update/delete allowed.
     */
    public Promise<String> record(String envId, String artifactId, String artifactVersion,
                                   String eventType, String actor, String detail) {
        return Promise.ofBlocking(executor, () -> {
            String eventId = UUID.randomUUID().toString();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO deployment_audit_log " +
                     "(event_id, env_id, artifact_id, artifact_version, event_type, " +
                     "actor, detail, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())")) {
                ps.setString(1, eventId);
                ps.setString(2, envId);
                ps.setString(3, artifactId);
                ps.setString(4, artifactVersion);
                ps.setString(5, eventType);
                ps.setString(6, actor);
                ps.setString(7, detail);
                ps.executeUpdate();
            }
            eventsRecordedCounter.increment();
            return eventId;
        });
    }

    /**
     * Query the audit log with flexible filters. All filters are optional except limit.
     */
    public Promise<List<AuditEvent>> query(AuditQuery q) {
        return Promise.ofBlocking(executor, () -> {
            List<Object> params = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                "SELECT event_id, env_id, artifact_id, artifact_version, event_type, " +
                "actor, detail, occurred_at FROM deployment_audit_log WHERE 1=1");

            if (q.envId() != null) {
                sql.append(" AND env_id = ?");
                params.add(q.envId());
            }
            if (q.artifactId() != null) {
                sql.append(" AND artifact_id = ?");
                params.add(q.artifactId());
            }
            if (q.artifactVersion() != null) {
                sql.append(" AND artifact_version = ?");
                params.add(q.artifactVersion());
            }
            if (q.eventType() != null) {
                sql.append(" AND event_type = ?");
                params.add(q.eventType());
            }
            if (q.from() != null) {
                sql.append(" AND occurred_at >= ?");
                params.add(Timestamp.from(q.from()));
            }
            if (q.to() != null) {
                sql.append(" AND occurred_at <= ?");
                params.add(Timestamp.from(q.to()));
            }
            sql.append(" ORDER BY occurred_at DESC LIMIT ?");
            params.add(Math.min(q.limit(), 1000));

            List<AuditEvent> results = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new AuditEvent(
                            rs.getString("event_id"), rs.getString("env_id"),
                            rs.getString("artifact_id"), rs.getString("artifact_version"),
                            rs.getString("event_type"), rs.getString("actor"),
                            rs.getString("detail"),
                            rs.getTimestamp("occurred_at").toInstant()
                        ));
                    }
                }
            }
            return results;
        });
    }

    /**
     * Returns the full deployment history for a specific artifact version across all environments.
     */
    public Promise<List<AuditEvent>> getArtifactLifecycle(String artifactId, String artifactVersion) {
        return query(new AuditQuery(null, artifactId, artifactVersion, null, null, null, 500));
    }

    /**
     * Returns recent deployment events for an environment (last 100).
     */
    public Promise<List<AuditEvent>> getRecentEnvHistory(String envId) {
        return query(new AuditQuery(envId, null, null, null, null, null, 100));
    }
}
