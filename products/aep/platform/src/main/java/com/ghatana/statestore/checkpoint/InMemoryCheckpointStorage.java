package com.ghatana.statestore.checkpoint;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of CheckpointStorage for testing.
 * Does not persist metadata to disk - all data is lost on restart.
 */
public class InMemoryCheckpointStorage implements CheckpointStorage {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCheckpointStorage.class);
    
    private final Map<CheckpointId, CheckpointMetadata> checkpoints = new ConcurrentHashMap<>();
    private final Map<CheckpointId, CheckpointMetadata> savepoints = new ConcurrentHashMap<>();
    
    @Override
    public Promise<CheckpointMetadata> saveCheckpoint(CheckpointMetadata metadata) {
        LOGGER.debug("Saving checkpoint: {}", metadata.getCheckpointId());
        checkpoints.put(metadata.getCheckpointId(), metadata);
        return Promise.of(metadata);
    }
    
    @Override
    public Promise<CheckpointMetadata> saveSavepoint(CheckpointMetadata metadata) {
        LOGGER.debug("Saving savepoint: {}", metadata.getCheckpointId());
        savepoints.put(metadata.getCheckpointId(), metadata);
        return Promise.of(metadata);
    }
    
    @Override
    public Promise<CheckpointMetadata> loadCheckpoint(CheckpointId checkpointId) {
        CheckpointMetadata metadata = checkpoints.get(checkpointId);
        if (metadata == null) {
            metadata = savepoints.get(checkpointId);
        }
        
        LOGGER.debug("Loading checkpoint: {} - {}", checkpointId, metadata != null ? "found" : "not found");
        return Promise.of(metadata);
    }
    
    @Override
    public Promise<Void> deleteCheckpoint(CheckpointId checkpointId) {
        LOGGER.debug("Deleting checkpoint: {}", checkpointId);
        checkpoints.remove(checkpointId);
        // Never delete savepoints
        return Promise.complete();
    }
    
    /**
     * Clear all checkpoints and savepoints (for testing).
     */
    public void clear() {
        checkpoints.clear();
        savepoints.clear();
    }
    
    /**
     * Get count of stored checkpoints.
     */
    public int getCheckpointCount() {
        return checkpoints.size();
    }
    
    /**
     * Get count of stored savepoints.
     */
    public int getSavepointCount() {
        return savepoints.size();
    }
}
