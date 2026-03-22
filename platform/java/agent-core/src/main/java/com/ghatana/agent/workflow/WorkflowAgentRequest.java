package com.ghatana.agent.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Request to execute a workflow agent.
 *
 * <p>Encapsulates all parameters needed to invoke a workflow agent,
 * including the target item, input context, and execution priority.
 *
 * <p><b>TypeScript Alignment:</b> This record corresponds to
 * {@code AgentExecutionRequest} from {@code @yappc/types/devsecops/workflow-automation}.
 *
 * @param id Unique identifier for this request
 * @param agentId The agent to execute
 * @param role The workflow role of the agent
 * @param itemId Optional item/task ID this execution relates to
 * @param input Input parameters for the agent
 * @param context Execution context (user, tenant, etc.)
 * @param priority Execution priority
 * @param requestedAt When the request was made
 *
 * @doc.type record
 * @doc.purpose Agent execution request
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record WorkflowAgentRequest(
        @NotNull String id,
        @NotNull String agentId,
        @NotNull WorkflowAgentRole role,
        @Nullable String itemId,
        @NotNull Map<String, Object> input,
        @NotNull ExecutionContext context,
        @NotNull Priority priority,
        @NotNull Instant requestedAt
) {

    /**
     * Execution priority levels.
     */
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Execution context containing tenant and user information.
     *
     * @param tenantId The tenant ID
     * @param userId The user ID who initiated the request
     * @param correlationId Trace correlation ID
     */
    public record ExecutionContext(
            @NotNull String tenantId,
            @NotNull String userId,
            @Nullable String correlationId
    ) {
        /**
         * Creates a system context for automated executions.
         *
         * @param tenantId The tenant ID
         * @return A system execution context
         */
        public static ExecutionContext system(String tenantId) {
            return new ExecutionContext(tenantId, "system", null);
        }
    }

    /**
     * Creates a new request builder.
     *
     * @param agentId The agent ID
     * @param role The agent role
     * @return A new builder
     */
    public static Builder builder(String agentId, WorkflowAgentRole role) {
        return new Builder(agentId, role);
    }

    /**
     * Builder for WorkflowAgentRequest.
     */
    public static class Builder {
        private final String agentId;
        private final WorkflowAgentRole role;
        private String itemId;
        private Map<String, Object> input = Map.of();
        private ExecutionContext context;
        private Priority priority = Priority.MEDIUM;

        private Builder(String agentId, WorkflowAgentRole role) {
            this.agentId = agentId;
            this.role = role;
        }

        public Builder itemId(String itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder input(Map<String, Object> input) {
            this.input = input;
            return this;
        }

        public Builder context(ExecutionContext context) {
            this.context = context;
            return this;
        }

        public Builder priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        public WorkflowAgentRequest build() {
            if (context == null) {
                throw new IllegalStateException("ExecutionContext is required");
            }
            return new WorkflowAgentRequest(
                    "req-" + System.currentTimeMillis(),
                    agentId,
                    role,
                    itemId,
                    input,
                    context,
                    priority,
                    Instant.now()
            );
        }
    }
}
