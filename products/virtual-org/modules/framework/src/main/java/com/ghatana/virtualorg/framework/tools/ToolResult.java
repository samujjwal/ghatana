package com.ghatana.virtualorg.framework.tools;

import java.util.Map;
import java.util.Objects;

/**
 * Result from tool execution.
 *
 * <p>
 * <b>Purpose</b><br>
 * Captures the outcome of a tool execution, including success/failure status,
 * output data, and error information.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Successful result
 * ToolResult success = ToolResult.success(Map.of("pr_number", 123, "url", "https://..."));
 *
 * // Failed result
 * ToolResult failure = ToolResult.failure("Connection timeout");
 *
 * // Check result
 * if (result.isSuccess()) {
 *     int prNumber = result.getInt("pr_number");
 * } else {
 *     String error = result.getError();
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Tool execution result
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ToolResult {

    /**
     * Result status codes.
     */
    public enum Status {
        SUCCESS,
        FAILURE,
        PERMISSION_DENIED,
        RATE_LIMITED,
        TIMEOUT,
        VALIDATION_ERROR
    }

    private final Status status;
    private final Map<String, Object> data;
    private final String error;
    private final long executionTimeMs;

    private ToolResult(Status status, Map<String, Object> data, String error, long executionTimeMs) {
        this.status = Objects.requireNonNull(status, "status required");
        this.data = data != null ? Map.copyOf(data) : Map.of();
        this.error = error;
        this.executionTimeMs = executionTimeMs;
    }

    // ========== Factory Methods ==========
    /**
     * Creates a successful result with data.
     *
     * @param data The output data
     * @return A successful result
     */
    public static ToolResult success(Map<String, Object> data) {
        return new ToolResult(Status.SUCCESS, data, null, 0);
    }

    /**
     * Creates a successful result with data and execution time.
     *
     * @param data The output data
     * @param executionTimeMs The execution time in milliseconds
     * @return A successful result
     */
    public static ToolResult success(Map<String, Object> data, long executionTimeMs) {
        return new ToolResult(Status.SUCCESS, data, null, executionTimeMs);
    }

    /**
     * Creates a successful result with a single value.
     *
     * @param key The output key
     * @param value The output value
     * @return A successful result
     */
    public static ToolResult success(String key, Object value) {
        return new ToolResult(Status.SUCCESS, Map.of(key, value), null, 0);
    }

    /**
     * Creates a failure result.
     *
     * @param error The error message
     * @return A failure result
     */
    public static ToolResult failure(String error) {
        return new ToolResult(Status.FAILURE, Map.of(), error, 0);
    }

    /**
     * Creates a failure result from an exception.
     *
     * @param exception The exception
     * @return A failure result
     */
    public static ToolResult failure(Throwable exception) {
        String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return new ToolResult(Status.FAILURE, Map.of("exceptionType", exception.getClass().getName()), message, 0);
    }

    /**
     * Creates a permission denied result.
     *
     * @return A permission denied result
     */
    public static ToolResult permissionDenied() {
        return new ToolResult(Status.PERMISSION_DENIED, Map.of(), "Permission denied", 0);
    }

    /**
     * Creates a permission denied result with details.
     *
     * @param missingPermission The missing permission
     * @return A permission denied result
     */
    public static ToolResult permissionDenied(String missingPermission) {
        return new ToolResult(Status.PERMISSION_DENIED,
                Map.of("missingPermission", missingPermission),
                "Permission denied: " + missingPermission, 0);
    }

    /**
     * Creates a rate limited result.
     *
     * @return A rate limited result
     */
    public static ToolResult rateLimited() {
        return new ToolResult(Status.RATE_LIMITED, Map.of(), "Rate limit exceeded", 0);
    }

    /**
     * Creates a rate limited result with retry info.
     *
     * @param retryAfterSeconds Seconds until retry is allowed
     * @return A rate limited result
     */
    public static ToolResult rateLimited(int retryAfterSeconds) {
        return new ToolResult(Status.RATE_LIMITED,
                Map.of("retryAfterSeconds", retryAfterSeconds),
                "Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds", 0);
    }

    /**
     * Creates a timeout result.
     *
     * @return A timeout result
     */
    public static ToolResult timeout() {
        return new ToolResult(Status.TIMEOUT, Map.of(), "Execution timed out", 0);
    }

    /**
     * Creates a validation error result.
     *
     * @param validationError The validation error message
     * @return A validation error result
     */
    public static ToolResult validationError(String validationError) {
        return new ToolResult(Status.VALIDATION_ERROR, Map.of(), validationError, 0);
    }

    /**
     * Creates an error result (alias for failure).
     *
     * @param error The error message
     * @return A failure result
     */
    public static ToolResult error(String error) {
        return failure(error);
    }

    /**
     * Creates a partial success result (completed with warnings).
     *
     * @param data The output data
     * @param warning Warning message
     * @return A partial success result
     */
    public static ToolResult partialSuccess(Map<String, Object> data, String warning) {
        Map<String, Object> dataWithWarning = new java.util.HashMap<>(data);
        dataWithWarning.put("_warning", warning);
        return new ToolResult(Status.SUCCESS, dataWithWarning, null, 0);
    }

    // ========== Accessors ==========
    public Status getStatus() {
        return status;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getError() {
        return error;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFailure() {
        return status != Status.SUCCESS;
    }

    public boolean isRetryable() {
        return status == Status.RATE_LIMITED || status == Status.TIMEOUT;
    }

    // ========== Data Accessors ==========
    /**
     * Gets a string value from the data.
     *
     * @param key The key
     * @return The string value or null
     */
    public String getString(String key) {
        Object value = data.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * Gets an integer value from the data.
     *
     * @param key The key
     * @return The integer value or 0
     */
    public int getInt(String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    /**
     * Gets a boolean value from the data.
     *
     * @param key The key
     * @return The boolean value or false
     */
    public boolean getBoolean(String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    /**
     * Converts this result to a string suitable for LLM consumption.
     *
     * @return A formatted string representation
     */
    public String toToolResponseString() {
        if (isSuccess()) {
            if (data.isEmpty()) {
                return "Success";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Success:\n");
            for (var entry : data.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            return sb.toString();
        } else {
            return "Error (" + status + "): " + (error != null ? error : "Unknown error");
        }
    }

    @Override
    public String toString() {
        return "ToolResult{"
                + "status=" + status
                + ", data=" + data
                + ", error='" + error + '\''
                + ", executionTimeMs=" + executionTimeMs
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolResult that = (ToolResult) o;
        return executionTimeMs == that.executionTimeMs
                && status == that.status
                && Objects.equals(data, that.data)
                && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, data, error, executionTimeMs);
    }
}
