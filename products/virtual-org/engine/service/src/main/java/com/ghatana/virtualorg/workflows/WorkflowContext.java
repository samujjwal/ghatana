package com.ghatana.virtualorg.workflows;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Execution context for workflows, containing inputs, state, and metadata.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates all data needed for workflow execution:
 * - Input parameters and configuration
 * - Tenant and user context
 * - Intermediate workflow state
 * - Correlation IDs for tracing
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowContext context = WorkflowContext.builder()
 *     .withTenantId("acme-corp")
 *     .withUserId("user-123")
 *     .withInput("pullRequest", prData)
 *     .withInput("reviewers", reviewerList)
 *     .withCorrelationId("req-456")
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Mutable - not thread-safe. Each workflow execution should have its own context instance.
 *
 * @doc.type class
 * @doc.purpose Workflow execution context holder
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class WorkflowContext {

    private final String tenantId;
    private final String userId;
    private final String correlationId;
    private final Map<String, Object> inputs;
    private final Map<String, Object> state;
    private final Map<String, String> metadata;

    private WorkflowContext(Builder builder) {
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId");
        this.userId = builder.userId;
        this.correlationId = builder.correlationId;
        this.inputs = new HashMap<>(builder.inputs);
        this.state = new HashMap<>(builder.state);
        this.metadata = new HashMap<>(builder.metadata);
    }

    /**
     * Returns the tenant ID for multi-tenant isolation.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Returns the user ID who initiated the workflow.
     */
    public Optional<String> getUserId() {
        return Optional.ofNullable(userId);
    }

    /**
     * Returns the correlation ID for distributed tracing.
     */
    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    /**
     * Gets an input parameter by key.
     *
     * @param key the input parameter key
     * @param type the expected type
     * @return the input value
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getInput(String key, Class<T> type) {
        Object value = inputs.get(key);
        return value != null && type.isInstance(value) 
            ? Optional.of((T) value) 
            : Optional.empty();
    }

    /**
     * Gets an input parameter by key without type checking.
     */
    public Optional<Object> getInput(String key) {
        return Optional.ofNullable(inputs.get(key));
    }

    /**
     * Gets all input parameters.
     */
    public Map<String, Object> getInputs() {
        return new HashMap<>(inputs);
    }

    /**
     * Stores intermediate state during workflow execution.
     *
     * @param key the state key
     * @param value the state value
     */
    public void setState(String key, Object value) {
        state.put(key, value);
    }

    /**
     * Retrieves intermediate state.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getState(String key, Class<T> type) {
        Object value = state.get(key);
        return value != null && type.isInstance(value) 
            ? Optional.of((T) value) 
            : Optional.empty();
    }

    /**
     * Gets all workflow state.
     */
    public Map<String, Object> getState() {
        return new HashMap<>(state);
    }

    /**
     * Adds metadata for observability and debugging.
     */
    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    /**
     * Gets metadata value.
     */
    public Optional<String> getMetadata(String key) {
        return Optional.ofNullable(metadata.get(key));
    }

    /**
     * Creates a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for WorkflowContext.
     */
    public static final class Builder {
        private String tenantId;
        private String userId;
        private String correlationId;
        private final Map<String, Object> inputs = new HashMap<>();
        private final Map<String, Object> state = new HashMap<>();
        private final Map<String, String> metadata = new HashMap<>();

        private Builder() {}

        public Builder withTenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder withUserId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder withCorrelationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder withInput(String key, Object value) {
            this.inputs.put(key, value);
            return this;
        }

        public Builder withInputs(Map<String, Object> inputs) {
            this.inputs.putAll(inputs);
            return this;
        }

        public Builder withState(String key, Object value) {
            this.state.put(key, value);
            return this;
        }

        public Builder withMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public WorkflowContext build() {
            return new WorkflowContext(this);
        }
    }
}
