package com.ghatana.digitalmarketing.domain.command;

/**
 * Lifecycle status of a DMOS command.
 *
 * <p>Commands transition in one direction only:
 * {@code PENDING → EXECUTING → SUCCEEDED | FAILED → ROLLED_BACK}</p>
 *
 * @doc.type class
 * @doc.purpose DMOS command lifecycle states (DMOS-F2-003)
 * @doc.layer product
 * @doc.pattern CQRS
 */
public enum DmCommandStatus {
    /** Command created but not yet picked up by the executor. */
    PENDING,
    /** Command is currently being executed. */
    EXECUTING,
    /** Command completed successfully. */
    SUCCEEDED,
    /** Command failed and is not retryable. */
    FAILED,
    /** Failed command has been rolled back via compensating action. */
    ROLLED_BACK
}
