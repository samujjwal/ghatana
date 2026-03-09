package com.ghatana.statestore.checkpoint;

/**
 * Type of checkpoint: automatic checkpoint or manual savepoint.
 */
public enum CheckpointType {
    /**
     * Automatic checkpoint triggered by coordinator for fault tolerance.
     * Subject to retention policy and automatic cleanup.
     */
    CHECKPOINT,
    
    /**
     * Manual savepoint created by user for upgrades or manual recovery.
     * Durable and not subject to automatic cleanup.
     */
    SAVEPOINT
}
