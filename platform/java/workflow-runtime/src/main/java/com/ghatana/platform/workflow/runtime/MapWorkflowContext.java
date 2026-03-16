/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.WorkflowContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link WorkflowContext} adapter backed by a mutable {@link Map}.
 *
 * <p>Used internally by {@link DurableWorkflowRuntime} to bridge the step
 * execution context (a plain {@code Map<String, Object>}) into the
 * {@link WorkflowContext} interface required by {@link com.ghatana.platform.workflow.WorkflowExpressionEvaluator}.
 *
 * @doc.type class
 * @doc.purpose Map-backed WorkflowContext adapter for runtime step execution
 * @doc.layer platform
 * @doc.pattern Adapter
 */
final class MapWorkflowContext implements WorkflowContext {

    private final String workflowId;
    private final String tenantId;
    private final String correlationId;
    private final Map<String, Object> variables;
    private volatile String currentStep;

    MapWorkflowContext(
            @NotNull String workflowId,
            @NotNull String tenantId,
            @NotNull String correlationId,
            @NotNull Map<String, Object> variables) {
        this.workflowId = workflowId;
        this.tenantId = tenantId;
        this.correlationId = correlationId;
        this.variables = new ConcurrentHashMap<>(variables);
    }

    @Override
    public @NotNull String getWorkflowId() { return workflowId; }

    @Override
    public @NotNull String getTenantId() { return tenantId; }

    @Override
    public @NotNull String getCorrelationId() { return correlationId; }

    @Override
    public @Nullable String getCurrentStep() { return currentStep; }

    @Override
    public @Nullable String getCategory() { return null; }

    @Override
    public void setVariable(@NotNull String key, @Nullable Object value) {
        if (value == null) {
            variables.remove(key);
        } else {
            variables.put(key, value);
        }
    }

    @Override
    public @Nullable Object getVariable(@NotNull String key) {
        return variables.get(key);
    }

    @Override
    public @NotNull Map<String, Object> getVariables() {
        return variables;
    }

    void setCurrentStep(@Nullable String step) {
        this.currentStep = step;
    }

    @Override
    public @NotNull WorkflowContext copy() {
        MapWorkflowContext copy = new MapWorkflowContext(workflowId, tenantId, correlationId, variables);
        copy.currentStep = this.currentStep;
        return copy;
    }
}
