package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose SUB_WORKFLOW step: invoke a child workflow from a parent.
 *              Supports synchronous (wait for child) and asynchronous (fire-and-forget) modes.
 *              Context passing: parent passes input, child returns output.
 *              Error propagation configurable. Infinite loop detection (depth limit).
 * @doc.layer   Workflow Orchestration (W-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-W01-014: Sub-workflow composition
 *
 * DDL (idempotent create):
 * <pre>
 * CREATE TABLE IF NOT EXISTS workflow_sub_invocations (
 *   invocation_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   parent_instance_id  TEXT NOT NULL,
 *   parent_step_id      TEXT NOT NULL,
 *   child_workflow_name TEXT NOT NULL,
 *   child_instance_id   TEXT,
 *   mode                TEXT NOT NULL, -- SYNC | ASYNC
 *   propagate_error     BOOLEAN NOT NULL DEFAULT true,
 *   status              TEXT NOT NULL DEFAULT 'PENDING',
 *   child_output        JSONB,
 *   child_error         TEXT,
 *   depth               INT NOT NULL DEFAULT 1,
 *   created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   completed_at        TIMESTAMPTZ
 * );
 * </pre>
 */
public class SubWorkflowCompositionService {

    /** Maximum allowed nesting depth to prevent infinite loops. */
    private static final int MAX_NESTING_DEPTH = 5;

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface WorkflowLaunchPort {
        String launch(String workflowName, Map<String, Object> input) throws Exception;
        Map<String, Object> getOutput(String childInstanceId, long pollTimeoutMs) throws Exception;
        String getStatus(String childInstanceId) throws Exception;
    }

    public interface WorkflowInstancePort {
        void completeStep(String instanceId, String stepId, Map<String, Object> output) throws Exception;
        void failStep(String instanceId, String stepId, String reason) throws Exception;
        int getDepth(String instanceId) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public enum SubWorkflowMode { SYNC, ASYNC }

    public record SubWorkflowConfig(
        String childWorkflowName,
        Map<String, Object> inputContext,
        SubWorkflowMode mode,
        boolean propagateError,
        long syncTimeoutMs
    ) {}

    public record SubInvocationResult(
        String invocationId,
        String childInstanceId,
        SubWorkflowMode mode,
        Map<String, Object> childOutput,
        boolean succeeded,
        String error
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private record InvocationEntry(String childInstanceId, String parentInstanceId, String parentStepId) {}

    private final Map<String, InvocationEntry> invocations = new ConcurrentHashMap<>();
    private final WorkflowLaunchPort workflowLaunch;
    private final WorkflowInstancePort workflowInstance;
    private final Executor executor;
    private final Counter subWorkflowSyncCounter;
    private final Counter subWorkflowAsyncCounter;
    private final Counter subWorkflowErrorCounter;
    private final Counter loopDetectedCounter;

    public SubWorkflowCompositionService(
        WorkflowLaunchPort workflowLaunch,
        WorkflowInstancePort workflowInstance,
        MeterRegistry registry,
        Executor executor
    ) {
        this.workflowLaunch   = workflowLaunch;
        this.workflowInstance = workflowInstance;
        this.executor         = executor;
        this.subWorkflowSyncCounter  = Counter.builder("workflow.sub.sync").register(registry);
        this.subWorkflowAsyncCounter = Counter.builder("workflow.sub.async").register(registry);
        this.subWorkflowErrorCounter = Counter.builder("workflow.sub.errors").register(registry);
        this.loopDetectedCounter     = Counter.builder("workflow.sub.loop_detected").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Invoke a sub-workflow from a parent step.
     * Checks nesting depth before launching to prevent infinite loops.
     */
    public Promise<SubInvocationResult> invoke(
        String parentInstanceId,
        String parentStepId,
        SubWorkflowConfig config
    ) {
        return Promise.ofBlocking(executor, () -> {
            // Depth check
            int parentDepth = workflowInstance.getDepth(parentInstanceId);
            if (parentDepth >= MAX_NESTING_DEPTH) {
                loopDetectedCounter.increment();
                String err = "Sub-workflow nesting depth " + parentDepth + " exceeds limit " + MAX_NESTING_DEPTH;
                workflowInstance.failStep(parentInstanceId, parentStepId, err);
                return new SubInvocationResult(null, null, config.mode(), null, false, err);
            }

            String invocationId = UUID.randomUUID().toString();

            // Launch child workflow
            String childInstanceId;
            try {
                childInstanceId = workflowLaunch.launch(config.childWorkflowName(), config.inputContext());
            } catch (Exception e) {
                subWorkflowErrorCounter.increment();
                if (config.propagateError()) {
                    workflowInstance.failStep(parentInstanceId, parentStepId, "Child launch failed: " + e.getMessage());
                }
                return new SubInvocationResult(invocationId, null, config.mode(), null, false, e.getMessage());
            }

            // Track invocation for async child checking
            invocations.put(invocationId, new InvocationEntry(childInstanceId, parentInstanceId, parentStepId));

            if (config.mode() == SubWorkflowMode.ASYNC) {
                subWorkflowAsyncCounter.increment();
                workflowInstance.completeStep(parentInstanceId, parentStepId,
                    Map.of("childInstanceId", childInstanceId, "mode", "ASYNC"));
                return new SubInvocationResult(invocationId, childInstanceId, SubWorkflowMode.ASYNC, null, true, null);
            }

            // SYNC: poll for child completion
            try {
                Map<String, Object> childOutput = workflowLaunch.getOutput(childInstanceId, config.syncTimeoutMs());
                invocations.remove(invocationId);
                workflowInstance.completeStep(parentInstanceId, parentStepId,
                    Map.of("childInstanceId", childInstanceId, "output", childOutput));
                subWorkflowSyncCounter.increment();
                return new SubInvocationResult(invocationId, childInstanceId, SubWorkflowMode.SYNC, childOutput, true, null);
            } catch (Exception e) {
                invocations.remove(invocationId);
                subWorkflowErrorCounter.increment();
                if (config.propagateError()) {
                    workflowInstance.failStep(parentInstanceId, parentStepId, "Child failed: " + e.getMessage());
                } else {
                    workflowInstance.completeStep(parentInstanceId, parentStepId,
                        Map.of("childError", e.getMessage(), "propagated", false));
                }
                return new SubInvocationResult(invocationId, childInstanceId, SubWorkflowMode.SYNC, null, false, e.getMessage());
            }
        });
    }

    /** Check the status of an async child workflow and update the parent if complete. */
    public Promise<String> checkAsyncChild(String invocationId) {
        return Promise.ofBlocking(executor, () -> {
            InvocationEntry entry = invocations.get(invocationId);
            if (entry == null) return "NOT_FOUND";

            String status = workflowLaunch.getStatus(entry.childInstanceId());
            if ("COMPLETED".equals(status)) {
                Map<String, Object> output = workflowLaunch.getOutput(entry.childInstanceId(), 0);
                invocations.remove(invocationId);
                workflowInstance.completeStep(entry.parentInstanceId(),
                    "_async_child_complete:" + entry.parentStepId(), output);
            } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                invocations.remove(invocationId);
            }
            return status;
        });
    }
}
