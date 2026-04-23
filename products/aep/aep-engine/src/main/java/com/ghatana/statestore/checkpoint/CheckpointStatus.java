package com.ghatana.statestore.checkpoint;

/**
 * Status of a checkpoint operation.
  * @doc.type enum
 * @doc.purpose Provides checkpoint status functionality.
 * @doc.layer product
 * @doc.pattern Enum
*/
public enum CheckpointStatus {
    /**
     * Checkpoint has been initiated and barriers injected.
     */
    IN_PROGRESS,

    /**
     * All operators have acknowledged and checkpoint is complete.
     */
    COMPLETED,

    /**
     * Checkpoint failed due to timeout or operator failure.
     */
    FAILED,

    /**
     * Checkpoint was cancelled before completion.
     */
    CANCELLED
}
