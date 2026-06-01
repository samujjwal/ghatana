/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.action;

import com.ghatana.aep.policy.PolicyDecisionContract;
import com.ghatana.datacloud.entity.agent.AgentRun;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * WS2: Canonical Action Run lifecycle capability with idempotency, replay, policy, approval, and trace support.
 *
 * <p>This capability defines the contract for executing Action Runs with full governance:
 * <ul>
 *   <li>Idempotency: Exactly-once semantics with idempotency keys</li>
 *   <li>Replay: Safe replay with dry-run and compensation support</li>
 *   <li>Policy: Policy decision evaluation before execution</li>
 *   <li>Approval: Human-in-the-loop approval gates</li>
 *   <li>Trace: Full observability with trace/span/event models</li>
 * </ul>
 *
 * <p>This replaces the generic WorkflowExecutionCapability with the canonical Action Run lifecycle,
 * aligning with the AgentRun entity for persistence and governance tracking.
 *
 * @doc.type interface
 * @doc.purpose Canonical Action Run lifecycle with idempotency, replay, policy, approval, and trace
 * @doc.layer product
 * @doc.pattern Capability
 */
public interface ActionRunCapability {

    /**
     * Execute an Action Run with full governance controls.
     *
     * <p>WS2: Executes an Action Run with:
     * <ul>
     *   <li>Idempotency key for exactly-once semantics</li>
     *   <li>Policy evaluation before execution</li>
     *   <li>Approval gates if required</li>
     *   <li>Full trace/span/event observability</li>
     * </ul>
     *
     * @param request execution request with all governance parameters
     * @return execution result with trace information
     */
    Promise<ActionRunResult> execute(ActionRunRequest request);

    /**
     * Replay an Action Run with replay-safe controls.
     *
     * <p>WS2: Replays a previous Action Run with:
     * <ul>
     *   <li>Replay mode (dry-run, replay-with-side-effects, recorded-output)</li>
     *   <li>Compensation strategy for rollback</li>
     *   <li>Policy evaluation for replay operations</li>
     *   <li>Trace linkage to original execution</li>
     * </ul>
     *
     * @param request replay request with replay mode and original execution ID
     * @return replay result with compensation information
     */
    Promise<ActionRunReplayResult> replay(ActionRunReplayRequest request);

    /**
     * Get Action Run status and trace information.
     *
     * @param runId Action Run ID
     * @return execution status with trace information
     */
    Promise<ActionRunStatus> getStatus(String runId);

    /**
     * Cancel an in-progress Action Run.
     *
     * @param runId Action Run ID
     * @param reason cancellation reason
     * @return cancellation result
     */
    Promise<ActionRunCancellationResult> cancel(String runId, String reason);

    /**
     * Rollback an Action Run using compensation strategy.
     *
     * <p>WS2: Rolls back an Action Run with:
     * <ul>
     *   <li>Compensation strategy from original execution</li>
     *   <li>Policy evaluation for rollback operations</li>
     *   <li>Compliance evidence persistence</li>
     * </ul>
     *
     * @param runId Action Run ID
     * @param compensationStrategy compensation strategy to use
     * @return rollback result
     */
    Promise<ActionRunRollbackResult> rollback(String runId, String compensationStrategy);

    /**
     * Get the persisted AgentRun entity for a given run ID.
     *
     * @param runId Action Run ID
     * @return AgentRun entity
     */
    Promise<Optional<AgentRun>> getAgentRun(String runId);

    // ==================== Supporting Types ====================

