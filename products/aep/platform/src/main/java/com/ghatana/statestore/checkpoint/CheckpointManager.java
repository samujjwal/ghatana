package com.ghatana.statestore.checkpoint;

import java.time.Instant;
import java.util.Map;

import io.activej.promise.Promise;

/**
 * Interface for managing state store checkpoints.
 * 
 * Day 27 Implementation: Provides checkpoint creation, restoration, and management
 * capabilities for durable state recovery.
 */
public interface CheckpointManager {
    
    /**
     * Create a new checkpoint with the given ID.
     * 
     * @param checkpointId Unique identifier for the checkpoint
     * @return Promise that completes when checkpoint is created
     */
    Promise<Void> createCheckpoint(String checkpointId);
    
    /**
     * Restore system state from a checkpoint.
     * 
     * @param checkpointId The checkpoint to restore from
     * @return Promise that completes when restoration is finished
     */
    Promise<Void> restoreFromCheckpoint(String checkpointId);
    
    /**
     * List all available checkpoints.
     * 
     * @return Promise of map from checkpoint ID to creation time
     */
    Promise<Map<String, Instant>> listCheckpoints();
    
    /**
     * Delete a checkpoint.
     * 
     * @param checkpointId The checkpoint to delete
     * @return Promise of true if checkpoint existed and was deleted
     */
    Promise<Boolean> deleteCheckpoint(String checkpointId);
    
    /**
     * Get information about a specific checkpoint.
     * 
     * @param checkpointId The checkpoint to inspect
     * @return Promise of optional checkpoint info
     */
    Promise<java.util.Optional<CheckpointInfo>> getCheckpointInfo(String checkpointId);
    
    /**
     * Clean up old checkpoints based on retention policy.
     * 
     * @param maxAge Maximum age for checkpoints to keep
     * @param maxCount Maximum number of checkpoints to keep
     * @return Promise of number of checkpoints deleted
     */
    Promise<Integer> cleanupOldCheckpoints(java.time.Duration maxAge, int maxCount);
}