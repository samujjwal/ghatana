package com.ghatana.statestore.checkpoint;

import io.activej.promise.Promise;

/**
 * Storage interface for persisting checkpoint metadata.
 * Implementations handle durable storage of checkpoint/savepoint metadata for recovery.
 */
public interface CheckpointStorage {
    
    /**
     * Save checkpoint metadata.
     *
     * @param metadata Checkpoint metadata to persist
     * @return Promise resolving when metadata is saved
     */
    Promise<CheckpointMetadata> saveCheckpoint(CheckpointMetadata metadata);
    
    /**
     * Save savepoint metadata.
     * Savepoints are stored separately and never auto-deleted.
     *
     * @param metadata Savepoint metadata to persist
     * @return Promise resolving when metadata is saved
     */
    Promise<CheckpointMetadata> saveSavepoint(CheckpointMetadata metadata);
    
    /**
     * Load checkpoint or savepoint metadata.
     *
     * @param checkpointId ID of the checkpoint/savepoint to load
     * @return Promise resolving to metadata or null if not found
     */
    Promise<CheckpointMetadata> loadCheckpoint(CheckpointId checkpointId);
    
    /**
     * Delete checkpoint metadata and associated snapshots.
     *
     * @param checkpointId ID of the checkpoint to delete
     * @return Promise resolving when checkpoint is deleted
     */
    Promise<Void> deleteCheckpoint(CheckpointId checkpointId);
}
