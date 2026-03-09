/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of WorkflowContext.
 *
 * @doc.type class
 * @doc.purpose Default workflow context implementation
 * @doc.layer core
 * @doc.pattern Value Object
 */
public class DefaultWorkflowContext implements WorkflowContext {

    private final String workflowId;
    private final String tenantId;
    private final String correlationId;
    private volatile String currentStep;
    private volatile String category;
    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    /**
     * Creates a new context for a workflow.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     */
    public DefaultWorkflowContext(@NotNull String workflowId, @NotNull String tenantId) {
        this.workflowId = Objects.requireNonNull(workflowId, "workflowId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.correlationId = UUID.randomUUID().toString();
    }

    /**
     * Creates a new context with a specific correlation ID.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @param correlationId The correlation ID for tracing
     */
    public DefaultWorkflowContext(
            @NotNull String workflowId,
            @NotNull String tenantId,
            @NotNull String correlationId) {
        this.workflowId = Objects.requireNonNull(workflowId, "workflowId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId cannot be null");
    }

    @Override
    @NotNull
    public String getWorkflowId() {
        return workflowId;
    }

    @Override
    @NotNull
    public String getTenantId() {
        return tenantId;
    }

    @Override
    @NotNull
    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    @Nullable
    public String getCurrentStep() {
        return currentStep;
    }

    /**
     * Sets the current step being executed.
     */
    public void setCurrentStep(@Nullable String currentStep) {
        this.currentStep = currentStep;
    }

    @Override
    @Nullable
    public String getCategory() {
        return category;
    }

    /**
     * Sets the workflow category.
     */
    public void setCategory(@Nullable String category) {
        this.category = category;
    }

    @Override
    public void setVariable(@NotNull String key, @Nullable Object value) {
        Objects.requireNonNull(key, "key cannot be null");
        if (value == null) {
            variables.remove(key);
        } else {
            variables.put(key, value);
        }
    }

    @Override
    @Nullable
    public Object getVariable(@NotNull String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return variables.get(key);
    }

    @Override
    @NotNull
    public Map<String, Object> getVariables() {
        return Map.copyOf(variables);
    }

    @Override
    @NotNull
    public WorkflowContext copy() {
        DefaultWorkflowContext copy = new DefaultWorkflowContext(
            workflowId, tenantId, correlationId
        );
        copy.setCurrentStep(this.currentStep);
        copy.setCategory(this.category);
        this.variables.forEach(copy::setVariable);
        return copy;
    }

    /**
     * Creates a builder for fluent context construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DefaultWorkflowContext.
     */
    public static class Builder {
        private String workflowId;
        private String tenantId;
        private String correlationId;
        private String currentStep;
        private String category;
        private final Map<String, Object> variables = new ConcurrentHashMap<>();

        public Builder workflowId(@NotNull String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public Builder tenantId(@NotNull String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder correlationId(@NotNull String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder currentStep(@Nullable String currentStep) {
            this.currentStep = currentStep;
            return this;
        }

        public Builder category(@Nullable String category) {
            this.category = category;
            return this;
        }

        public Builder variable(@NotNull String key, @Nullable Object value) {
            if (value != null) {
                this.variables.put(key, value);
            }
            return this;
        }

        public DefaultWorkflowContext build() {
            Objects.requireNonNull(workflowId, "workflowId is required");
            Objects.requireNonNull(tenantId, "tenantId is required");

            DefaultWorkflowContext context;
            if (correlationId != null) {
                context = new DefaultWorkflowContext(workflowId, tenantId, correlationId);
            } else {
                context = new DefaultWorkflowContext(workflowId, tenantId);
            }

            context.setCurrentStep(currentStep);
            context.setCategory(category);
            variables.forEach(context::setVariable);

            return context;
        }
    }

    @Override
    public String toString() {
        return "DefaultWorkflowContext{" +
                "workflowId='" + workflowId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", currentStep='" + currentStep + '\'' +
                ", category='" + category + '\'' +
                ", variablesCount=" + variables.size() +
                '}';
    }
}
