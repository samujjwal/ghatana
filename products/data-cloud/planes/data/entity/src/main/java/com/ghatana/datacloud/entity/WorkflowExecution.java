package com.ghatana.datacloud.entity;

import java.time.Instant;
import java.util.*;

/**
 * Represents a single execution run of a workflow definition.
 *
 * <p><b>Purpose</b><br>
 * Captures the runtime state of a workflow execution, including per-node progress,
 * overall status, input/output variables, and error information. Immutable value object.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowExecution execution = WorkflowExecution.builder()
 *     .tenantId("tenant-123")
 *     .workflowId(workflowId)
 *     .status(WorkflowExecution.Status.PENDING)
 *     .startedBy("user-456")
 *     .inputVariables(Map.of("dataset", "orders"))
 *     .build();
 * }</pre>
 *
 * <p><b>Lifecycle</b><br>
 * PENDING → RUNNING → COMPLETED | FAILED | CANCELLED
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - thread-safe.
 *
 * @see WorkflowExecutionRepository
 * @see Workflow
 * @doc.type class
 * @doc.purpose Workflow execution runtime state domain model
 * @doc.layer domain
 * @doc.pattern Value Object (Domain Layer)
 */
public class WorkflowExecution {

    /**
     * Execution lifecycle statuses.
     */
    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final UUID id;
    private final String tenantId;
    private final UUID workflowId;
    private final Status status;
    private final String startedBy;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Map<String, Object> inputVariables;
    private final Map<String, Object> outputVariables;
    private final List<NodeExecution> nodeExecutions;
    private final String errorMessage;

    /**
     * Creates a new workflow execution.
     *
     * @param id               unique execution ID
     * @param tenantId         owning tenant
     * @param workflowId       workflow definition ID
     * @param status           current execution status
     * @param startedBy        user who triggered this execution
     * @param startedAt        when execution started
     * @param completedAt      when execution completed (null if still running)
     * @param inputVariables   input variables passed to the workflow
     * @param outputVariables  output variables produced by the workflow
     * @param nodeExecutions   per-node execution records
     * @param errorMessage     error description if FAILED, null otherwise
     */
    public WorkflowExecution(
            UUID id,
            String tenantId,
            UUID workflowId,
            Status status,
            String startedBy,
            Instant startedAt,
            Instant completedAt,
            Map<String, Object> inputVariables,
            Map<String, Object> outputVariables,
            List<NodeExecution> nodeExecutions,
            String errorMessage) {
        this.id = Objects.requireNonNull(id, "ID must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        this.workflowId = Objects.requireNonNull(workflowId, "Workflow ID must not be null");
        this.status = Objects.requireNonNull(status, "Status must not be null");
        this.startedBy = Objects.requireNonNull(startedBy, "Started-by must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "Started-at must not be null");
        this.completedAt = completedAt;
        this.inputVariables = inputVariables != null ? Collections.unmodifiableMap(new HashMap<>(inputVariables)) : Map.of();
        this.outputVariables = outputVariables != null ? Collections.unmodifiableMap(new HashMap<>(outputVariables)) : Map.of();
        this.nodeExecutions = nodeExecutions != null ? Collections.unmodifiableList(new ArrayList<>(nodeExecutions)) : List.of();
        this.errorMessage = errorMessage;
    }

    // Getters
    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getWorkflowId() { return workflowId; }
    public Status getStatus() { return status; }
    public String getStartedBy() { return startedBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Map<String, Object> getInputVariables() { return inputVariables; }
    public Map<String, Object> getOutputVariables() { return outputVariables; }
    public List<NodeExecution> getNodeExecutions() { return nodeExecutions; }
    public String getErrorMessage() { return errorMessage; }

    /**
     * Returns a new builder for WorkflowExecution.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder pre-populated with this execution's values.
     *
     * @return a builder with this execution's values
     */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .tenantId(tenantId)
                .workflowId(workflowId)
                .status(status)
                .startedBy(startedBy)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .inputVariables(new HashMap<>(inputVariables))
                .outputVariables(new HashMap<>(outputVariables))
                .nodeExecutions(new ArrayList<>(nodeExecutions))
                .errorMessage(errorMessage);
    }

    /**
     * Builder for WorkflowExecution.
     */
    public static class Builder {
        private UUID id;
        private String tenantId;
        private UUID workflowId;
        private Status status = Status.PENDING;
        private String startedBy;
        private Instant startedAt;
        private Instant completedAt;
        private Map<String, Object> inputVariables = new HashMap<>();
        private Map<String, Object> outputVariables = new HashMap<>();
        private List<NodeExecution> nodeExecutions = new ArrayList<>();
        private String errorMessage;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder workflowId(UUID workflowId) { this.workflowId = workflowId; return this; }
        public Builder status(Status status) { this.status = status; return this; }
        public Builder startedBy(String startedBy) { this.startedBy = startedBy; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder inputVariables(Map<String, Object> inputVariables) { this.inputVariables = inputVariables; return this; }
        public Builder outputVariables(Map<String, Object> outputVariables) { this.outputVariables = outputVariables; return this; }
        public Builder nodeExecutions(List<NodeExecution> nodeExecutions) { this.nodeExecutions = nodeExecutions; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        /**
         * Builds the WorkflowExecution.
         *
         * @return the workflow execution
         */
        public WorkflowExecution build() {
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (startedAt == null) {
                startedAt = Instant.now();
            }
            return new WorkflowExecution(
                    id, tenantId, workflowId, status, startedBy,
                    startedAt, completedAt, inputVariables, outputVariables,
                    nodeExecutions, errorMessage);
        }
    }

    /**
     * Per-node execution record within a workflow execution.
     */
    public static class NodeExecution {
        private final String nodeId;
        private final String nodeName;
        private final Status status;
        private final Instant startedAt;
        private final Instant completedAt;
        private final Map<String, Object> output;
        private final String errorMessage;

        public NodeExecution(
                String nodeId,
                String nodeName,
                Status status,
                Instant startedAt,
                Instant completedAt,
                Map<String, Object> output,
                String errorMessage) {
            this.nodeId = Objects.requireNonNull(nodeId, "Node ID must not be null");
            this.nodeName = nodeName;
            this.status = Objects.requireNonNull(status, "Status must not be null");
            this.startedAt = startedAt;
            this.completedAt = completedAt;
            this.output = output != null ? Collections.unmodifiableMap(new HashMap<>(output)) : Map.of();
            this.errorMessage = errorMessage;
        }

        public String getNodeId() { return nodeId; }
        public String getNodeName() { return nodeName; }
        public Status getStatus() { return status; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getCompletedAt() { return completedAt; }
        public Map<String, Object> getOutput() { return output; }
        public String getErrorMessage() { return errorMessage; }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowExecution that = (WorkflowExecution) o;
        return Objects.equals(id, that.id) && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId);
    }

    @Override
    public String toString() {
        return "WorkflowExecution{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", workflowId=" + workflowId +
                ", status=" + status +
                ", startedBy='" + startedBy + '\'' +
                ", startedAt=" + startedAt +
                '}';
    }
}
