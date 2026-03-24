package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of task registration.
 *
 * @param success Whether registration succeeded
 * @param taskId  The registered task ID (if successful)
 * @param message Result message
 * @doc.type record
 * @doc.purpose Task registration result
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TaskRegistrationResult(
        boolean success,
        @Nullable String taskId,
        @NotNull String message
) {
    public static TaskRegistrationResult success(@NotNull String taskId) {
        return new TaskRegistrationResult(true, taskId, "Task registered successfully");
    }

    public static TaskRegistrationResult failure(@NotNull String message) {
        return new TaskRegistrationResult(false, null, message);
    }

    public static TaskRegistrationResult conflict(@NotNull String message) {
        return new TaskRegistrationResult(false, null, "Conflict: " + message);
    }
}
