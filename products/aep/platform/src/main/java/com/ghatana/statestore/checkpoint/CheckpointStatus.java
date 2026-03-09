package com.ghatana.statestore.checkpoint;

/**
 * Status of a checkpoint operation.
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
