package com.ghatana.kernel.workflow;

/**
 * Status of a command execution.
 *
 * @doc.type enum
 * @doc.purpose Command lifecycle states (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum CommandStatus {
    /**
     * Command is pending execution.
     */
    PENDING,

    /**
     * Command is currently executing.
     */
    RUNNING,

    /**
     * Command completed successfully.
     */
    COMPLETED,

    /**
     * Command failed execution.
     */
    FAILED,

    /**
     * Command was cancelled.
     */
    CANCELLED,

    /**
     * Command is scheduled for future execution.
     */
    SCHEDULED
}
