package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;

/**
 * Result of task removal/unregistration.
 *
 * @param success Whether removal succeeded
 * @param message Result message
 * @doc.type record
 * @doc.purpose Task removal result
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TaskRemovalResult(
        boolean success,
        @NotNull String message
) {
    public static TaskRemovalResult successful() {
        return new TaskRemovalResult(true, "Task unregistered successfully");
    }

    public static TaskRemovalResult notFound(@NotNull String taskId) {
        return new TaskRemovalResult(false, "Task not found: " + taskId);
    }

    public static TaskRemovalResult failure(@NotNull String message) {
        return new TaskRemovalResult(false, message);
    }
}
