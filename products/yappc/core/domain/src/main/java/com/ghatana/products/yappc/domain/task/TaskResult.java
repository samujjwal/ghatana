package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Result of task execution.
 *
 * @param <TOutput> Output type
 * @doc.type record
 * @doc.purpose Task execution result container
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TaskResult<TOutput>(
        @NotNull String executionId,
        @NotNull String taskId,
        @NotNull TaskExecutionStatus status,
        @Nullable TOutput output,
        @Nullable String error,
        @NotNull Instant startTime,
        @Nullable Instant endTime,
        @NotNull Map<String, Object> metadata
) {
    /**
     * Creates a successful task result.
     *
     * @param executionId Execution ID
     * @param taskId      Task ID
     * @param output      Task output
     * @param <T>         Output type
     * @return A successful result
     */
    public static <T> TaskResult<T> success(
            @NotNull String executionId,
            @NotNull String taskId,
            @NotNull T output
    ) {
        return new TaskResult<>(
                executionId,
                taskId,
                TaskExecutionStatus.COMPLETED,
                output,
                null,
                Instant.now(),
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Creates a failed task result.
     *
     * @param executionId Execution ID
     * @param taskId      Task ID
     * @param error       Error message
     * @param <T>         Output type
     * @return A failed result
     */
    public static <T> TaskResult<T> failure(
            @NotNull String executionId,
            @NotNull String taskId,
            @NotNull String error
    ) {
        return new TaskResult<>(
                executionId,
                taskId,
                TaskExecutionStatus.FAILED,
                null,
                error,
                Instant.now(),
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Checks if the task execution was successful.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return status == TaskExecutionStatus.COMPLETED && output != null;
    }

    /**
     * Checks if the task execution failed.
     *
     * @return true if failed
     */
    public boolean isFailure() {
        return status == TaskExecutionStatus.FAILED || error != null;
    }

    /**
     * Gets the execution duration in milliseconds.
     *
     * @return Duration in milliseconds, or -1 if not completed
     */
    public long getDurationMs() {
        if (endTime == null) {
            return -1;
        }
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
}
