package com.ghatana.yappc.service;

import org.jetbrains.annotations.NotNull;

import com.ghatana.platform.core.exception.ResourceNotFoundException;

/**
 * Exception thrown when no suitable agent can be found for a task.
 *
 * @doc.type class
 * @doc.purpose Exception for agent discovery failures
 * @doc.layer product
 * @doc.pattern Exception
 */
public class NoAgentFoundException extends ResourceNotFoundException {

    private final String taskId;

    public NoAgentFoundException(@NotNull String message) {
        super(message);
        this.taskId = null;
    }

    public NoAgentFoundException(@NotNull String message, @NotNull String taskId) {
        super(message);
        this.taskId = taskId;
    }

    public NoAgentFoundException(@NotNull String message, @NotNull String taskId, @NotNull Throwable cause) {
        super(message, cause);
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }
}
