package com.ghatana.statestore.checkpoint;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Coordinates checkpoints across distributed operators in the event processing pipeline.
 * Manages checkpoint lifecycle: barrier injection, operator acknowledgements, completion tracking.
 * 
 * <p>Checkpoint Protocol:
 * <ol>
 *   <li>Coordinator triggers checkpoint and injects barriers into streams</li>
 *   <li>Operators receive barriers, snapshot state, acknowledge</li>
 *   <li>Coordinator collects all acks and marks checkpoint complete</li>
 *   <li>Checkpoint metadata persisted for recovery</li>
 * </ol>
 */
public interface CheckpointCoordinator {
    
    /**
     * Trigger a new automatic checkpoint.
     * Creates a new checkpoint ID, injects barriers, and waits for operator acknowledgements.
     *
     * @return Promise resolving to checkpoint metadata when complete or failed
     */
    Promise<CheckpointMetadata> triggerCheckpoint();
    
    /**
     * Trigger a named savepoint for manual backup.
     * Savepoints are durable and not subject to automatic retention policies.
     *
     * @param name Human-readable savepoint name
     * @return Promise resolving to savepoint metadata when complete
     */
    Promise<CheckpointMetadata> triggerSavepoint(String name);
    
    /**
     * Acknowledge a checkpoint from an operator.
     * Called by operators after successfully snapshotting their state.
     *
     * @param checkpointId ID of the checkpoint being acknowledged
     * @param operatorId ID of the operator acknowledging
     * @param stateSize Size of the operator's state snapshot in bytes
     * @param snapshotPath Path or reference to the persisted snapshot
     * @return Promise resolving when acknowledgement is recorded
     */
    Promise<Void> acknowledgeCheckpoint(CheckpointId checkpointId, String operatorId, 
                                       long stateSize, String snapshotPath);
    
    /**
     * Restore system from a checkpoint or savepoint.
     * Loads checkpoint metadata and coordinates operator state restoration.
     *
     * @param checkpointId ID of the checkpoint/savepoint to restore from
     * @return Promise resolving when all operators have restored state
     */
    Promise<Void> restoreFromCheckpoint(CheckpointId checkpointId);
    
    /**
     * Get metadata for a specific checkpoint.
     *
     * @param checkpointId Checkpoint ID to query
     * @return Optional containing metadata if checkpoint exists
     */
    Optional<CheckpointMetadata> getCheckpointMetadata(CheckpointId checkpointId);
    
    /**
     * List all available checkpoints and savepoints.
     *
     * @param includeCheckpoints Include automatic checkpoints
     * @param includeSavepoints Include manual savepoints
     * @return List of checkpoint metadata sorted by creation time (newest first)
     */
    List<CheckpointMetadata> listCheckpoints(boolean includeCheckpoints, boolean includeSavepoints);
    
    /**
     * Delete old checkpoints according to retention policy.
     * Savepoints are never deleted automatically.
     *
     * @param retentionCount Number of recent checkpoints to retain
     * @param retentionDuration Maximum age of checkpoints to retain
     * @return Promise resolving to count of deleted checkpoints
     */
    Promise<Integer> cleanupCheckpoints(int retentionCount, Duration retentionDuration);
    
    /**
     * Cancel an in-progress checkpoint.
     *
     * @param checkpointId ID of the checkpoint to cancel
     * @return Promise resolving when checkpoint is cancelled
     */
    Promise<Void> cancelCheckpoint(CheckpointId checkpointId);
    
    /**
     * Start the checkpoint coordinator.
     * Initializes periodic checkpoint triggering if configured.
     *
     * @return Promise resolving when coordinator is ready
     */
    Promise<Void> start();
    
    /**
     * Stop the checkpoint coordinator.
     * Cancels any in-progress checkpoints and stops periodic triggering.
     *
     * @return Promise resolving when coordinator is stopped
     */
    Promise<Void> stop();
}
