package com.ghatana.digitalmarketing.domain.workflow;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record for a single step in a durable workflow execution.
 *
 * @doc.type class
 * @doc.purpose Represents a workflow step with name, type, status and timing (DMOS-F2-004)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class DmWorkflowStep {

    private final String name;
    private final String stepType;
    private final DmWorkflowStepStatus status;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String failureReason;

    private DmWorkflowStep(Builder builder) {
        this.name          = requireNonBlank(builder.name, "name");
        this.stepType      = requireNonBlank(builder.stepType, "stepType");
        this.status        = Objects.requireNonNull(builder.status, "status must not be null");
        this.startedAt     = builder.startedAt;
        this.completedAt   = builder.completedAt;
        this.failureReason = builder.failureReason;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be null or blank");
        return value;
    }

    // ── Lifecycle transitions ─────────────────────────────────────────────────

    /** Returns a copy of this step marked as EXECUTING with startedAt = now. */
    public DmWorkflowStep markExecuting() {
        return toBuilder().status(DmWorkflowStepStatus.EXECUTING).startedAt(Instant.now()).build();
    }

    /** Returns a copy of this step marked as COMPLETED with completedAt = now. */
    public DmWorkflowStep markCompleted() {
        return toBuilder().status(DmWorkflowStepStatus.COMPLETED).completedAt(Instant.now()).build();
    }

    /** Returns a copy of this step marked as FAILED with a failure reason and completedAt = now. */
    public DmWorkflowStep markFailed(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder()
            .status(DmWorkflowStepStatus.FAILED)
            .failureReason(reason)
            .completedAt(Instant.now())
            .build();
    }

    /** Returns a copy of this step marked as SKIPPED. */
    public DmWorkflowStep markSkipped() {
        return toBuilder().status(DmWorkflowStepStatus.SKIPPED).build();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getName()                   { return name; }
    public String getStepType()               { return stepType; }
    public DmWorkflowStepStatus getStatus()   { return status; }
    public Instant getStartedAt()             { return startedAt; }
    public Instant getCompletedAt()           { return completedAt; }
    public String getFailureReason()          { return failureReason; }

    public boolean isTerminal() {
        return status == DmWorkflowStepStatus.COMPLETED
            || status == DmWorkflowStepStatus.FAILED
            || status == DmWorkflowStepStatus.SKIPPED;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public Builder toBuilder() {
        return new Builder()
            .name(this.name)
            .stepType(this.stepType)
            .status(this.status)
            .startedAt(this.startedAt)
            .completedAt(this.completedAt)
            .failureReason(this.failureReason);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private String stepType;
        private DmWorkflowStepStatus status;
        private Instant startedAt;
        private Instant completedAt;
        private String failureReason;

        private Builder() {}

        public Builder name(String v)               { this.name = v; return this; }
        public Builder stepType(String v)           { this.stepType = v; return this; }
        public Builder status(DmWorkflowStepStatus v) { this.status = v; return this; }
        public Builder startedAt(Instant v)         { this.startedAt = v; return this; }
        public Builder completedAt(Instant v)       { this.completedAt = v; return this; }
        public Builder failureReason(String v)      { this.failureReason = v; return this; }

        public DmWorkflowStep build() { return new DmWorkflowStep(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmWorkflowStep other)) return false;
        return Objects.equals(name, other.name) && Objects.equals(stepType, other.stepType);
    }

    @Override
    public int hashCode() { return Objects.hash(name, stepType); }

    @Override
    public String toString() {
        return "DmWorkflowStep{name='" + name + "', stepType='" + stepType + "', status=" + status + "}";
    }
}