    /**
     * Action Run execution request.
     */
    record ActionRunRequest(
            String actionId,
            String tenantId,
            String userId,
            String idempotencyKey,
            Map<String, Object> input,
            ExecutionMode mode,
            PolicyDecisionContract.EvaluationContext policyContext,
            Set<String> requiredApprovals,
            Optional<String> parentTraceId,
            Map<String, Object> metadata
    ) {
        public ActionRunRequest {
            input = Map.copyOf(input != null ? input : Map.of());
            policyContext = policyContext != null ? policyContext : PolicyDecisionContract.EvaluationContext.of(userId, "production");
            requiredApprovals = Set.copyOf(requiredApprovals != null ? requiredApprovals : Set.of());
            parentTraceId = parentTraceId != null ? parentTraceId : Optional.empty();
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Action Run execution result.
     */
    record ActionRunResult(
            String runId,
            String actionId,
            String tenantId,
            RunStatus status,
            Map<String, Object> output,
            String traceId,
            PolicyDecisionContract.PolicyDecision policyDecision,
            Set<String> grantedApprovals,
            Instant startTime,
            Instant endTime,
            Map<String, Object> metadata
    ) {
        public ActionRunResult {
            output = Map.copyOf(output != null ? output : Map.of());
            policyDecision = policyDecision;
            grantedApprovals = Set.copyOf(grantedApprovals != null ? grantedApprovals : Set.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }

        public boolean isSuccessful() {
            return status == RunStatus.COMPLETED;
        }
    }

    /**
     * Action Run replay request.
     */
    record ActionRunReplayRequest(
            String originalRunId,
            String replayRunId,
            String tenantId,
            String userId,
            ReplayMode replayMode,
            String compensationStrategy,
            PolicyDecisionContract.EvaluationContext policyContext,
            Map<String, Object> metadata
    ) {
        public ActionRunReplayRequest {
            policyContext = policyContext != null ? policyContext : PolicyDecisionContract.EvaluationContext.of(userId, "production");
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Action Run replay result.
     */
    record ActionRunReplayResult(
            String replayRunId,
            String originalRunId,
            ReplayStatus status,
            Map<String, Object> output,
            String traceId,
            PolicyDecisionContract.ReplayPolicyDecision policyDecision,
            boolean compensationExecuted,
            Instant startTime,
            Instant endTime,
            Map<String, Object> metadata
    ) {
        public ActionRunReplayResult {
            output = Map.copyOf(output != null ? output : Map.of());
            policyDecision = policyDecision;
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    /**
     * Action Run execution status.
     */
    record ActionRunStatus(
            String runId,
            String actionId,
            String tenantId,
            RunStatus status,
            ExecutionPhase phase,
            String traceId,
            Instant startTime,
            Optional<Instant> endTime,
            Map<String, Object> metadata
    ) {
        public ActionRunStatus {
            endTime = endTime != null ? endTime : Optional.empty();
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }

        public boolean isCompleted() {
            return status == RunStatus.COMPLETED || status == RunStatus.FAILED || status == RunStatus.CANCELLED;
        }
    }

    /**
     * Action Run cancellation result.
     */
    record ActionRunCancellationResult(
            String runId,
            boolean cancelled,
            String reason,
            Instant cancelledAt,
            Map<String, Object> metadata
    ) {
        public ActionRunCancellationResult {
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Action Run rollback result.
     */
    record ActionRunRollbackResult(
            String runId,
            boolean rolledBack,
            String compensationStrategy,
            Set<String> compensatedResources,
            String rollbackTraceId,
            Instant rolledBackAt,
            Map<String, Object> metadata
    ) {
        public ActionRunRollbackResult {
            compensatedResources = Set.copyOf(compensatedResources != null ? compensatedResources : Set.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Execution mode.
     */
    enum ExecutionMode {
        NORMAL,           // Normal execution with all side effects
        DRY_RUN,          // Dry-run without side effects
        VALIDATION_ONLY   // Validation only, no execution
    }

    /**
     * Replay mode.
     */
    enum ReplayMode {
        DRY_RUN,                  // Evaluate without side effects
        REPLAY_WITH_SIDE_EFFECTS, // Full replay with side effects
        RECORDED_OUTPUT           // Use recorded agent outputs
    }

    /**
     * Run status - aligns with AgentRun.RunStatus.
     */
    enum RunStatus {
        INITIALIZED,      // Run has been created but not started
        RUNNING,         // Run is currently executing
        WAITING_APPROVAL, // Run is waiting for human approval
        COMPLETED,       // Run completed successfully
        FAILED,          // Run failed
        CANCELLED,       // Run was cancelled
        TIMEOUT,         // Run timed out
        SUSPENDED        // Run was suspended
    }

    /**
     * Execution phase.
     */
    enum ExecutionPhase {
        POLICY_CHECK,
        APPROVAL,
        EXECUTION,
        COMPENSATION,
        COMPLETED
    }

    /**
     * Replay status.
     */
    enum ReplayStatus {
        SUCCESS,
        FAILED,
        SKIPPED,
        COMPENSATION_FAILED
    }
}
