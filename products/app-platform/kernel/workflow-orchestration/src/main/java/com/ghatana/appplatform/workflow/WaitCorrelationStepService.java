package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Implements WAIT step types: EVENT (correlated by instance_id or custom key),
 *              SCHEDULE (cron expression via K-15 scheduler), and MANUAL (API signal).
 *              Handles expiry with configurable action (CONTINUE/FAIL/COMPENSATE).
 * @doc.layer   Workflow Orchestration (W-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-W01-006: Wait / correlation step
 *
 * DDL (idempotent create):
 * <pre>
 * CREATE TABLE IF NOT EXISTS workflow_wait_states (
 *   wait_id         TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   instance_id     TEXT NOT NULL,
 *   step_id         TEXT NOT NULL,
 *   wait_type       TEXT NOT NULL,          -- EVENT | SCHEDULE | MANUAL
 *   correlation_key TEXT,
 *   correlation_expr TEXT,                  -- CEL expression
 *   cron_expression TEXT,
 *   timeout_at      TIMESTAMPTZ,
 *   expiry_action   TEXT NOT NULL DEFAULT 'FAIL',
 *   status          TEXT NOT NULL DEFAULT 'WAITING',
 *   signalled_at    TIMESTAMPTZ,
 *   signal_payload  JSONB,
 *   created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS workflow_correlations (
 *   correlation_id  TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   wait_id         TEXT NOT NULL REFERENCES workflow_wait_states(wait_id),
 *   event_type      TEXT NOT NULL,
 *   correlation_key TEXT NOT NULL,
 *   matched_at      TIMESTAMPTZ
 * );
 * </pre>
 */
public class WaitCorrelationStepService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface CronSchedulerPort {
        /** Schedule a one-time callback at the next cron occurrence. Returns job ID. */
        String scheduleOnce(String cronExpression, String callbackTopic, String payload) throws Exception;
        void cancel(String jobId) throws Exception;
    }

    public interface EventBusPort {
        /** Subscribe to the given event type. When received, call the signal handler by correlation key. */
        void subscribe(String eventType, String correlationKey, String waitId) throws Exception;
        void unsubscribe(String waitId) throws Exception;
    }

    public interface WorkflowResumePort {
        /** Resume a workflow instance from a WAIT step with the given signal payload. */
        void resume(String instanceId, String stepId, Map<String, Object> signalPayload) throws Exception;
        /** Handle expiry action (FAIL or COMPENSATE) on the running instance. */
        void handleExpiry(String instanceId, String stepId, String expiryAction) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public enum WaitType { EVENT, SCHEDULE, MANUAL }
    public enum ExpiryAction { CONTINUE, FAIL, COMPENSATE }
    public enum WaitStatus { WAITING, SIGNALLED, EXPIRED, CANCELLED }

    public record WaitState(
        String waitId,
        String instanceId,
        String stepId,
        WaitType waitType,
        String correlationKey,
        String correlationExpr,
        String cronExpression,
        String timeoutAt,
        ExpiryAction expiryAction,
        WaitStatus status,
        String signalledAt,
        Map<String, Object> signalPayload,
        String createdAt
    ) {}

    public record SignalRequest(
        String instanceId,
        String eventType,
        String correlationKey,
        Map<String, Object> payload
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final CronSchedulerPort cronScheduler;
    private final EventBusPort eventBus;
    private final WorkflowResumePort resumePort;
    private final Executor executor;
    private final Counter waitCreatedCounter;
    private final Counter waitSignalledCounter;
    private final Counter waitExpiredCounter;

    public WaitCorrelationStepService(
        javax.sql.DataSource ds,
        CronSchedulerPort cronScheduler,
        EventBusPort eventBus,
        WorkflowResumePort resumePort,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds           = ds;
        this.cronScheduler = cronScheduler;
        this.eventBus     = eventBus;
        this.resumePort   = resumePort;
        this.executor     = executor;
        this.waitCreatedCounter  = Counter.builder("workflow.wait.created").register(registry);
        this.waitSignalledCounter = Counter.builder("workflow.wait.signalled").register(registry);
        this.waitExpiredCounter  = Counter.builder("workflow.wait.expired").register(registry);

        Gauge.builder("workflow.wait.active", ds, d -> {
            try (Connection c = d.getConnection(); PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM workflow_wait_states WHERE status='WAITING'");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            } catch (Exception e) { return 0; }
        }).register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Register an EVENT wait: instance pauses until an external event matching the correlation
     * expression arrives or the timeout elapses.
     */
    public Promise<WaitState> waitForEvent(
        String instanceId,
        String stepId,
        String eventType,
        String correlationKey,
        String correlationExpr,
        long timeoutMs,
        ExpiryAction expiry
    ) {
        return Promise.ofBlocking(executor, () -> {
            String timeoutAt = timeoutMs > 0
                ? java.time.Instant.now().plusMillis(timeoutMs).toString()
                : null;

            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO workflow_wait_states " +
                     "(instance_id, step_id, wait_type, correlation_key, correlation_expr, timeout_at, expiry_action) " +
                     "VALUES (?,?,?,?,?,?::timestamptz,?) RETURNING wait_id, created_at"
                 )) {
                ps.setString(1, instanceId);
                ps.setString(2, stepId);
                ps.setString(3, WaitType.EVENT.name());
                ps.setString(4, correlationKey);
                ps.setString(5, correlationExpr);
                ps.setString(6, timeoutAt);
                ps.setString(7, expiry.name());

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String waitId = rs.getString("wait_id");
                    String createdAt = rs.getTimestamp("created_at").toString();

                    // Subscribe to incoming event bus
                    eventBus.subscribe(eventType, correlationKey, waitId);
                    waitCreatedCounter.increment();

                    return loadWaitState(waitId, instanceId, stepId, WaitType.EVENT, correlationKey,
                        correlationExpr, null, timeoutAt, expiry, WaitStatus.WAITING, null, null, createdAt);
                }
            }
        });
    }

    /**
     * Register a SCHEDULE wait: instance pauses until a cron expression fires.
     */
    public Promise<WaitState> waitForSchedule(
        String instanceId,
        String stepId,
        String cronExpression,
        ExpiryAction expiry
    ) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO workflow_wait_states " +
                     "(instance_id, step_id, wait_type, cron_expression, expiry_action) " +
                     "VALUES (?,?,?,?,?) RETURNING wait_id, created_at"
                 )) {
                ps.setString(1, instanceId);
                ps.setString(2, stepId);
                ps.setString(3, WaitType.SCHEDULE.name());
                ps.setString(4, cronExpression);
                ps.setString(5, expiry.name());

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String waitId = rs.getString("wait_id");
                    String createdAt = rs.getTimestamp("created_at").toString();

                    // Register cron trigger
                    cronScheduler.scheduleOnce(cronExpression, "workflow.schedule.signal",
                        "{\"waitId\":\"" + waitId + "\"}");
                    waitCreatedCounter.increment();

                    return loadWaitState(waitId, instanceId, stepId, WaitType.SCHEDULE, null, null,
                        cronExpression, null, expiry, WaitStatus.WAITING, null, null, createdAt);
                }
            }
        });
    }

    /**
     * Register a MANUAL wait: instance pauses until an operator calls signalManual().
     */
    public Promise<WaitState> waitForManualSignal(
        String instanceId,
        String stepId,
        long timeoutMs,
        ExpiryAction expiry
    ) {
        return Promise.ofBlocking(executor, () -> {
            String timeoutAt = timeoutMs > 0
                ? java.time.Instant.now().plusMillis(timeoutMs).toString()
                : null;

            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO workflow_wait_states " +
                     "(instance_id, step_id, wait_type, timeout_at, expiry_action) " +
                     "VALUES (?,?,?,?::timestamptz,?) RETURNING wait_id, created_at"
                 )) {
                ps.setString(1, instanceId);
                ps.setString(2, stepId);
                ps.setString(3, WaitType.MANUAL.name());
                ps.setString(4, timeoutAt);
                ps.setString(5, expiry.name());

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String waitId = rs.getString("wait_id");
                    String createdAt = rs.getTimestamp("created_at").toString();
                    waitCreatedCounter.increment();
                    return loadWaitState(waitId, instanceId, stepId, WaitType.MANUAL, null, null,
                        null, timeoutAt, expiry, WaitStatus.WAITING, null, null, createdAt);
                }
            }
        });
    }

    /**
     * Send an event signal that may resolve waiting instances via correlation.
     */
    public Promise<Integer> signal(SignalRequest request) {
        return Promise.ofBlocking(executor, () -> {
            // Find matching wait states by correlation key
            List<String[]> matches = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT wait_id, instance_id, step_id, expiry_action FROM workflow_wait_states " +
                     "WHERE wait_type='EVENT' AND correlation_key=? AND status='WAITING'"
                 )) {
                ps.setString(1, request.correlationKey());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        matches.add(new String[]{
                            rs.getString("wait_id"), rs.getString("instance_id"),
                            rs.getString("step_id"), rs.getString("expiry_action")
                        });
                    }
                }
            }

            int resolved = 0;
            for (String[] match : matches) {
                try (Connection c = ds.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "UPDATE workflow_wait_states SET status='SIGNALLED', signalled_at=NOW(), signal_payload=?::jsonb " +
                         "WHERE wait_id=?"
                     )) {
                    ps.setString(1, mapToJson(request.payload()));
                    ps.setString(2, match[0]);
                    ps.executeUpdate();
                }
                resumePort.resume(match[1], match[2], request.payload());
                waitSignalledCounter.increment();
                resolved++;
            }
            return resolved;
        });
    }

    /**
     * Process expired wait states (called by a scheduled job every minute).
     */
    public Promise<Integer> processExpiredWaits() {
        return Promise.ofBlocking(executor, () -> {
            List<String[]> expired = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE workflow_wait_states SET status='EXPIRED' " +
                     "WHERE status='WAITING' AND timeout_at IS NOT NULL AND timeout_at < NOW() " +
                     "RETURNING wait_id, instance_id, step_id, expiry_action"
                 );
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    expired.add(new String[]{rs.getString("wait_id"), rs.getString("instance_id"),
                        rs.getString("step_id"), rs.getString("expiry_action")});
                }
            }

            for (String[] e : expired) {
                resumePort.handleExpiry(e[1], e[2], e[3]);
                waitExpiredCounter.increment();
            }
            return expired.size();
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private WaitState loadWaitState(String waitId, String instanceId, String stepId,
        WaitType waitType, String corrKey, String corrExpr, String cron, String timeout,
        ExpiryAction expiry, WaitStatus status, String signalledAt, Map<String, Object> payload, String createdAt
    ) {
        return new WaitState(waitId, instanceId, stepId, waitType, corrKey, corrExpr, cron,
            timeout, expiry, status, signalledAt, payload, createdAt);
    }

    private String mapToJson(Map<String, Object> map) {
        if (map == null) return "null";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
            first = false;
        }
        return sb.append("}").toString();
    }
}
