package com.ghatana.virtualorg.workflows;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of workflow execution containing outputs, status, and error details.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates workflow execution outcome:
 * - Success/failure status
 * - Output data and artifacts
 * - Error information if failed
 * - Execution metrics
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowResult result = WorkflowResult.success()
 *     .withOutput("approvalStatus", "approved")
 *     .withOutput("reviewComments", comments)
 *     .withMetric("reviewersCount", 3)
 *     .build();
 * 
 * if (result.isSuccess()) {
 *     String status = result.getOutput("approvalStatus", String.class)
 *         .orElseThrow();
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable after build() - safe for concurrent access.
 *
 * @doc.type class
 * @doc.purpose Workflow execution result holder
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class WorkflowResult {

    private final boolean success;
    private final Map<String, Object> outputs;
    private final Map<String, Object> metrics;
    private final String errorMessage;
    private final Throwable error;

    private WorkflowResult(Builder builder) {
        this.success = builder.success;
        this.outputs = Map.copyOf(builder.outputs);
        this.metrics = Map.copyOf(builder.metrics);
        this.errorMessage = builder.errorMessage;
        this.error = builder.error;
    }

    /**
     * Returns true if workflow execution succeeded.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns true if workflow execution failed.
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * Gets an output value by key.
     *
     * @param key the output key
     * @param type the expected type
     * @return the output value
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOutput(String key, Class<T> type) {
        Object value = outputs.get(key);
        return value != null && type.isInstance(value)
            ? Optional.of((T) value)
            : Optional.empty();
    }

    /**
     * Gets an output value without type checking.
     */
    public Optional<Object> getOutput(String key) {
        return Optional.ofNullable(outputs.get(key));
    }

    /**
     * Gets all output values.
     */
    public Map<String, Object> getOutputs() {
        return outputs;
    }

    /**
     * Gets a metric value.
     */
    public Optional<Object> getMetric(String key) {
        return Optional.ofNullable(metrics.get(key));
    }

    /**
     * Gets all metrics.
     */
    public Map<String, Object> getMetrics() {
        return metrics;
    }

    /**
     * Gets the error message if workflow failed.
     */
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    /**
     * Gets the error throwable if workflow failed.
     */
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    /**
     * Creates a builder for successful result.
     */
    public static Builder success() {
        return new Builder(true);
    }

    /**
     * Creates a builder for failed result.
     */
    public static Builder failure(String errorMessage) {
        return new Builder(false).withErrorMessage(errorMessage);
    }

    /**
     * Creates a builder for failed result with throwable.
     */
    public static Builder failure(String errorMessage, Throwable error) {
        return new Builder(false)
            .withErrorMessage(errorMessage)
            .withError(error);
    }

    /**
     * Builder for WorkflowResult.
     */
    public static final class Builder {
        private final boolean success;
        private final Map<String, Object> outputs = new HashMap<>();
        private final Map<String, Object> metrics = new HashMap<>();
        private String errorMessage;
        private Throwable error;

        private Builder(boolean success) {
            this.success = success;
        }

        public Builder withOutput(String key, Object value) {
            this.outputs.put(key, value);
            return this;
        }

        public Builder withOutputs(Map<String, Object> outputs) {
            this.outputs.putAll(outputs);
            return this;
        }

        public Builder withMetric(String key, Object value) {
            this.metrics.put(key, value);
            return this;
        }

        public Builder withMetrics(Map<String, Object> metrics) {
            this.metrics.putAll(metrics);
            return this;
        }

        public Builder withErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder withError(Throwable error) {
            this.error = error;
            return this;
        }

        public WorkflowResult build() {
            return new WorkflowResult(this);
        }
    }
}
