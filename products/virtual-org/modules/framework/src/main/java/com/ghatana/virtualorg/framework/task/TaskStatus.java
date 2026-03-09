package com.ghatana.virtualorg.framework.task;

/**
 * Task lifecycle status enumeration.
 */
public enum TaskStatus {
    DECLARED("Task declared but not assigned"),
    ASSIGNED("Task assigned to agent"),
    STARTED("Task execution in progress"),
    COMPLETED("Task completed successfully"),
    FAILED("Task failed with error");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
