package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
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

    private final javax.sql.DataSource ds;
    private final WorkflowLaunchPort workflowLaunch;
    private final WorkflowInstancePort workflowInstance;
    private final Executor executor;
    private final Counter subWorkflowSyncCounter;
    private final Counter subWorkflowAsyncCounter;
    private final Counter subWorkflowErrorCounter;
    private final Counter loopDetectedCounter;

    public SubWorkflowCompositionService(
        javax.sql.DataSource ds,
        WorkflowLaunchPort workflowLaunch,
        WorkflowInstancePort workflowInstance,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
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

            // Persist invocation record
            String invocationId = persistInvocation(parentInstanceId, parentStepId, config, parentDepth + 1);

            // Launch child workflow
            String childInstanceId;
            try {
                childInstanceId = workflowLaunch.launch(config.childWorkflowName(), config.inputContext());
                updateChildInstanceId(invocationId, childInstanceId);
            } catch (Exception e) {
                updateStatus(invocationId, "LAUNCH_FAILED", null, e.getMessage());
                subWorkflowErrorCounter.increment();
                if (config.propagateError()) {
                    workflowInstance.failStep(parentInstanceId, parentStepId, "Child launch failed: " + e.getMessage());
                }
                return new SubInvocationResult(invocationId, null, config.mode(), null, false, e.getMessage());
            }

            if (config.mode() == SubWorkflowMode.ASYNC) {
                subWorkflowAsyncCounter.increment();
                updateStatus(invocationId, "ASYNC_RUNNING", null, null);
                workflowInstance.completeStep(parentInstanceId, parentStepId,
                    Map.of("childInstanceId", childInstanceId, "mode", "ASYNC"));
                return new SubInvocationResult(invocationId, childInstanceId, SubWorkflowMode.ASYNC, null, true, null);
            }

            // SYNC: poll for child completion
            try {
                Map<String, Object> childOutput = workflowLaunch.getOutput(childInstanceId, config.syncTimeoutMs());
                updateStatus(invocationId, "COMPLETED", childOutput, null);
                workflowInstance.completeStep(parentInstanceId, parentStepId,
                    Map.of("childInstanceId", childInstanceId, "output", childOutput));
                subWorkflowSyncCounter.increment();
                return new SubInvocationResult(invocationId, childInstanceId, SubWorkflowMode.SYNC, childOutput, true, null);
            } catch (Exception e) {
                updateStatus(invocationId, "CHILD_FAILED", null, e.getMessage());
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
            Optional<String[]> inv = loadInvocation(invocationId);
            if (inv.isEmpty()) return "NOT_FOUND";
            String[] row = inv.get();
            String childInstanceId = row[0];
            String parentInstanceId = row[1];
            String parentStepId = row[2];

            String status = workflowLaunch.getStatus(childInstanceId);
            if ("COMPLETED".equals(status)) {
                Map<String, Object> output = workflowLaunch.getOutput(childInstanceId, 0);
                updateStatus(invocationId, "COMPLETED", output, null);
                workflowInstance.completeStep(parentInstanceId, "_async_child_complete:" + parentStepId, output);
            } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                updateStatus(invocationId, "CHILD_FAILED", null, "Child " + status);
            }
            return status;
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String persistInvocation(String parentId, String stepId, SubWorkflowConfig config, int depth) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO workflow_sub_invocations " +
                 "(parent_instance_id, parent_step_id, child_workflow_name, mode, propagate_error, depth) " +
                 "VALUES (?,?,?,?,?,?) RETURNING invocation_id"
             )) {
            ps.setString(1, parentId);
            ps.setString(2, stepId);
            ps.setString(3, config.childWorkflowName());
            ps.setString(4, config.mode().name());
            ps.setBoolean(5, config.propagateError());
            ps.setInt(6, depth);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void updateChildInstanceId(String invocationId, String childInstanceId) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE workflow_sub_invocations SET child_instance_id=?, status='RUNNING' WHERE invocation_id=?"
             )) {
            ps.setString(1, childInstanceId);
            ps.setString(2, invocationId);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    private void updateStatus(String invocationId, String status, Map<String, Object> output, String error) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE workflow_sub_invocations SET status=?, child_output=?::jsonb, child_error=?, completed_at=NOW() WHERE invocation_id=?"
             )) {
            ps.setString(1, status);
            ps.setString(2, output != null ? output.toString() : null);
            ps.setString(3, error);
            ps.setString(4, invocationId);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    private Optional<String[]> loadInvocation(String invocationId) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT child_instance_id, parent_instance_id, parent_step_id FROM workflow_sub_invocations WHERE invocation_id=?"
             )) {
            ps.setString(1, invocationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
                return Optional.empty();
            }
        } catch (Exception e) { return Optional.empty(); }
    }
}
