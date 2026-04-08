/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable definition of a single step within a workflow.
 *
 * <p>Step definitions are part of a {@link WorkflowDefinition} and describe
 * <b>what</b> a step does (kind, configuration, transitions) without containing
 * runtime state. The runtime engine interprets these definitions to execute
 * each step appropriately.
 *
 * @doc.type record
 * @doc.purpose Immutable specification of a workflow step
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record WorkflowStepDefinition(
    @NotNull String stepId,
    @NotNull String name,
    @NotNull WorkflowStepKind kind,
    @Nullable String operatorId,
    @Nullable String celCondition,
    @Nullable String nextStepOnTrue,
    @Nullable String nextStepOnFalse,
    @Nullable String nextStep,
    @Nullable String subWorkflowId,
    int maxRetries,
    @Nullable Duration retryBackoff,
    @Nullable Duration timeout,
    @Nullable String compensationStepId,
    @NotNull Map<String, Object> config
) {
    public WorkflowStepDefinition {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        config = config != null ? Map.copyOf(config) : Map.of();
    }

    /**
     * Creates an ACTION step with sensible defaults.
     */
    public static WorkflowStepDefinition action(String stepId, String name, String operatorId) {
        return new WorkflowStepDefinition(
            stepId, name, WorkflowStepKind.ACTION,
            operatorId, null, null, null, null, null,
            0, null, null, null, Map.of());
    }

    /**
     * Creates a DECISION step.
     */
    public static WorkflowStepDefinition decision(
            String stepId, String name, String celCondition,
            String nextOnTrue, String nextOnFalse) {
        return new WorkflowStepDefinition(
            stepId, name, WorkflowStepKind.DECISION,
            null, celCondition, nextOnTrue, nextOnFalse, null, null,
            0, null, null, null, Map.of());
    }

    /**
     * Creates a WAIT step.
     */
    public static WorkflowStepDefinition wait(String stepId, String name, Duration timeout) {
        return new WorkflowStepDefinition(
            stepId, name, WorkflowStepKind.WAIT,
            null, null, null, null, null, null,
            0, null, timeout, null, Map.of());
    }

    /**
     * Creates a SUB_WORKFLOW step.
     */
    public static WorkflowStepDefinition subWorkflow(String stepId, String name, String subWorkflowId) {
        return new WorkflowStepDefinition(
            stepId, name, WorkflowStepKind.SUB_WORKFLOW,
            null, null, null, null, null, subWorkflowId,
            0, null, null, null, Map.of());
    }

    /**
     * Creates a HUMAN_IN_THE_LOOP checkpoint step.
     *
     * <p>When execution reaches this step, the workflow transitions to
     * {@link com.ghatana.platform.workflow.WorkflowRunStatus#WAITING_FOR_HITL} and
     * pauses until an operator calls
     * {@link com.ghatana.platform.workflow.runtime.HitlPauseOperator#approve(String)} or
     * {@link com.ghatana.platform.workflow.runtime.HitlPauseOperator#reject(String, String)}.
     *
     * @param stepId   unique step identifier
     * @param name     human-readable name for the checkpoint
     * @param nextStep the step to execute after the HITL is approved (null for end)
     */
    public static WorkflowStepDefinition humanInTheLoop(String stepId, String name, String nextStep) {
        return new WorkflowStepDefinition(
            stepId, name, WorkflowStepKind.HUMAN_IN_THE_LOOP,
            null, null, null, null, nextStep, null,
            0, null, null, null, Map.of());
    }

    /**
     * Returns a copy with retry settings applied.
     */
    public WorkflowStepDefinition withRetries(int max, Duration backoff) {
        return new WorkflowStepDefinition(
            stepId, name, kind, operatorId, celCondition,
            nextStepOnTrue, nextStepOnFalse, nextStep, subWorkflowId,
            max, backoff, timeout, compensationStepId, config);
    }

    /**
     * Returns a copy with a timeout applied.
     */
    public WorkflowStepDefinition withTimeout(Duration timeout) {
        return new WorkflowStepDefinition(
            stepId, name, kind, operatorId, celCondition,
            nextStepOnTrue, nextStepOnFalse, nextStep, subWorkflowId,
            maxRetries, retryBackoff, timeout, compensationStepId, config);
    }

    /**
     * Returns a copy with a compensation step reference.
     */
    public WorkflowStepDefinition withCompensation(String compensationStepId) {
        return new WorkflowStepDefinition(
            stepId, name, kind, operatorId, celCondition,
            nextStepOnTrue, nextStepOnFalse, nextStep, subWorkflowId,
            maxRetries, retryBackoff, timeout, compensationStepId, config);
    }

    /**
     * Returns a copy with the next step set.
     */
    public WorkflowStepDefinition withNextStep(String nextStep) {
        return new WorkflowStepDefinition(
            stepId, name, kind, operatorId, celCondition,
            nextStepOnTrue, nextStepOnFalse, nextStep, subWorkflowId,
            maxRetries, retryBackoff, timeout, compensationStepId, config);
    }
}
