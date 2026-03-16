package com.ghatana.appplatform.certification;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Certificate revocation and emergency kill-switch for certified plugins.
 *              Two revocation types: VOLUNTARY (developer initiated) and FORCED (operator / compliance).
 *              Propagation target: all K-04 plugin runtimes must be notified within 60 seconds.
 *              Emergency kill: operator terminates all running instances of a plugin platform-wide
 *              without waiting for the next maintenance window.
 *              Stores revocation reason and timestamp; emits CertificateRevoked event.
 * @doc.layer   Pack Certification (P-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-P01-011: Certificate revocation and platform-wide kill-switch
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS certificate_revocations (
 *   revocation_id    TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   plugin_id        TEXT NOT NULL,
 *   version          TEXT NOT NULL,   -- '*' for all versions (emergency kill)
 *   revocation_type  TEXT NOT NULL,   -- VOLUNTARY | FORCED | EMERGENCY_KILL
 *   reason           TEXT NOT NULL,
 *   revoked_by       TEXT NOT NULL,
 *   revoked_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   propagated_at    TIMESTAMPTZ,
 *   propagation_ms   BIGINT
 * );
 * </pre>
 */
public class CertificateRevocationKillService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface K04PropagationPort {
        /** Notify all K-04 runtime nodes to stop accepting the plugin. Must complete within 60 s. */
        void propagateRevocation(String pluginId, String version) throws Exception;
        /** Kill all currently executing instances of pluginId (all versions). */
        void killAllInstances(String pluginId) throws Exception;
    }

    public interface EventBusPort {
        void publish(String topic, Map<String, String> payload) throws Exception;
    }

    public interface K07AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private static final long PROPAGATION_SLA_MS = 60_000L;

    private final javax.sql.DataSource ds;
    private final K04PropagationPort propagation;
    private final EventBusPort eventBus;
    private final K07AuditPort audit;
    private final Executor executor;
    private final Counter revocationsCounter;
    private final Counter emergencyKillsCounter;

    public CertificateRevocationKillService(
        javax.sql.DataSource ds,
        K04PropagationPort propagation,
        EventBusPort eventBus,
        K07AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                  = ds;
        this.propagation         = propagation;
        this.eventBus            = eventBus;
        this.audit               = audit;
        this.executor            = executor;
        this.revocationsCounter  = Counter.builder("certification.revocation.count").register(registry);
        this.emergencyKillsCounter = Counter.builder("certification.revocation.emergency_kills").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Developer-initiated voluntary revocation of a specific version. */
    public Promise<Void> revokeVoluntary(String pluginId, String version, String reason, String requestedBy) {
        return revoke(pluginId, version, "VOLUNTARY", reason, requestedBy);
    }

    /** Operator/compliance-initiated forced revocation of a specific version. */
    public Promise<Void> revokeForced(String pluginId, String version, String reason, String requestedBy) {
        return revoke(pluginId, version, "FORCED", reason, requestedBy);
    }

    /**
     * Emergency kill-switch: kills all running instances of a plugin across all versions
     * and revokes all certificates immediately. Propagation must complete within 60 seconds.
     */
    public Promise<Void> emergencyKill(String pluginId, String reason, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            long start = System.currentTimeMillis();

            // Kill running instances first
            propagation.killAllInstances(pluginId);

            // Revoke all VALID certificates for this plugin
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE plugin_certificates SET status='REVOKED', revoked_at=NOW(), revoke_reason=? " +
                     "WHERE plugin_id=? AND status='VALID'"
                 )) {
                ps.setString(1, reason); ps.setString(2, pluginId); ps.executeUpdate();
            }

            // Propagate revocation to all K-04 runtimes
            propagation.propagateRevocation(pluginId, "*");

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > PROPAGATION_SLA_MS) {
                audit.record(requestedBy, "EMERGENCY_KILL_SLA_BREACH",
                    "pluginId=" + pluginId + " elapsedMs=" + elapsed);
            }

            persistRevocation(pluginId, "*", "EMERGENCY_KILL", reason, requestedBy, elapsed);
            publishEvent(pluginId, "*", "EMERGENCY_KILL", reason);
            audit.record(requestedBy, "EMERGENCY_KILL", "pluginId=" + pluginId + " reason=" + reason);
            emergencyKillsCounter.increment();
            return null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Promise<Void> revoke(String pluginId, String version, String type, String reason, String revokedBy) {
        return Promise.ofBlocking(executor, () -> {
            long start = System.currentTimeMillis();
            // Revoke certificate record
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE plugin_certificates SET status='REVOKED', revoked_at=NOW(), revoke_reason=? " +
                     "WHERE plugin_id=? AND version=? AND status='VALID'"
                 )) {
                ps.setString(1, reason); ps.setString(2, pluginId); ps.setString(3, version);
                ps.executeUpdate();
            }
            propagation.propagateRevocation(pluginId, version);
            long elapsed = System.currentTimeMillis() - start;
            persistRevocation(pluginId, version, type, reason, revokedBy, elapsed);
            publishEvent(pluginId, version, type, reason);
            audit.record(revokedBy, "CERTIFICATE_REVOKED",
                "pluginId=" + pluginId + " version=" + version + " type=" + type);
            revocationsCounter.increment();
            return null;
        });
    }

    private void persistRevocation(String pluginId, String version, String type,
                                    String reason, String revokedBy, long elapsedMs) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO certificate_revocations (plugin_id, version, revocation_type, reason, revoked_by, propagated_at, propagation_ms) " +
                 "VALUES (?,?,?,?,?, NOW(), ?)"
             )) {
            ps.setString(1, pluginId);  ps.setString(2, version);    ps.setString(3, type);
            ps.setString(4, reason);    ps.setString(5, revokedBy);  ps.setLong(6, elapsedMs);
            ps.executeUpdate();
        }
    }

    private void publishEvent(String pluginId, String version, String type, String reason) {
        try {
            eventBus.publish("certification.revoked", Map.of(
                "pluginId", pluginId, "version", version, "type", type, "reason", reason));
        } catch (Exception ignored) {
            // best-effort event publication; revocation is already persisted
        }
    }
}
