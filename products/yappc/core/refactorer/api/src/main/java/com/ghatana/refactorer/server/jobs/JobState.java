package com.ghatana.refactorer.server.jobs;

/**
 * Represents the canonical job lifecycle states.
 *
 * @doc.type enum
 * @doc.purpose Standardize job status transitions across controllers, stores, and telemetry.
 * @doc.layer product
 * @doc.pattern Enum
 */
public enum JobState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
