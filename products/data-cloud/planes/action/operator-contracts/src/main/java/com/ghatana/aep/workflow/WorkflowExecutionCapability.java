package com.ghatana.aep.workflow;

import com.ghatana.aep.observability.trace.TraceModels;
import com.ghatana.aep.policy.PolicyDecisionContract;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * WS2: Canonical workflow execution capability with idempotency, replay, policy, approval, and trace support.
 *
 * <p>This capability defines the contract for executing workflows (Action Runs) with full governance:
 * <ul>
 *   <li>Idempotency: Exactly-once semantics with idempotency keys</li>
 *   <li>Replay: Safe replay with dry-run and compensation support</li>
 *   <li>Policy: Policy decision evaluation before execution</li>
 *   <li>Approval: Human-in-the-loop approval gates</li>
 *   <li>Trace: Full observability with trace/span/event models</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Canonical workflow execution with idempotency, replay, policy, approval, and trace
 * @doc.layer product
 * @doc.pattern Capability
 */
public interface WorkflowExecutionCapability {

    /**
     * Execute a workflow with full governance controls.
     *
     * <p>WS2: Executes a workflow with:
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
    Promise<WorkflowExecutionResult> execute(WorkflowExecutionRequest request);

    /**
     * Replay a workflow execution with replay-safe controls.
     *
     * <p>WS2: Replays a previous execution with:
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
    Promise<WorkflowReplayResult> replay(WorkflowReplayRequest request);

    /**
     * Get execution status and trace information.
     *
     * @param executionId execution ID
     * @return execution status with trace information
     */
    Promise<WorkflowExecutionStatus> getStatus(String executionId);

    /**
     * Cancel an in-progress execution.
     *
     * @param executionId execution ID
     * @param reason cancellation reason
     * @return cancellation result
     */
    Promise<WorkflowCancellationResult> cancel(String executionId, String reason);

    /**
     * Rollback an execution using compensation strategy.
     *
     * <p>WS2: Rolls back an execution with:
     * <ul>
     *   <li>Compensation strategy from original execution</li>
     *   <li>Policy evaluation for rollback operations</li>
 *   <li>Compliance evidence persistence</li>
     * </ul>
     *
     * @param executionId execution ID
     * @param compensationStrategy compensation strategy to use
     * @return rollback result
     */
    Promise<WorkflowRollbackResult> rollback(String executionId, String compensationStrategy);

    // ==================== Supporting Types ====================

    /**
     * Workflow execution request.
     */
    record WorkflowExecutionRequest(
            String workflowId,
            String tenantId,
            String userId,
            String idempotencyKey,
            Map<String, Object> input,
            WorkflowExecutionMode mode,
            PolicyDecisionContract.EvaluationContext policyContext,
            Set<String> requiredApprovals,
            Optional<String> parentTraceId,
            Map<String, Object> metadata
    ) {
        public WorkflowExecutionRequest {
            input = Map.copyOf(input != null ? input : Map.of());
            policyContext = policyContext != null ? policyContext : PolicyDecisionContract.EvaluationContext.of(userId, "production");
            requiredApprovals = Set.copyOf(requiredApprovals != null ? requiredApprovals : Set.of());
            parentTraceId = parentTraceId != null ? parentTraceId : Optional.empty();
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Workflow execution result.
     */
    record WorkflowExecutionResult(
            String executionId,
            String workflowId,
            String tenantId,
            ExecutionStatus status,
            Map<String, Object> output,
            String traceId,
            TraceModels.ExecutionTrace executionTrace,
            PolicyDecisionContract.PolicyDecision policyDecision,
            Set<String> grantedApprovals,
            Instant startTime,
            Instant endTime,
            Map<String, Object> metadata
    ) {
        public WorkflowExecutionResult {
            output = Map.copyOf(output != null ? output : Map.of());
            executionTrace = executionTrace;
            policyDecision = policyDecision;
            grantedApprovals = Set.copyOf(grantedApprovals != null ? grantedApprovals : Set.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }

        public boolean isSuccessful() {
            return status == ExecutionStatus.SUCCESS;
        }
    }

    /**
     * Workflow replay request.
     */
    record WorkflowReplayRequest(
            String originalExecutionId,
            String replayExecutionId,
            String tenantId,
            String userId,
            ReplayMode replayMode,
            String compensationStrategy,
            PolicyDecisionContract.EvaluationContext policyContext,
            Map<String, Object> metadata
    ) {
        public WorkflowReplayRequest {
            policyContext = policyContext != null ? policyContext : PolicyDecisionContract.EvaluationContext.of(userId, "production");
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Workflow replay result.
     */
    record WorkflowReplayResult(
            String replayExecutionId,
            String originalExecutionId,
            ReplayStatus status,
            Map<String, Object> output,
            String traceId,
            TraceModels.ReplaySpan replaySpan,
            PolicyDecisionContract.ReplayPolicyDecision policyDecision,
            boolean compensationExecuted,
            Instant startTime,
            Instant endTime,
            Map<String, Object> metadata
    ) {
        public WorkflowReplayResult {
            output = Map.copyOf(output != null ? output : Map.of());
            replaySpan = replaySpan;
            policyDecision = policyDecision;
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    /**
     * Workflow execution status.
     */
    record WorkflowExecutionStatus(
            String executionId,
            String workflowId,
            String tenantId,
            ExecutionStatus status,
            ExecutionPhase phase,
            String traceId,
            Instant startTime,
            Optional<Instant> endTime,
            Map<String, Object> metadata
    ) {
        public WorkflowExecutionStatus {
            endTime = endTime != null ? endTime : Optional.empty();
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }

        public boolean isCompleted() {
            return status == ExecutionStatus.SUCCESS || status == ExecutionStatus.FAILED || status == ExecutionStatus.CANCELLED;
        }
    }

    /**
     * Workflow cancellation result.
     */
    record WorkflowCancellationResult(
            String executionId,
            boolean cancelled,
            String reason,
            Instant cancelledAt,
            Map<String, Object> metadata
    ) {
        public WorkflowCancellationResult {
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Workflow rollback result.
     */
    record WorkflowRollbackResult(
            String executionId,
            boolean rolledBack,
            String compensationStrategy,
            Set<String> compensatedResources,
            String rollbackTraceId,
            Instant rolledBackAt,
            Map<String, Object> metadata
    ) {
        public WorkflowRollbackResult {
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
     * Execution status.
     */
    enum ExecutionStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED,
        TIMEOUT,
        SKIPPED
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
