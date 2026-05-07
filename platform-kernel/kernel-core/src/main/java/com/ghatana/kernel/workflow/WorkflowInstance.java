package com.ghatana.kernel.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Instance of a workflow execution.
 *
 * @doc.type class
 * @doc.purpose Workflow execution instance (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Entity
 */
public final class WorkflowInstance {

    private final String instanceId;
    private final String workflowId;
    private final String tenantId;
    private final String userId;
    private final WorkflowStatus status;
    private final int currentStep;
    private final int totalSteps;
    private final Map<String, Object> input;
    private final Map<String, Object> output;
    private final String error;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant completedAt;

    private WorkflowInstance(Builder builder) {
        this.instanceId = Objects.requireNonNull(builder.instanceId, "instanceId must not be null");
        this.workflowId = Objects.requireNonNull(builder.workflowId, "workflowId must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.userId = builder.userId;
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.currentStep = builder.currentStep;
        this.totalSteps = builder.totalSteps;
        this.input = Map.copyOf(builder.input);
        this.output = Map.copyOf(builder.output);
        this.error = builder.error;
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
    }

    public String getInstanceId() { return instanceId; }
    public String getWorkflowId() { return workflowId; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public WorkflowStatus getStatus() { return status; }
    public int getCurrentStep() { return currentStep; }
    public int getTotalSteps() { return totalSteps; }
    public Map<String, Object> getInput() { return input; }
    public Map<String, Object> getOutput() { return output; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public boolean isRunning() {
        return status == WorkflowStatus.RUNNING;
    }

    public boolean isCompleted() {
        return status == WorkflowStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == WorkflowStatus.FAILED;
    }

    public boolean canResume() {
        return status == WorkflowStatus.PAUSED || status == WorkflowStatus.FAILED;
    }

    public Builder toBuilder() {
        return new Builder()
            .instanceId(instanceId)
            .workflowId(workflowId)
            .tenantId(tenantId)
            .userId(userId)
            .status(status)
            .currentStep(currentStep)
            .totalSteps(totalSteps)
            .input(input)
            .output(output)
            .error(error)
            .createdAt(createdAt)
            .startedAt(startedAt)
            .completedAt(completedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String instanceId;
        private String workflowId;
        private String tenantId;
        private String userId;
        private WorkflowStatus status = WorkflowStatus.PENDING;
        private int currentStep = 0;
        private int totalSteps = 0;
        private Map<String, Object> input = Map.of();
        private Map<String, Object> output = Map.of();
        private String error;
        private Instant createdAt = Instant.now();
        private Instant startedAt;
        private Instant completedAt;

        public Builder instanceId(String instanceId) { this.instanceId = instanceId; return this; }
        public Builder workflowId(String workflowId) { this.workflowId = workflowId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder status(WorkflowStatus status) { this.status = status; return this; }
        public Builder currentStep(int currentStep) { this.currentStep = currentStep; return this; }
        public Builder totalSteps(int totalSteps) { this.totalSteps = totalSteps; return this; }
        public Builder input(Map<String, Object> input) { this.input = input; return this; }
        public Builder output(Map<String, Object> output) { this.output = output; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }

        public WorkflowInstance build() {
            if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId must not be blank");
            if (workflowId == null || workflowId.isBlank()) throw new IllegalArgumentException("workflowId must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            return new WorkflowInstance(this);
        }
    }
}
