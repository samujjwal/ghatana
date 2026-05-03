package com.ghatana.digitalmarketing.domain.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing a durable workflow execution.
 * <p>
 * A workflow has an ordered list of steps. The {@code currentStepIndex} tracks
 * which step is active. Status transitions follow a strict lifecycle.
 * </p>
 *
 * @doc.type class
 * @doc.purpose Durable workflow execution entity with ordered steps and lifecycle (DMOS-F2-004)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmWorkflowExecution {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String name;
    private final String correlationId;
    private final List<DmWorkflowStep> steps;
    private final int currentStepIndex;
    private final DmWorkflowStatus status;
    private final Instant createdAt;
    private final Instant completedAt;
    private final String failureReason;

    private DmWorkflowExecution(Builder builder) {
        this.id               = requireNonBlank(builder.id, "id");
        this.tenantId         = requireNonBlank(builder.tenantId, "tenantId");
        this.workspaceId      = requireNonBlank(builder.workspaceId, "workspaceId");
        this.name             = requireNonBlank(builder.name, "name");
        this.correlationId    = requireNonBlank(builder.correlationId, "correlationId");
        this.steps            = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(builder.steps, "steps must not be null")));
        this.currentStepIndex = builder.currentStepIndex;
        this.status           = Objects.requireNonNull(builder.status, "status must not be null");
        this.createdAt        = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.completedAt      = builder.completedAt;
        this.failureReason    = builder.failureReason;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be null or blank");
        return value;
    }

    // ── Lifecycle transitions ─────────────────────────────────────────────────

    /** Start this workflow — transitions PENDING → RUNNING. */
    public DmWorkflowExecution start() {
        if (status != DmWorkflowStatus.PENDING) {
            throw new IllegalStateException("Only PENDING workflows can be started, current: " + status);
        }
        return toBuilder().status(DmWorkflowStatus.RUNNING).build();
    }

    /** Advance to the next step, updating the current step's completed state. */
    public DmWorkflowExecution advanceStep(DmWorkflowStep completedStep) {
        Objects.requireNonNull(completedStep, "completedStep must not be null");
        List<DmWorkflowStep> updated = new ArrayList<>(steps);
        updated.set(currentStepIndex, completedStep);
        int next = currentStepIndex + 1;
        return toBuilder()
            .steps(updated)
            .currentStepIndex(next)
            .build();
    }

    /** Complete this workflow — all steps finished. */
    public DmWorkflowExecution complete() {
        if (status != DmWorkflowStatus.RUNNING) {
            throw new IllegalStateException("Only RUNNING workflows can be completed, current: " + status);
        }
        return toBuilder()
            .status(DmWorkflowStatus.COMPLETED)
            .completedAt(Instant.now())
            .build();
    }

    /** Fail this workflow with a reason. */
    public DmWorkflowExecution fail(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        if (status != DmWorkflowStatus.RUNNING && status != DmWorkflowStatus.PAUSED) {
            throw new IllegalStateException("Only RUNNING or PAUSED workflows can be failed, current: " + status);
        }
        return toBuilder()
            .status(DmWorkflowStatus.FAILED)
            .failureReason(reason)
            .completedAt(Instant.now())
            .build();
    }

    /** Pause this workflow — transitions RUNNING → PAUSED. */
    public DmWorkflowExecution pause() {
        if (status != DmWorkflowStatus.RUNNING) {
            throw new IllegalStateException("Only RUNNING workflows can be paused, current: " + status);
        }
        return toBuilder().status(DmWorkflowStatus.PAUSED).build();
    }

    /** Resume this workflow — transitions PAUSED → RUNNING. */
    public DmWorkflowExecution resume() {
        if (status != DmWorkflowStatus.PAUSED) {
            throw new IllegalStateException("Only PAUSED workflows can be resumed, current: " + status);
        }
        return toBuilder().status(DmWorkflowStatus.RUNNING).build();
    }

    /** Mark as ROLLED_BACK — only valid from FAILED. */
    public DmWorkflowExecution rollback() {
        if (status != DmWorkflowStatus.FAILED) {
            throw new IllegalStateException("Only FAILED workflows can be rolled back, current: " + status);
        }
        return toBuilder().status(DmWorkflowStatus.ROLLED_BACK).build();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getId()                     { return id; }
    public String getTenantId()               { return tenantId; }
    public String getWorkspaceId()            { return workspaceId; }
    public String getName()                   { return name; }
    public String getCorrelationId()          { return correlationId; }
    public List<DmWorkflowStep> getSteps()    { return steps; }
    public int getCurrentStepIndex()          { return currentStepIndex; }
    public DmWorkflowStatus getStatus()       { return status; }
    public Instant getCreatedAt()             { return createdAt; }
    public Instant getCompletedAt()           { return completedAt; }
    public String getFailureReason()          { return failureReason; }

    public boolean isTerminal() {
        return status == DmWorkflowStatus.COMPLETED
            || status == DmWorkflowStatus.FAILED
            || status == DmWorkflowStatus.ROLLED_BACK;
    }

    public DmWorkflowStep currentStep() {
        if (steps.isEmpty() || currentStepIndex >= steps.size()) return null;
        return steps.get(currentStepIndex);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public Builder toBuilder() {
        return new Builder()
            .id(this.id)
            .tenantId(this.tenantId)
            .workspaceId(this.workspaceId)
            .name(this.name)
            .correlationId(this.correlationId)
            .steps(new ArrayList<>(this.steps))
            .currentStepIndex(this.currentStepIndex)
            .status(this.status)
            .createdAt(this.createdAt)
            .completedAt(this.completedAt)
            .failureReason(this.failureReason);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String tenantId;
        private String workspaceId;
        private String name;
        private String correlationId;
        private List<DmWorkflowStep> steps = new ArrayList<>();
        private int currentStepIndex = 0;
        private DmWorkflowStatus status;
        private Instant createdAt;
        private Instant completedAt;
        private String failureReason;

        private Builder() {}

        public Builder id(String v)                          { this.id = v; return this; }
        public Builder tenantId(String v)                    { this.tenantId = v; return this; }
        public Builder workspaceId(String v)                 { this.workspaceId = v; return this; }
        public Builder name(String v)                        { this.name = v; return this; }
        public Builder correlationId(String v)               { this.correlationId = v; return this; }
        public Builder steps(List<DmWorkflowStep> v)         { this.steps = v; return this; }
        public Builder currentStepIndex(int v)               { this.currentStepIndex = v; return this; }
        public Builder status(DmWorkflowStatus v)            { this.status = v; return this; }
        public Builder createdAt(Instant v)                  { this.createdAt = v; return this; }
        public Builder completedAt(Instant v)                { this.completedAt = v; return this; }
        public Builder failureReason(String v)               { this.failureReason = v; return this; }

        public DmWorkflowExecution build() { return new DmWorkflowExecution(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmWorkflowExecution other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "DmWorkflowExecution{id='" + id + "', name='" + name + "', status=" + status + "}";
    }
}
