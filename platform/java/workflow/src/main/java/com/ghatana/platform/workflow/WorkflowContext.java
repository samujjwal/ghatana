package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Context provided to a Workflow during execution.
 *
 * @doc.type interface
 * @doc.purpose Execution context for Workflows
 * @doc.layer core
 */
public interface WorkflowContext {

    /**
     * Returns the workflow ID.
     */
    @NotNull
    String getWorkflowId();

    /**
     * Returns the tenant ID.
     */
    @NotNull
    String getTenantId();

    /**
     * Returns the correlation ID for tracing.
     */
    @NotNull
    String getCorrelationId();

    /**
     * Returns the current step being executed.
     */
    @Nullable
    String getCurrentStep();

    /**
     * Returns the workflow category (DevSecOps stage).
     */
    @Nullable
    String getCategory();

    /**
     * Sets a variable in the workflow context.
     */
    void setVariable(@NotNull String key, @Nullable Object value);

    /**
     * Adds metadata to the workflow context (alias for setVariable).
     */
    default void addMetadata(@NotNull String key, @Nullable String value) {
        setVariable(key, value);
    }

    /**
     * Gets a variable from the workflow context.
     */
    @Nullable
    Object getVariable(@NotNull String key);

    /**
     * Returns all variables.
     */
    @NotNull
    Map<String, Object> getVariables();

    /**
     * Returns all workflow data as a map (alias for getVariables).
     */
    @NotNull
    default Map<String, Object> getData() {
        return getVariables();
    }

    /**
     * Gets a value from the context (alias for getVariable).
     */
    @Nullable
    default Object get(@NotNull String key) {
        return getVariable(key);
    }

    /**
     * Sets a value in the context (alias for setVariable).
     */
    default void put(@NotNull String key, @Nullable Object value) {
        setVariable(key, value);
    }

    /**
     * Checks if a key exists in the context.
     */
    default boolean containsKey(@NotNull String key) {
        return getVariables().containsKey(key);
    }

    /**
     * Gets a value from the context with a default value if not present.
     */
    @Nullable
    default Object getOrDefault(@NotNull String key, @Nullable Object defaultValue) {
        Object value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Returns context data as a map (alias for getVariables).
     */
    @NotNull
    default Map<String, Object> asMap() {
        return getVariables();
    }

    /**
     * Creates a shallow copy of the context.
     */
    @NotNull
    WorkflowContext copy();

    /**
     * Creates a context for a specific workflow.
     */
    static WorkflowContext forWorkflow(@NotNull String workflowId, @NotNull String tenantId) {
        return new DefaultWorkflowContext(workflowId, tenantId);
    }
}
