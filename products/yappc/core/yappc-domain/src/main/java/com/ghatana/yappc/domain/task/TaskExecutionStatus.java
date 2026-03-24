package com.ghatana.products.yappc.domain.task;

/**
 * Task execution status enumeration.
 *
 * @doc.type enum
 * @doc.purpose Track task execution lifecycle status
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum TaskExecutionStatus {
    PENDING("Pending", "Task queued for execution"),
    RUNNING("Running", "Task is executing"),
    COMPLETED("Completed", "Task completed successfully"),
    FAILED("Failed", "Task execution failed"),
    CANCELLED("Cancelled", "Task was cancelled");

    private final String displayName;
    private final String description;

    TaskExecutionStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Checks if the status represents a terminal state.
     *
     * @return true if terminal (completed, failed, or cancelled)
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * Checks if the status represents an active state.
     *
     * @return true if active (pending or running)
     */
    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
