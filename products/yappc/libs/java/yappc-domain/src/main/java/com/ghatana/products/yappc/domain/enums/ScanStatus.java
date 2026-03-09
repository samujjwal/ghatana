package com.ghatana.products.yappc.domain.enums;

/**
 * Enumeration of possible scan job statuses in the YAPPC platform.
 *
 * <p>This enum represents the lifecycle states of a security scan job,
 * from initial pending state through completion or failure.</p>
 *
 * @doc.type enum
 * @doc.purpose Defines the lifecycle states for scan job execution
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum ScanStatus {

    /**
     * Scan job is queued and waiting to be executed.
     */
    PENDING("Pending", false),

    /**
     * Scan job is currently executing.
     */
    RUNNING("Running", false),

    /**
     * Scan job has completed successfully.
     */
    COMPLETED("Completed", true),

    /**
     * Scan job failed with an error.
     */
    FAILED("Failed", true),

    /**
     * Scan job was cancelled by user or system.
     */
    CANCELLED("Cancelled", true);

    private final String displayName;
    private final boolean terminal;

    ScanStatus(String displayName, boolean terminal) {
        this.displayName = displayName;
        this.terminal = terminal;
    }

    /**
     * Returns the human-readable display name.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this is a terminal state (no further transitions).
     *
     * @return true if terminal state
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * Checks if the scan is still active (not in a terminal state).
     *
     * @return true if scan is active
     */
    public boolean isActive() {
        return !terminal;
    }
}
