package com.ghatana.virtualorg.tool;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

/**
 * Result of a tool execution.
 *
 * <p><b>Purpose</b><br>
 * Value object representing the outcome of a tool execution with
 * success/failure status, result data, timing, and metadata.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Success case
 * ToolResult success = ToolResult.success(
 *     "File created successfully",
 *     Duration.ofMillis(150)
 * );
 * 
 * // Failure case
 * ToolResult failure = ToolResult.failure(
 *     "Permission denied",
 *     Duration.ofMillis(50)
 * );
 * }</pre>
 *
 * <p><b>Validation</b><br>
 * Canonical constructor validates:
 * - duration: non-null
 * - metadata: non-null (may be empty)
 * - error: must be provided when success=false
 *
 * @param success      whether the execution was successful
 * @param result       the result data
 * @param error        error message if failed
 * @param duration     execution duration
 * @param metadata     additional metadata
 * @doc.type record
 * @doc.purpose Tool execution result value object
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record ToolResult(
        boolean success,
        @Nullable String result,
        @Nullable String error,
        @NotNull Duration duration,
        @NotNull Map<String, String> metadata
) {
    public ToolResult {
        if (duration == null) {
            throw new IllegalArgumentException("duration cannot be null");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata cannot be null");
        }
        if (!success && error == null) {
            throw new IllegalArgumentException("error must be provided when success is false");
        }
    }

    /**
     * Creates a successful result.
     *
     * @param result   the result data
     * @param duration the duration
     * @return the tool result
     */
    public static ToolResult success(@NotNull String result, @NotNull Duration duration) {
        return new ToolResult(true, result, null, duration, Map.of());
    }

    /**
     * Creates a successful result with metadata.
     *
     * @param result   the result data
     * @param duration the duration
     * @param metadata the metadata
     * @return the tool result
     */
    public static ToolResult success(
            @NotNull String result,
            @NotNull Duration duration,
            @NotNull Map<String, String> metadata) {
        return new ToolResult(true, result, null, duration, metadata);
    }

    /**
     * Creates a failed result.
     *
     * @param error    the error message
     * @param duration the duration
     * @return the tool result
     */
    public static ToolResult failure(@NotNull String error, @NotNull Duration duration) {
        return new ToolResult(false, null, error, duration, Map.of());
    }

    /**
     * Creates a failed result with metadata.
     *
     * @param error    the error message
     * @param duration the duration
     * @param metadata the metadata
     * @return the tool result
     */
    public static ToolResult failure(
            @NotNull String error,
            @NotNull Duration duration,
            @NotNull Map<String, String> metadata) {
        return new ToolResult(false, null, error, duration, metadata);
    }
}
