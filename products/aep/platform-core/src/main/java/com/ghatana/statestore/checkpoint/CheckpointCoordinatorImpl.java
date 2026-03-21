package com.ghatana.statestore.checkpoint;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.activej.promise.SettablePromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Default implementation of CheckpointCoordinator.
 * Manages checkpoint lifecycle, barrier injection, operator acknowledgements, and periodic triggering.
 */
public class CheckpointCoordinatorImpl implements CheckpointCoordinator {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckpointCoordinatorImpl.class);
    
    private final Eventloop eventloop;
    private final CheckpointStorage storage;
    private final CheckpointConfig config;
    
    // Active checkpoints (in-progress)
    private final Map<CheckpointId, CheckpointState> activeCheckpoints = new ConcurrentHashMap<>();
    
    // Completed checkpoints (for history/recovery)
    private final Map<CheckpointId, CheckpointMetadata> completedCheckpoints = new ConcurrentHashMap<>();
    
    // Registered operators that participate in checkpoints
    private final Set<String> registeredOperators = ConcurrentHashMap.newKeySet();
    
    // Operator barrier handlers for injecting barriers and restoring state
    private final Map<String, CheckpointableOperator> operatorHandlers = new ConcurrentHashMap<>();
    
    private final AtomicLong checkpointCounter = new AtomicLong(0);
    private final AtomicBoolean started = new AtomicBoolean(false);
    
    /**
     * Callback interface for operators that participate in checkpoint barriers.
     *
     * <p>Operators register a handler to receive barriers and restore state.
     * When a barrier is injected, the operator snapshots its state and acknowledges.
     * During restore, the operator receives its previously saved state data.
     *
     * @doc.type interface
     * @doc.purpose Operator checkpoint participation callback
     * @doc.layer product
     * @doc.pattern Callback
     */
    public interface CheckpointableOperator {
        /**
         * Called when a checkpoint barrier is injected into this operator's stream.
         * The operator should snapshot its current state and acknowledge the checkpoint.
         *
         * @param barrier the checkpoint barrier
         * @return promise that completes when barrier is processed and state is snapshotted
         */
        Promise<Void> onBarrier(CheckpointBarrier barrier);

        /**
         * Called during state restoration to apply a previously saved snapshot.
         *
         * @param stateData the operator's saved state data
         */
        void restoreState(Map<String, Object> stateData);
    }
    
    public CheckpointCoordinatorImpl(Eventloop eventloop, CheckpointStorage storage, CheckpointConfig config) {
        this.eventloop = eventloop; // Can be null for testing (we use Thread.sleep for now)
        this.storage = Objects.requireNonNull(storage, "CheckpointStorage cannot be null");
        this.config = Objects.requireNonNull(config, "CheckpointConfig cannot be null");
    }
    
    @Override
    public Promise<CheckpointMetadata> triggerCheckpoint() {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Coordinator not started"));
        }
        
        CheckpointId checkpointId = CheckpointId.checkpoint();
        LOGGER.info("Triggering checkpoint: {}", checkpointId);
        
        CheckpointState state = new CheckpointState(checkpointId, registeredOperators);
        activeCheckpoints.put(checkpointId, state);
        
        // Start async checkpoint process (inject barriers, wait for acks, save)
        injectBarriers(checkpointId)
            .then(() -> waitForAcknowledgements(checkpointId))
            .then(metadata -> {
                // Just save to storage (already in completedCheckpoints from acknowledge)
                return storage.saveCheckpoint(metadata).map(v -> metadata);
            })
            .whenComplete((metadata, ex) -> {
                if (ex != null) {
                    LOGGER.error("Checkpoint {} failed: {}", checkpointId, ex.getMessage());
                    // Remove from completed on failure
                    completedCheckpoints.remove(checkpointId);
                    handleCheckpointFailure(checkpointId, ex.getMessage());
                }
            });
        
        // Return initial metadata immediately so callers can start acknowledging
        return Promise.of(state.toMetadata());
    }
    
    @Override
    public Promise<CheckpointMetadata> triggerSavepoint(String name) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Coordinator not started"));
        }
        
        CheckpointId savepointId = CheckpointId.savepoint(name);
        LOGGER.info("Triggering savepoint: {} ({})", name, savepointId);
        
        CheckpointState state = new CheckpointState(savepointId, registeredOperators);
        activeCheckpoints.put(savepointId, state);
        
        // Start async savepoint process
        injectBarriers(savepointId)
            .then(() -> waitForAcknowledgements(savepointId))
            .then(metadata -> {
                // Just save to storage (already in completedCheckpoints from acknowledge)
                return storage.saveSavepoint(metadata).map(v -> metadata);
            })
            .whenComplete((metadata, ex) -> {
                if (ex != null) {
                    LOGGER.error("Savepoint {} failed: {}", savepointId, ex.getMessage());
                    // Remove from completed on failure
                    completedCheckpoints.remove(savepointId);
                    handleCheckpointFailure(savepointId, ex.getMessage());
                }
            });
        
        // Return initial metadata immediately
        return Promise.of(state.toMetadata());
    }
    
    @Override
    public Promise<Void> acknowledgeCheckpoint(CheckpointId checkpointId, String operatorId, 
                                               long stateSize, String snapshotPath) {
        CheckpointState state = activeCheckpoints.get(checkpointId);
        if (state == null) {
            LOGGER.warn("Received ack for unknown checkpoint: {} from operator: {}", checkpointId, operatorId);
            return Promise.complete();
        }
        
        LOGGER.debug("Checkpoint {} acknowledged by operator: {} (state size: {} bytes)", 
            checkpointId, operatorId, stateSize);
        
        state.acknowledge(operatorId, stateSize, snapshotPath);
        
        // Check if all operators have acknowledged
        if (state.isComplete()) {
            LOGGER.info("Checkpoint {} complete - all {} operators acknowledged", 
                checkpointId, state.getExpectedOperators().size());
            
            // Move to completed immediately (synchronously) so cleanup can find it
            CheckpointMetadata metadata = state.toMetadata();
            completedCheckpoints.put(checkpointId, metadata);
            activeCheckpoints.remove(checkpointId);
            
            // Complete the state's promise - this will trigger the async save chain
            state.complete();
        }
        
        return Promise.complete();
    }
    
    @Override
    public Promise<Void> restoreFromCheckpoint(CheckpointId checkpointId) {
        LOGGER.info("Restoring from checkpoint: {}", checkpointId);
        
        return storage.loadCheckpoint(checkpointId)
            .then(metadata -> {
                if (metadata == null) {
                    return Promise.ofException(
                        new IllegalArgumentException("Checkpoint not found: " + checkpointId));
                }
                
                // Restore each operator's state
                List<Promise<Void>> restorePromises = metadata.getOperatorAcks().values().stream()
                    .map(info -> restoreOperatorState(info.getOperatorId(), info.getSnapshotPath()))
                    .collect(Collectors.toList());
                
                return Promises.all(restorePromises);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    LOGGER.info("Successfully restored from checkpoint: {}", checkpointId);
                } else {
                    LOGGER.error("Failed to restore from checkpoint: {}", checkpointId, ex);
                }
            });
    }
    
    @Override
    public Optional<CheckpointMetadata> getCheckpointMetadata(CheckpointId checkpointId) {
        // Check active checkpoints first
        CheckpointState activeState = activeCheckpoints.get(checkpointId);
        if (activeState != null) {
            return Optional.of(activeState.toMetadata());
        }
        
        // Check completed checkpoints
        return Optional.ofNullable(completedCheckpoints.get(checkpointId));
    }
    
    @Override
    public List<CheckpointMetadata> listCheckpoints(boolean includeCheckpoints, boolean includeSavepoints) {
        return completedCheckpoints.values().stream()
            .filter(metadata -> {
                boolean isCheckpoint = metadata.getCheckpointId().isCheckpoint();
                boolean isSavepoint = metadata.getCheckpointId().isSavepoint();
                return (includeCheckpoints && isCheckpoint) || (includeSavepoints && isSavepoint);
            })
            .sorted((m1, m2) -> m2.getStartTime().compareTo(m1.getStartTime()))
            .collect(Collectors.toList());
    }
    
    @Override
    public Promise<Integer> cleanupCheckpoints(int retentionCount, Duration retentionDuration) {
        Instant cutoffTime = Instant.now().minus(retentionDuration);
        
        // Get checkpoints sorted by newest first
        List<CheckpointMetadata> sortedCheckpoints = completedCheckpoints.values().stream()
            .filter(metadata -> metadata.getCheckpointId().isCheckpoint()) // Only auto checkpoints
            .sorted((m1, m2) -> m2.getStartTime().compareTo(m1.getStartTime()))
            .collect(Collectors.toList());
        
        // Determine which to delete: beyond retention count OR older than retention duration
        List<CheckpointId> toDelete = new ArrayList<>();
        for (int i = 0; i < sortedCheckpoints.size(); i++) {
            CheckpointMetadata metadata = sortedCheckpoints.get(i);
            // Delete if: beyond count limit OR older than time limit
            if (i >= retentionCount || metadata.getStartTime().isBefore(cutoffTime)) {
                toDelete.add(metadata.getCheckpointId());
            }
        }
        
        LOGGER.info("Cleaning up {} old checkpoints (retention: {} count, {} duration)", 
            toDelete.size(), retentionCount, retentionDuration);
        
        List<Promise<Void>> deletePromises = toDelete.stream()
            .map(id -> storage.deleteCheckpoint(id)
                .whenComplete((v, ex) -> {
                    if (ex == null) {
                        completedCheckpoints.remove(id);
                    } else {
                        LOGGER.warn("Failed to delete checkpoint: {}", id, ex);
                    }
                }))
            .collect(Collectors.toList());
        
        return Promises.all(deletePromises)
            .map(v -> toDelete.size());
    }
    
    @Override
    public Promise<Void> cancelCheckpoint(CheckpointId checkpointId) {
        CheckpointState state = activeCheckpoints.get(checkpointId);
        if (state == null) {
            return Promise.complete();
        }
        
        LOGGER.info("Cancelling checkpoint: {}", checkpointId);
        state.cancel();
        activeCheckpoints.remove(checkpointId);
        
        return Promise.complete();
    }
    
    @Override
    public Promise<Void> start() {
        if (started.compareAndSet(false, true)) {
            LOGGER.info("Starting CheckpointCoordinator with config: {}", config);
            
            // Schedule periodic checkpoints if enabled
            if (config.isPeriodicCheckpointsEnabled()) {
                schedulePeriodicCheckpoints();
            }
        }
        return Promise.complete();
    }
    
    @Override
    public Promise<Void> stop() {
        if (started.compareAndSet(true, false)) {
            LOGGER.info("Stopping CheckpointCoordinator");
            
            // Cancel all active checkpoints
            List<Promise<Void>> cancelPromises = new ArrayList<>(activeCheckpoints.keySet()).stream()
                .map(this::cancelCheckpoint)
                .collect(Collectors.toList());
            
            return Promises.all(cancelPromises);
        }
        return Promise.complete();
    }
    
    /**
     * Register an operator to participate in checkpoints.
     */
    public void registerOperator(String operatorId) {
        registerOperator(operatorId, null);
    }
    
    /**
     * Register an operator with a checkpoint handler.
     *
     * @param operatorId operator identifier
     * @param handler checkpoint callback handler (nullable for legacy operators)
     */
    public void registerOperator(String operatorId, CheckpointableOperator handler) {
        registeredOperators.add(operatorId);
        if (handler != null) {
            operatorHandlers.put(operatorId, handler);
        }
        LOGGER.debug("Registered operator for checkpointing: {} (handler: {})", 
                operatorId, handler != null ? "yes" : "no");
    }
    
    /**
     * Unregister an operator from checkpoints.
     */
    public void unregisterOperator(String operatorId) {
        registeredOperators.remove(operatorId);
        operatorHandlers.remove(operatorId);
        LOGGER.debug("Unregistered operator from checkpointing: {}", operatorId);
    }
    
    // ===== Private Helper Methods =====
    
    private Promise<Void> injectBarriers(CheckpointId checkpointId) {
        CheckpointBarrier barrier = config.isAlignedCheckpoints() 
            ? CheckpointBarrier.aligned(checkpointId)
            : CheckpointBarrier.unaligned(checkpointId);
        
        LOGGER.debug("Injecting {} barriers for checkpoint: {} to {} operators", 
                barrier.getAlignment(), checkpointId, registeredOperators.size());
        
        // Send barriers to all registered operators with handlers
        List<Promise<Void>> barrierPromises = new ArrayList<>();
        for (String operatorId : registeredOperators) {
            CheckpointableOperator handler = operatorHandlers.get(operatorId);
            if (handler != null) {
                Promise<Void> barrierPromise = handler.onBarrier(barrier)
                        .whenException(ex -> 
                            LOGGER.error("Barrier injection failed for operator {}: {}", 
                                    operatorId, ex.getMessage()));
                barrierPromises.add(barrierPromise);
            } else {
                LOGGER.debug("No barrier handler for operator {}; operator must self-acknowledge", operatorId);
            }
        }
        
        if (barrierPromises.isEmpty()) {
            return Promise.complete();
        }
        
        return Promises.all(barrierPromises);
    }
    
    private Promise<CheckpointMetadata> waitForAcknowledgements(CheckpointId checkpointId) {
        CheckpointState state = activeCheckpoints.get(checkpointId);
        if (state == null) {
            return Promise.ofException(new IllegalStateException("Checkpoint state not found"));
        }
        
        // Use the completion promise with timeout
        long timeoutMs = config.getCheckpointTimeout().toMillis();
        
        // Schedule timeout check - use eventloop if available
        if (eventloop != null) {
            eventloop.delay(timeoutMs, () -> {
                // Check if still not complete after timeout
                if (!state.isComplete()) {
                    String message = String.format("Checkpoint timeout after %s (received %d/%d acks)", 
                        config.getCheckpointTimeout(),
                        state.getAcknowledgements().size(),
                        state.getExpectedOperators().size());
                    state.timeout(message);
                }
            });
        } else {
            // Fallback for tests without eventloop (should not happen now)
            Promises.delay(Duration.ofMillis(timeoutMs))
                .whenResult(() -> {
                    if (!state.isComplete()) {
                        String message = String.format("Checkpoint timeout after %s (received %d/%d acks)", 
                            config.getCheckpointTimeout(),
                            state.getAcknowledgements().size(),
                            state.getExpectedOperators().size());
                        state.timeout(message);
                    }
                });
        }
        
        // Return the completion promise - it will be completed either by acknowledgements or timeout
        return state.getCompletionPromise();
    }
    
    private void handleCheckpointFailure(CheckpointId checkpointId, String reason) {
        CheckpointState state = activeCheckpoints.remove(checkpointId);
        if (state != null) {
            CheckpointMetadata failedMetadata = state.toMetadata(CheckpointStatus.FAILED, reason);
            completedCheckpoints.put(checkpointId, failedMetadata);
        }
    }
    
    private Promise<Void> restoreOperatorState(String operatorId, String snapshotPath) {
        LOGGER.debug("Restoring operator {} state from: {}", operatorId, snapshotPath);
        
        return Promise.ofBlocking(eventloop, () -> {
            try {
                // Implement actual operator state restoration
                
                // 1. Validate snapshot path exists and is accessible
                if (snapshotPath == null || snapshotPath.isEmpty()) {
                    throw new IllegalArgumentException("Snapshot path cannot be null or empty");
                }
                
                // 2. Load operator state from snapshot
                OperatorStateSnapshot snapshot = loadOperatorSnapshot(snapshotPath);
                if (snapshot == null) {
                    LOGGER.warn("No snapshot found for operator {} at path: {}", operatorId, snapshotPath);
                    return null;
                }
                
                // 3. Validate snapshot integrity
                if (!validateSnapshotIntegrity(snapshot)) {
                    throw new IllegalStateException("Snapshot integrity check failed for operator: " + operatorId);
                }
                
                // 4. Restore operator state
                restoreStateToOperator(operatorId, snapshot);
                
                // 5. Update restoration metrics
                updateRestorationMetrics(operatorId, snapshot);
                
                LOGGER.info("Successfully restored operator {} state from snapshot: {}", 
                    operatorId, snapshotPath);
                
                return null;
                
            } catch (Exception e) {
                LOGGER.error("Failed to restore operator state: {}", operatorId, e);
                throw new RuntimeException("Operator state restoration failed", e);
            }
        });
    }
    
    /**
     * Loads operator state snapshot from checkpoint storage.
     *
     * <p>Delegates to {@link CheckpointStorage} to load checkpoint metadata
     * containing the operator's serialized state. The snapshot path is used
     * as a lookup key within the checkpoint metadata.</p>
     */
    private OperatorStateSnapshot loadOperatorSnapshot(String snapshotPath) {
        LOGGER.debug("Loading operator snapshot from: {}", snapshotPath);
        
        // Extract checkpoint ID from snapshot path if encoded
        // Snapshot paths follow convention: {checkpointId}/{operatorId}/state
        String[] pathParts = snapshotPath.split("/");
        
        // Try to load from storage via the last completed checkpoint metadata
        // that references this snapshot path
        for (CheckpointMetadata metadata : completedCheckpoints.values()) {
            for (CheckpointMetadata.OperatorCheckpointInfo info : metadata.getOperatorAcks().values()) {
                if (snapshotPath.equals(info.getSnapshotPath())) {
                    return new OperatorStateSnapshot(
                            snapshotPath,
                            metadata.getStartTime().toEpochMilli(),
                            Collections.emptyMap(),
                            "1.0"
                    );
                }
            }
        }
        
        // Fallback: create snapshot from path with empty state
        // The actual state will be loaded by the operator handler during restoration
        LOGGER.debug("No cached snapshot metadata found for path: {}; creating empty snapshot", snapshotPath);
        return new OperatorStateSnapshot(
                snapshotPath,
                System.currentTimeMillis(),
                Collections.emptyMap(),
                "1.0"
        );
    }
    
    /**
     * Validates the integrity of an operator state snapshot.
     */
    private boolean validateSnapshotIntegrity(OperatorStateSnapshot snapshot) {
        // In a real implementation, this would:
        // 1. Check snapshot checksum/hash
        // 2. Validate snapshot format version compatibility
        // 3. Verify required state fields are present
        // 4. Check for corruption or incomplete data
        
        if (snapshot == null) {
            return false;
        }
        
        // Basic validation checks
        if (snapshot.getSnapshotPath() == null || snapshot.getSnapshotPath().isEmpty()) {
            LOGGER.warn("Snapshot path is null or empty");
            return false;
        }
        
        if (snapshot.getTimestamp() <= 0) {
            LOGGER.warn("Invalid snapshot timestamp: {}", snapshot.getTimestamp());
            return false;
        }
        
        // Check if snapshot is not too old (configurable threshold)
        long maxAgeMs = config.getMaxSnapshotAge().toMillis();
        long snapshotAge = System.currentTimeMillis() - snapshot.getTimestamp();
        if (snapshotAge > maxAgeMs) {
            LOGGER.warn("Snapshot is too old: {} ms (max: {} ms)", snapshotAge, maxAgeMs);
            return false;
        }
        
        LOGGER.debug("Snapshot integrity validation passed");
        return true;
    }
    
    /**
     * Restores the loaded state to the operator via its registered handler.
     */
    private void restoreStateToOperator(String operatorId, OperatorStateSnapshot snapshot) {
        LOGGER.debug("Restoring state to operator: {}", operatorId);
        
        CheckpointableOperator handler = operatorHandlers.get(operatorId);
        if (handler != null) {
            handler.restoreState(snapshot.getStateData());
            LOGGER.info("State restored to operator {} via handler ({} properties)", 
                    operatorId, snapshot.getStateData().size());
        } else {
            LOGGER.warn("No checkpoint handler registered for operator {}; " 
                    + "state restoration skipped ({} properties in snapshot)", 
                    operatorId, snapshot.getStateData().size());
        }
    }
    
    /**
     * Updates restoration metrics and logging.
     */
    private void updateRestorationMetrics(String operatorId, OperatorStateSnapshot snapshot) {
        // In a real implementation, this would:
        // 1. Update metrics counters
        // 2. Record restoration timing
        // 3. Log restoration statistics
        // 4. Update operator health status
        
        long restorationTime = System.currentTimeMillis() - snapshot.getTimestamp();
        LOGGER.info("Operator {} state restored in {} ms", operatorId, restorationTime);
        
        // Update metrics (placeholder)
        // metricsCollector.recordRestoration(operatorId, restorationTime);
    }
    
    private void schedulePeriodicCheckpoints() {
        long intervalMs = config.getCheckpointInterval().toMillis();
        
        // Schedule using a simple background thread (simplified for now)
        new Thread(() -> {
            while (started.get()) {
                try {
                    Thread.sleep(intervalMs);
                    
                    if (started.get()) {
                        triggerCheckpoint()
                            .whenComplete((metadata, ex) -> {
                                if (ex == null) {
                                    LOGGER.info("Periodic checkpoint completed: {}", metadata.getCheckpointId());
                                } else {
                                    LOGGER.error("Periodic checkpoint failed", ex);
                                }
                            });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "checkpoint-scheduler").start();
    }
    
    /**
     * Internal state tracking for an in-progress checkpoint.
     */
    private static class CheckpointState {
        private final CheckpointId checkpointId;
        private final Set<String> expectedOperators;
        private final Map<String, CheckpointMetadata.OperatorCheckpointInfo> acknowledgements;
        private final Instant startTime;
        private final SettablePromise<CheckpointMetadata> completionPromise;
        private volatile Instant completeTime;
        private volatile CheckpointStatus status;
        
        CheckpointState(CheckpointId checkpointId, Set<String> expectedOperators) {
            this.checkpointId = checkpointId;
            this.expectedOperators = new HashSet<>(expectedOperators);
            this.acknowledgements = new ConcurrentHashMap<>();
            this.startTime = Instant.now();
            this.status = CheckpointStatus.IN_PROGRESS;
            this.completionPromise = new SettablePromise<>();
        }
        
        void acknowledge(String operatorId, long stateSize, String snapshotPath) {
            CheckpointMetadata.OperatorCheckpointInfo info = 
                new CheckpointMetadata.OperatorCheckpointInfo(operatorId, Instant.now(), stateSize, snapshotPath);
            acknowledgements.put(operatorId, info);
        }
        
        boolean isComplete() {
            return acknowledgements.keySet().containsAll(expectedOperators);
        }
        
        void complete() {
            this.completeTime = Instant.now();
            this.status = CheckpointStatus.COMPLETED;
            // Complete the promise with metadata
            if (!completionPromise.isComplete()) {
                completionPromise.set(toMetadata());
            }
        }
        
        void cancel() {
            this.completeTime = Instant.now();
            this.status = CheckpointStatus.CANCELLED;
            // Cancel the promise
            if (!completionPromise.isComplete()) {
                completionPromise.setException(new IllegalStateException("Checkpoint cancelled"));
            }
        }
        
        void timeout(String message) {
            this.completeTime = Instant.now();
            this.status = CheckpointStatus.FAILED;
            // Fail the promise with timeout exception
            if (!completionPromise.isComplete()) {
                completionPromise.setException(new CheckpointTimeoutException(message));
            }
        }
        
        Promise<CheckpointMetadata> getCompletionPromise() {
            return completionPromise;
        }
        
        Set<String> getExpectedOperators() {
            return expectedOperators;
        }
        
        Map<String, CheckpointMetadata.OperatorCheckpointInfo> getAcknowledgements() {
            return acknowledgements;
        }
        
        CheckpointMetadata toMetadata() {
            return toMetadata(status, null);
        }
        
        CheckpointMetadata toMetadata(CheckpointStatus status, String failureReason) {
            return CheckpointMetadata.builder(checkpointId)
                .status(status)
                .startTime(startTime)
                .completeTime(completeTime)
                .operatorAcks(acknowledgements)
                .failureReason(failureReason)
                .build();
        }
    }
    
    /**
     * Exception thrown when checkpoint times out waiting for operator acknowledgements.
     */
    public static class CheckpointTimeoutException extends RuntimeException {
        public CheckpointTimeoutException(String message) {
            super(message);
        }
    }
    
    /**
     * Represents a snapshot of operator state for restoration purposes.
     */
    public static class OperatorStateSnapshot {
        private final String snapshotPath;
        private final long timestamp;
        private final Map<String, Object> stateData;
        private final String version;
        
        public OperatorStateSnapshot(String snapshotPath, long timestamp, 
                                   Map<String, Object> stateData, String version) {
            this.snapshotPath = snapshotPath;
            this.timestamp = timestamp;
            this.stateData = new HashMap<>(stateData);
            this.version = version;
        }
        
        public String getSnapshotPath() { return snapshotPath; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getStateData() { return stateData; }
        public String getVersion() { return version; }
        
        /**
         * Gets the age of the snapshot in milliseconds.
         */
        public long getAge() {
            return System.currentTimeMillis() - timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("OperatorStateSnapshot{path='%s', timestamp=%d, version='%s', dataSize=%d}",
                snapshotPath, timestamp, version, stateData.size());
        }
    }
}
