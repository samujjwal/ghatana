package com.ghatana.agent.memory.model.taskstate;

/**
 * Lifecycle status for a task.
 *
 * @doc.type enum
 * @doc.purpose Task lifecycle states
 * @doc.layer agent-memory
 */
public enum TaskLifecycleStatus {
    CREATED,
    PLANNING,
    IN_PROGRESS,
    BLOCKED,
    PAUSED,
    COMPLETED,
    FAILED,
    ARCHIVED
}
