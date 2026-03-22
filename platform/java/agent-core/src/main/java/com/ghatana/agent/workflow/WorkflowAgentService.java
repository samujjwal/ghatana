package com.ghatana.agent.workflow;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Service for executing workflow agents.
 *
 * <p>Provides the core interface for agent execution in the workflow automation
 * system. Implementations should use ActiveJ Promise for all async operations
 * and integrate with LLMGateway for AI-powered agents.
 *
 * <p><b>Architecture:</b> Per copilot-instructions.md Hybrid Backend Strategy,
 * this Java service handles the core domain logic for agent execution. The Node.js
 * frontend calls this via REST API endpoints.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * WorkflowAgentRequest request = WorkflowAgentRequest.builder(agentId, WorkflowAgentRole.CODE_REVIEWER)
 *     .itemId("item-123")
 *     .input(Map.of("code", sourceCode, "language", "java"))
 *     .context(ExecutionContext.system("tenant-1"))
 *     .priority(Priority.HIGH)
 *     .build();
 *
 * WorkflowAgentResult result = workflowAgentService.execute(request).getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Workflow agent execution service
 * @doc.layer core
 * @doc.pattern Service
 * @see com.ghatana.ai.llm.LLMGateway
 */
public interface WorkflowAgentService {

    /**
     * Executes a workflow agent with the given request.
     *
     * @param request The execution request
     * @return A Promise resolving to the execution result
     */
    @NotNull
    Promise<WorkflowAgentResult> execute(@NotNull WorkflowAgentRequest request);

    /**
     * Executes multiple requests in batch.
     *
     * <p>Requests are executed concurrently where possible, respecting
     * rate limits and resource constraints.
     *
     * @param requests The execution requests
     * @return A Promise resolving to the list of results
     */
    @NotNull
    Promise<List<WorkflowAgentResult>> executeBatch(@NotNull List<WorkflowAgentRequest> requests);

    /**
     * Cancels a pending or running execution.
     *
     * @param requestId The request ID to cancel
     * @return A Promise resolving to true if cancellation succeeded
     */
    @NotNull
    Promise<Boolean> cancel(@NotNull String requestId);

    /**
     * Gets the current status of an execution.
     *
     * @param requestId The request ID
     * @return A Promise resolving to the execution status
     */
    @NotNull
    Promise<ExecutionStatus> getStatus(@NotNull String requestId);

    /**
     * Lists available agents for a specific role.
     *
     * @param role The workflow role
     * @return A Promise resolving to the list of agent IDs
     */
    @NotNull
    Promise<List<String>> getAgentsForRole(@NotNull WorkflowAgentRole role);

    /**
     * Gets health information for an agent.
     *
     * @param agentId The agent ID
     * @return A Promise resolving to the health info
     */
    @NotNull
    Promise<AgentHealthInfo> getAgentHealth(@NotNull String agentId);

    /**
     * Execution status for tracking.
     */
    enum ExecutionStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        NOT_FOUND
    }

    /**
     * Agent health information.
     *
     * @param agentId The agent ID
     * @param role The agent role
     * @param healthy Whether the agent is healthy
     * @param lastExecutionTime Last execution timestamp (epoch millis)
     * @param successRate Success rate (0.0 to 1.0)
     * @param avgResponseTimeMs Average response time in milliseconds
     */
    record AgentHealthInfo(
            String agentId,
            WorkflowAgentRole role,
            boolean healthy,
            long lastExecutionTime,
            double successRate,
            long avgResponseTimeMs
    ) {
        /**
         * Creates a healthy status.
         */
        public static AgentHealthInfo healthy(String agentId, WorkflowAgentRole role) {
            return new AgentHealthInfo(agentId, role, true, System.currentTimeMillis(), 1.0, 0);
        }

        /**
         * Creates an unhealthy status.
         */
        public static AgentHealthInfo unhealthy(String agentId, WorkflowAgentRole role) {
            return new AgentHealthInfo(agentId, role, false, 0, 0.0, 0);
        }
    }
}
