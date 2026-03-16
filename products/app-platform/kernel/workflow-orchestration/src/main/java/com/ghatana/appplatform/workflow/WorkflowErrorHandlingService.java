package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Step-level retry (configurable attempts + backoff), CATCH blocks per error type,
 *              FINALLY steps, saga compensation on failure. Permanent errors skip retry.
 * @doc.layer   Workflow Orchestration (W-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-W01-007: Workflow error handling and retry
 *
 * DDL (idempotent create):
 * <pre>
 * CREATE TABLE IF NOT EXISTS workflow_step_retry_log (
 *   retry_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   instance_id     TEXT NOT NULL,
 *   step_id         TEXT NOT NULL,
 *   attempt_number  INT NOT NULL,
 *   error_type      TEXT,
 *   error_message   TEXT,
 *   next_retry_at   TIMESTAMPTZ,
 *   status          TEXT NOT NULL,   -- SCHEDULED | EXHAUSTED | RECOVERED
 *   created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class WorkflowErrorHandlingService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface StepExecutorPort {
        StepOutcome executeStep(String instanceId, String stepId, Map<String, Object> context) throws Exception;
    }

    public interface CompensationPort {
        void runCompensation(String instanceId, String fromStepId, String errorReason) throws Exception;
    }

    public interface RetrySchedulerPort {
        void scheduleRetry(String instanceId, String stepId, long delayMs, int attempt) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public enum ErrorType { TASK_ERROR, TIMEOUT, TRANSIENT, PERMANENT }

    public record StepOutcome(
        boolean success,
        Object output,
        String errorType,
        String errorMessage
    ) {}

    public record RetryPolicy(
        int maxAttempts,
        long initialBackoffMs,
        double backoffMultiplier,
        long maxBackoffMs,
        Set<ErrorType> retryableErrorTypes
    ) {}

    public record CatchBlock(ErrorType errorType, String handlerStepId) {}

    public record ErrorHandlingConfig(
        RetryPolicy retryPolicy,
        List<CatchBlock> catchBlocks,
        String finallyStepId,
        boolean enableCompensation
    ) {}

    public record StepExecutionResult(
        String instanceId,
        String stepId,
        boolean succeeded,
        int attemptsMade,
        ErrorType finalErrorType,
        String finalErrorMessage,
        String handledByCatch,
        boolean compensationTriggered
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final StepExecutorPort stepExecutor;
    private final CompensationPort compensationPort;
    private final RetrySchedulerPort retryScheduler;
    private final Executor executor;
    private final Counter retryCounter;
    private final Counter compensationCounter;
    private final Counter catchHandledCounter;
    private final Counter permanentFailCounter;

    public WorkflowErrorHandlingService(
        javax.sql.DataSource ds,
        StepExecutorPort stepExecutor,
        CompensationPort compensationPort,
        RetrySchedulerPort retryScheduler,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds              = ds;
        this.stepExecutor    = stepExecutor;
        this.compensationPort = compensationPort;
        this.retryScheduler  = retryScheduler;
        this.executor        = executor;
        this.retryCounter         = Counter.builder("workflow.error.retries").register(registry);
        this.compensationCounter  = Counter.builder("workflow.error.compensations").register(registry);
        this.catchHandledCounter  = Counter.builder("workflow.error.catch_handled").register(registry);
        this.permanentFailCounter = Counter.builder("workflow.error.permanent_failures").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Execute a workflow step with the full error handling policy applied:
     * retry on TRANSIENT, catch typed errors, always run FINALLY, compensate on unrecoverable failure.
     */
    public Promise<StepExecutionResult> executeWithErrorHandling(
        String instanceId,
        String stepId,
        Map<String, Object> context,
        ErrorHandlingConfig config
    ) {
        return Promise.ofBlocking(executor, () -> {
            int attempt = 0;
            StepOutcome lastOutcome = null;

            while (attempt < config.retryPolicy().maxAttempts()) {
                attempt++;
                try {
                    StepOutcome outcome = stepExecutor.executeStep(instanceId, stepId, context);
                    lastOutcome = outcome;

                    if (outcome.success()) {
                        runFinally(instanceId, config);
                        return new StepExecutionResult(instanceId, stepId, true, attempt,
                            null, null, null, false);
                    }

                    ErrorType errorType = parseErrorType(outcome.errorType());

                    // Permanent errors: skip retry
                    if (errorType == ErrorType.PERMANENT || !config.retryPolicy().retryableErrorTypes().contains(errorType)) {
                        logRetry(instanceId, stepId, attempt, errorType, outcome.errorMessage(), "EXHAUSTED");
                        permanentFailCounter.increment();
                        break;
                    }

                    // Check if max attempts reached
                    if (attempt >= config.retryPolicy().maxAttempts()) {
                        logRetry(instanceId, stepId, attempt, errorType, outcome.errorMessage(), "EXHAUSTED");
                        break;
                    }

                    // Schedule retry with backoff
                    long delay = computeBackoff(config.retryPolicy(), attempt);
                    logRetry(instanceId, stepId, attempt, errorType, outcome.errorMessage(), "SCHEDULED");
                    retryScheduler.scheduleRetry(instanceId, stepId, delay, attempt + 1);
                    retryCounter.increment();

                    // Wait the backoff inline for simplicity (real impl would reschedule)
                    Thread.sleep(Math.min(delay, 2000));

                } catch (Exception e) {
                    lastOutcome = new StepOutcome(false, null, ErrorType.TASK_ERROR.name(), e.getMessage());
                    if (attempt >= config.retryPolicy().maxAttempts()) break;
                }
            }

            // Check CATCH blocks
            if (lastOutcome != null && !lastOutcome.success()) {
                ErrorType errType = parseErrorType(lastOutcome.errorType());
                Optional<CatchBlock> catchBlock = config.catchBlocks().stream()
                    .filter(cb -> cb.errorType() == errType)
                    .findFirst();

                if (catchBlock.isPresent()) {
                    catchHandledCounter.increment();
                    runFinally(instanceId, config);
                    return new StepExecutionResult(instanceId, stepId, false, attempt, errType,
                        lastOutcome.errorMessage(), catchBlock.get().handlerStepId(), false);
                }

                // No catch matched — compensate if configured
                if (config.enableCompensation()) {
                    compensationPort.runCompensation(instanceId, stepId, lastOutcome.errorMessage());
                    compensationCounter.increment();
                    runFinally(instanceId, config);
                    return new StepExecutionResult(instanceId, stepId, false, attempt, errType,
                        lastOutcome.errorMessage(), null, true);
                }
            }

            runFinally(instanceId, config);
            ErrorType finalErr = lastOutcome != null ? parseErrorType(lastOutcome.errorType()) : ErrorType.TASK_ERROR;
            String finalMsg = lastOutcome != null ? lastOutcome.errorMessage() : "Unknown error";
            return new StepExecutionResult(instanceId, stepId, false, attempt, finalErr, finalMsg, null, false);
        });
    }

    /** Schedule a retry for a step (called by the retry scheduler delivering its deferred callback). */
    public Promise<Void> scheduleRetry(String instanceId, String stepId, int attemptNumber, long delayMs) {
        return Promise.ofBlocking(executor, () -> {
            retryScheduler.scheduleRetry(instanceId, stepId, delayMs, attemptNumber);
            retryCounter.increment();
            return null;
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void runFinally(String instanceId, ErrorHandlingConfig config) {
        if (config.finallyStepId() != null) {
            try {
                stepExecutor.executeStep(instanceId, config.finallyStepId(), Map.of());
            } catch (Exception ignored) {}
        }
    }

    private long computeBackoff(RetryPolicy policy, int attempt) {
        long delay = (long) (policy.initialBackoffMs() * Math.pow(policy.backoffMultiplier(), attempt - 1));
        return Math.min(delay, policy.maxBackoffMs());
    }

    private ErrorType parseErrorType(String raw) {
        if (raw == null) return ErrorType.TASK_ERROR;
        try { return ErrorType.valueOf(raw); } catch (Exception e) { return ErrorType.TASK_ERROR; }
    }

    private void logRetry(String instanceId, String stepId, int attempt, ErrorType errorType, String errorMsg, String status) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO workflow_step_retry_log " +
                 "(instance_id, step_id, attempt_number, error_type, error_message, status) " +
                 "VALUES (?,?,?,?,?,?)"
             )) {
            ps.setString(1, instanceId);
            ps.setString(2, stepId);
            ps.setInt(3, attempt);
            ps.setString(4, errorType.name());
            ps.setString(5, errorMsg);
            ps.setString(6, status);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }
}
