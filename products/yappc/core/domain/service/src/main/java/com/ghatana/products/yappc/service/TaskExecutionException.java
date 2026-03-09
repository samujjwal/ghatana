package com.ghatana.products.yappc.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when task execution fails.
 *
 * @doc.type class
 * @doc.purpose Exception for task execution failures
 * @doc.layer product
 * @doc.pattern Exception
 */
public class TaskExecutionException extends RuntimeException {

    private final String taskId;
    private final String agentName;

    public TaskExecutionException(@NotNull String message, @NotNull String taskId, @NotNull String agentName) {
        super(message);
        this.taskId = taskId;
        this.agentName = agentName;
    }

    public TaskExecutionException(
            @NotNull String message,
            @NotNull String taskId,
            @NotNull String agentName,
            @Nullable Throwable cause
    ) {
        super(message, cause);
        this.taskId = taskId;
        this.agentName = agentName;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getAgentName() {
        return agentName;
    }
}
