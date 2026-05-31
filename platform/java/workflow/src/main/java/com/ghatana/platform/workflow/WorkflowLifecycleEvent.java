/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable lifecycle event emitted at every significant phase of workflow execution.
 *
 * <p>Consumed by {@link WorkflowLifecycleListener} implementations for observability,
 * audit logging, and event publishing.
 *
 * @doc.type record
 * @doc.purpose Workflow lifecycle event (emitted at every state change)
 * @doc.layer core
 * @doc.pattern Event
 */
public record WorkflowLifecycleEvent(
        @NotNull String runId,
        @NotNull String workflowId,
        @NotNull Phase phase,
        @Nullable String stepId,
        @NotNull Instant timestamp,
        @Nullable String tenantId,
        @Nullable String correlationId,
        @Nullable String errorMessage,
        @Nullable String actorId,
        @NotNull Map<String, Object> attributes
) {

    /**
     * Canonical constructor with defensive copy of attributes.
     */
    public WorkflowLifecycleEvent {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(timestamp, "timestamp");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * Classifies the lifecycle phase this event represents.
     */
    public enum Phase {
        WORKFLOW_CREATED,
        WORKFLOW_STARTED,
        STEP_STARTED,
        STEP_COMPLETED,
        STEP_FAILED,
        STEP_RETRYING,
        WORKFLOW_WAITING,
        WORKFLOW_RESUMED,
        WORKFLOW_COMPLETED,
        WORKFLOW_FAILED,
        WORKFLOW_CANCELLED,
        WORKFLOW_SUSPENDED,
        WORKFLOW_COMPENSATING,
        WORKFLOW_COMPENSATED
    }

    /**
     * Convenience factory for creating events with minimal fields.
     */
    public static WorkflowLifecycleEvent of(
            @NotNull String runId,
            @NotNull String workflowId,
            @NotNull Phase phase) {
        return new WorkflowLifecycleEvent(
                runId, workflowId, phase, null, Instant.now(),
                null, null, null, null, Map.of());
    }

    /**
     * Convenience factory for step-level events.
     */
    public static WorkflowLifecycleEvent forStep(
            @NotNull String runId,
            @NotNull String workflowId,
            @NotNull Phase phase,
            @NotNull String stepId) {
        return new WorkflowLifecycleEvent(
                runId, workflowId, phase, stepId, Instant.now(),
                null, null, null, null, Map.of());
    }
}
