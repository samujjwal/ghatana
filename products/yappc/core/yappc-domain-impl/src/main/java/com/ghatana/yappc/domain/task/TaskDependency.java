package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;

/**
 * Task dependency specification.
 *
 * @param taskId      The ID of the dependent task
 * @param required    Whether the dependency is required
 * @param description Description of the dependency relationship
 * @doc.type record
 * @doc.purpose Define task dependencies
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TaskDependency(
        @NotNull String taskId,
        boolean required,
        @NotNull String description
) {
    public TaskDependency {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be null or blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be null or blank");
        }
    }

    /**
     * Creates a required dependency.
     *
     * @param taskId      The dependent task ID
     * @param description The dependency description
     * @return A new required dependency
     */
    public static TaskDependency required(@NotNull String taskId, @NotNull String description) {
        return new TaskDependency(taskId, true, description);
    }

    /**
     * Creates an optional dependency.
     *
     * @param taskId      The dependent task ID
     * @param description The dependency description
     * @return A new optional dependency
     */
    public static TaskDependency optional(@NotNull String taskId, @NotNull String description) {
        return new TaskDependency(taskId, false, description);
    }
}
