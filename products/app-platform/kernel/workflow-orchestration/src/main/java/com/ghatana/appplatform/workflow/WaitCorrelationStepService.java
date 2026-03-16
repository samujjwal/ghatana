package com.ghatana.appplatform.workflow;

import com.ghatana.platform.workflow.WorkflowWaitCoordinator;
import com.ghatana.platform.workflow.WorkflowWaitCoordinator.WaitCondition;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.time.Instant;
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

    private final WorkflowWaitCoordinator waitCoordinator;
    private final CronSchedulerPort cronScheduler;
    private final EventBusPort eventBus;
    private final WorkflowResumePort resumePort;
    private final Executor executor;
    private final Counter waitCreatedCounter;
    private final Counter waitSignalledCounter;
    private final Counter waitExpiredCounter;

    public WaitCorrelationStepService(
        WorkflowWaitCoordinator waitCoordinator,
        CronSchedulerPort cronScheduler,
        EventBusPort eventBus,
        WorkflowResumePort resumePort,
        MeterRegistry registry,
        Executor executor
    ) {
        this.waitCoordinator = waitCoordinator;
        this.cronScheduler   = cronScheduler;
        this.eventBus        = eventBus;
        this.resumePort      = resumePort;
        this.executor        = executor;
        this.waitCreatedCounter   = Counter.builder("workflow.wait.created").register(registry);
        this.waitSignalledCounter = Counter.builder("workflow.wait.signalled").register(registry);
        this.waitExpiredCounter   = Counter.builder("workflow.wait.expired").register(registry);
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
        String waitId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        String timeoutAt = timeoutMs > 0 ? Instant.now().plusMillis(timeoutMs).toString() : null;

        WaitCondition condition = WaitCondition.forEvent(eventType, correlationKey);
        return waitCoordinator.registerWait(instanceId, condition)
            .then(v -> Promise.ofBlocking(executor, () -> {
                eventBus.subscribe(eventType, correlationKey, waitId);
                waitCreatedCounter.increment();
                return loadWaitState(waitId, instanceId, stepId, WaitType.EVENT, correlationKey,
                    correlationExpr, null, timeoutAt, expiry, WaitStatus.WAITING, null, null, now);
            }));
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
        String waitId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        // Register as manual-approval wait; the cron scheduler signals externally when it fires
        WaitCondition condition = WaitCondition.forManualApproval();
        return waitCoordinator.registerWait(instanceId, condition)
            .then(v -> Promise.ofBlocking(executor, () -> {
                cronScheduler.scheduleOnce(cronExpression, "workflow.schedule.signal",
                    "{\"waitId\":\"" + waitId + "\"}");
                waitCreatedCounter.increment();
                return loadWaitState(waitId, instanceId, stepId, WaitType.SCHEDULE, null, null,
                    cronExpression, null, expiry, WaitStatus.WAITING, null, null, now);
            }));
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
        String waitId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        String timeoutAt = timeoutMs > 0 ? Instant.now().plusMillis(timeoutMs).toString() : null;

        // Use timer-based wait if timeout is specified (enables expiry detection), else manual
        WaitCondition condition = timeoutMs > 0
            ? WaitCondition.forTimer(Duration.ofMillis(timeoutMs))
            : WaitCondition.forManualApproval();
        return waitCoordinator.registerWait(instanceId, condition)
            .map(v -> {
                waitCreatedCounter.increment();
                return loadWaitState(waitId, instanceId, stepId, WaitType.MANUAL, null, null,
                    null, timeoutAt, expiry, WaitStatus.WAITING, null, null, now);
            });
    }

    /**
     * Send an event signal that may resolve waiting instances via correlation.
     */
    public Promise<Integer> signal(SignalRequest request) {
        return waitCoordinator.signal(request.instanceId(), request.eventType(), request.payload())
            .map(resolved -> {
                if (resolved) {
                    try {
                        resumePort.resume(request.instanceId(), null, request.payload());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to resume instance " + request.instanceId(), e);
                    }
                    waitSignalledCounter.increment();
                    return 1;
                }
                return 0;
            });
    }

    /**
     * Process expired wait states (called by a scheduled job every minute).
     */
    public Promise<Integer> processExpiredWaits() {
        return waitCoordinator.findFirableWaits(Instant.now())
            .map(firableRunIds -> {
                for (String runId : firableRunIds) {
                    waitCoordinator.cancel(runId);
                    try {
                        resumePort.handleExpiry(runId, null, ExpiryAction.FAIL.name());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to handle expiry for run " + runId, e);
                    }
                    waitExpiredCounter.increment();
                }
                return firableRunIds.size();
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
}
