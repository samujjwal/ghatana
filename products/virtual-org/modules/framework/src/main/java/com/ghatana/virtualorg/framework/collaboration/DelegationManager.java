package com.ghatana.virtualorg.framework.collaboration;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Interface for delegating tasks between agents.
 *
 * <p>
 * <b>Purpose</b><br>
 * Manages task delegation including: - Capability-based agent selection - Task
 * assignment and tracking - Workload balancing - Escalation handling
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * DelegationManager delegation = new DefaultDelegationManager(conversationManager, registry);
 *
 * // Delegate a task
 * DelegationResult result = delegation.delegate(DelegationRequest.builder()
 *     .fromAgent("manager-agent")
 *     .taskType("code-review")
 *     .taskDescription("Review PR #123")
 *     .priority(Priority.HIGH)
 *     .build()).getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Delegate tasks between agents
 * @doc.layer product
 * @doc.pattern Mediator
 */
public interface DelegationManager {

    /**
     * Delegates a task to an appropriate agent.
     *
     * @param request Delegation request
     * @return Promise of delegation result
     */
    Promise<DelegationResult> delegate(DelegationRequest request);

    /**
     * Delegates to a specific agent.
     *
     * @param request Delegation request
     * @param targetAgentId Target agent ID
     * @return Promise of delegation result
     */
    Promise<DelegationResult> delegateTo(DelegationRequest request, String targetAgentId);

    /**
     * Finds agents capable of handling a task type.
     *
     * @param taskType Task type
     * @return Promise of capable agent IDs
     */
    Promise<List<String>> findCapableAgents(String taskType);

    /**
     * Gets the current workload for an agent.
     *
     * @param agentId Agent ID
     * @return Promise of workload info
     */
    Promise<AgentWorkload> getWorkload(String agentId);

    /**
     * Cancels a delegated task.
     *
     * @param delegationId Delegation ID
     * @param reason Cancellation reason
     * @return Promise indicating success
     */
    Promise<Boolean> cancel(String delegationId, String reason);

    /**
     * Escalates a task to a higher-level agent.
     *
     * @param delegationId Original delegation ID
     * @param reason Escalation reason
     * @return Promise of new delegation result
     */
    Promise<DelegationResult> escalate(String delegationId, String reason);

    /**
     * Reports progress on a delegated task.
     *
     * @param delegationId Delegation ID
     * @param progress Progress report
     * @return Promise indicating success
     */
    Promise<Boolean> reportProgress(String delegationId, TaskProgress progress);

    /**
     * Completes a delegated task.
     *
     * @param delegationId Delegation ID
     * @param result Task result
     * @return Promise indicating success
     */
    Promise<Boolean> complete(String delegationId, TaskResult result);

    /**
     * Gets the status of a delegation.
     *
     * @param delegationId Delegation ID
     * @return Promise of delegation status
     */
    Promise<DelegationStatus> getStatus(String delegationId);

    // ========== Value Objects ==========
    /**
     * Request to delegate a task.
     */
    record DelegationRequest(
            String fromAgentId,
            String taskType,
            String taskDescription,
            Map<String, Object> taskData,
            AgentMessage.Priority priority,
            long timeoutMs,
            List<String> requiredCapabilities,
            boolean allowEscalation
    ) {
        

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String fromAgentId;
        private String taskType;
        private String taskDescription;
        private Map<String, Object> taskData;
        private AgentMessage.Priority priority = AgentMessage.Priority.NORMAL;
        private long timeoutMs = 300_000; // 5 minutes default
        private List<String> requiredCapabilities;
        private boolean allowEscalation = true;

        public Builder fromAgent(String agentId) {
            this.fromAgentId = agentId;
            return this;
        }

        public Builder taskType(String taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder taskDescription(String description) {
            this.taskDescription = description;
            return this;
        }

        public Builder taskData(Map<String, Object> data) {
            this.taskData = data;
            return this;
        }

        public Builder priority(AgentMessage.Priority priority) {
            this.priority = priority;
            return this;
        }

        public Builder timeout(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder requiredCapabilities(List<String> capabilities) {
            this.requiredCapabilities = capabilities;
            return this;
        }

        public Builder allowEscalation(boolean allow) {
            this.allowEscalation = allow;
            return this;
        }

        public DelegationRequest build() {
            return new DelegationRequest(
                    fromAgentId,
                    taskType,
                    taskDescription,
                    taskData != null ? Map.copyOf(taskData) : Map.of(),
                    priority,
                    timeoutMs,
                    requiredCapabilities != null ? List.copyOf(requiredCapabilities) : List.of(),
                    allowEscalation
            );
        }
    }
}

/**
 * Result of a delegation attempt.
 */
record DelegationResult(
        String delegationId,
        String assignedAgentId,
        boolean success,
        String failureReason,
        long estimatedCompletionMs
        ) {

    public static DelegationResult success(String delegationId, String agentId, long estimatedMs) {
        return new DelegationResult(delegationId, agentId, true, null, estimatedMs);
    }

    public static DelegationResult failure(String reason) {
        return new DelegationResult(null, null, false, reason, 0);
    }
}

/**
 * Workload information for an agent.
 */
record AgentWorkload(
        String agentId,
        int activeTasks,
        int queuedTasks,
        double utilizationPercent,
        boolean available
        ) {

}

/**
 * Progress report for a task.
 */
record TaskProgress(
        int percentComplete,
        String statusMessage,
        Map<String, Object> data
        ) {

}

/**
 * Result of a completed task.
 */
record TaskResult(
        boolean success,
        Map<String, Object> output,
        String errorMessage,
        long durationMs
        ) {

    public static TaskResult success(Map<String, Object> output, long durationMs) {
        return new TaskResult(true, output, null, durationMs);
    }

    public static TaskResult failure(String error, long durationMs) {
        return new TaskResult(false, Map.of(), error, durationMs);
    }
}

/**
 * Status of a delegation.
 */
enum DelegationStatus {
    PENDING,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED,
    ESCALATED,
    TIMED_OUT
}
}
