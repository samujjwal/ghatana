package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;

/**
 * Result of task cancellation.
 *
 * @param success Whether cancellation succeeded
 * @param message Result message
 * @doc.type record
 * @doc.purpose Task cancellation result
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TaskCancellationResult(
        boolean success,
        @NotNull String message
) {
    public static TaskCancellationResult successful() {
        return new TaskCancellationResult(true, "Task cancelled successfully");
    }

    public static TaskCancellationResult notFound(@NotNull String executionId) {
        return new TaskCancellationResult(false, "Execution not found: " + executionId);
    }

    public static TaskCancellationResult alreadyCompleted() {
        return new TaskCancellationResult(false, "Task already completed");
    }

    public static TaskCancellationResult failure(@NotNull String message) {
        return new TaskCancellationResult(false, message);
    }
}
