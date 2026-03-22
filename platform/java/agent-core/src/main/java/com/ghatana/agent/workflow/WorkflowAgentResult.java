package com.ghatana.agent.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Result of a workflow agent execution.
 *
 * <p>Captures the output, status, and metrics from an agent task execution.
 * This record is immutable and thread-safe.
 *
 * <p><b>TypeScript Alignment:</b> This record corresponds to
 * {@code AgentExecutionResult} from {@code @yappc/types/devsecops/workflow-automation}.
 *
 * @param id Unique identifier for this result
 * @param requestId The request ID this result corresponds to
 * @param agentId The agent that produced this result
 * @param status Execution status (success, failed, partial)
 * @param output The result output data
 * @param confidence Confidence score (0.0 to 1.0)
 * @param error Error message if failed
 * @param metrics Execution metrics
 * @param startedAt When execution started
 * @param completedAt When execution completed
 *
 * @doc.type record
 * @doc.purpose Agent execution result with metrics
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record WorkflowAgentResult(
        @NotNull String id,
        @NotNull String requestId,
        @NotNull String agentId,
        @NotNull ExecutionStatus status,
        @Nullable Map<String, Object> output,
        double confidence,
        @Nullable String error,
        @NotNull ExecutionMetrics metrics,
        @NotNull Instant startedAt,
        @NotNull Instant completedAt
) {

    /**
     * Execution status enumeration.
     */
    public enum ExecutionStatus {
        SUCCESS,
        FAILED,
        PARTIAL,
        CANCELLED
    }

    /**
     * Execution metrics.
     *
     * @param durationMs Execution duration in milliseconds
     * @param tokensUsed Total tokens consumed
     * @param cost Estimated cost in USD
     */
    public record ExecutionMetrics(
            long durationMs,
            int tokensUsed,
            double cost
    ) {
        /**
         * Creates empty metrics for zero-cost operations.
         */
        public static ExecutionMetrics empty() {
            return new ExecutionMetrics(0, 0, 0.0);
        }
    }

    /**
     * Creates a successful result.
     *
     * @param id Result ID
     * @param requestId Request ID
     * @param agentId Agent ID
     * @param output Output data
     * @param confidence Confidence score
     * @param metrics Execution metrics
     * @param startedAt Start time
     * @return A success result
     */
    public static WorkflowAgentResult success(
            String id,
            String requestId,
            String agentId,
            Map<String, Object> output,
            double confidence,
            ExecutionMetrics metrics,
            Instant startedAt
    ) {
        return new WorkflowAgentResult(
                id, requestId, agentId,
                ExecutionStatus.SUCCESS,
                output, confidence, null,
                metrics, startedAt, Instant.now()
        );
    }

    /**
     * Creates a failed result.
     *
     * @param id Result ID
     * @param requestId Request ID
     * @param agentId Agent ID
     * @param error Error message
     * @param metrics Execution metrics
     * @param startedAt Start time
     * @return A failure result
     */
    public static WorkflowAgentResult failure(
            String id,
            String requestId,
            String agentId,
            String error,
            ExecutionMetrics metrics,
            Instant startedAt
    ) {
        return new WorkflowAgentResult(
                id, requestId, agentId,
                ExecutionStatus.FAILED,
                null, 0.0, error,
                metrics, startedAt, Instant.now()
        );
    }
}
