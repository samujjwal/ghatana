package com.ghatana.kernel.workflow;

import java.util.Map;
import java.util.Objects;

/**
 * Context for workflow execution.
 *
 * @doc.type class
 * @doc.purpose Workflow execution context (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class WorkflowContext {

    private final String workflowId;
    private final String tenantId;
    private final String userId;
    private final Map<String, Object> input;
    private final Map<String, Object> variables;

    private WorkflowContext(Builder builder) {
        this.workflowId = Objects.requireNonNull(builder.workflowId, "workflowId must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.userId = builder.userId;
        this.input = Map.copyOf(builder.input);
        this.variables = Map.copyOf(builder.variables);
    }

    public String getWorkflowId() { return workflowId; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public Map<String, Object> getInput() { return input; }
    public Map<String, Object> getVariables() { return variables; }

    public Builder toBuilder() {
        return new Builder()
            .workflowId(workflowId)
            .tenantId(tenantId)
            .userId(userId)
            .input(input)
            .variables(variables);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String workflowId;
        private String tenantId;
        private String userId;
        private Map<String, Object> input = Map.of();
        private Map<String, Object> variables = Map.of();

        public Builder workflowId(String workflowId) { this.workflowId = workflowId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder input(Map<String, Object> input) { this.input = input; return this; }
        public Builder variables(Map<String, Object> variables) { this.variables = variables; return this; }

        public WorkflowContext build() {
            if (workflowId == null || workflowId.isBlank()) throw new IllegalArgumentException("workflowId must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            return new WorkflowContext(this);
        }
    }
}
