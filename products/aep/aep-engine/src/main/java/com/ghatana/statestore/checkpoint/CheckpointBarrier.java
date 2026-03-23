package com.ghatana.statestore.checkpoint;

import java.time.Instant;
import java.util.Objects;

/**
 * Checkpoint barrier injected into event streams to delimit checkpoint boundaries.
 * When an operator receives a barrier, it snapshots its state and acknowledges the checkpoint.
 */
public final class CheckpointBarrier {
    
    private final CheckpointId checkpointId;
    private final Instant timestamp;
    private final BarrierAlignment alignment;
    
    private CheckpointBarrier(CheckpointId checkpointId, Instant timestamp, BarrierAlignment alignment) {
        this.checkpointId = Objects.requireNonNull(checkpointId, "Checkpoint ID cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        this.alignment = Objects.requireNonNull(alignment, "Barrier alignment cannot be null");
    }
    
    /**
     * Create an aligned checkpoint barrier.
     * Operators must wait for barriers from all inputs before processing.
     */
    public static CheckpointBarrier aligned(CheckpointId checkpointId) {
        return new CheckpointBarrier(checkpointId, Instant.now(), BarrierAlignment.ALIGNED);
    }
    
    /**
     * Create an unaligned (async) checkpoint barrier.
     * Operators can proceed with barrier immediately without waiting.
     */
    public static CheckpointBarrier unaligned(CheckpointId checkpointId) {
        return new CheckpointBarrier(checkpointId, Instant.now(), BarrierAlignment.UNALIGNED);
    }
    
    public CheckpointId getCheckpointId() {
        return checkpointId;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public BarrierAlignment getAlignment() {
        return alignment;
    }
    
    public boolean isAligned() {
        return alignment == BarrierAlignment.ALIGNED;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckpointBarrier that = (CheckpointBarrier) o;
        return Objects.equals(checkpointId, that.checkpointId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(checkpointId);
    }
    
    @Override
    public String toString() {
        return String.format("CheckpointBarrier{checkpointId=%s, timestamp=%s, alignment=%s}", 
            checkpointId, timestamp, alignment);
    }
    
    /**
     * Barrier alignment mode for checkpoints.
     */
    public enum BarrierAlignment {
        /**
         * Aligned barriers - operator waits for barriers from all inputs.
         * Provides exactly-once processing semantics but may add latency.
         */
        ALIGNED,
        
        /**
         * Unaligned barriers - operator processes barrier immediately.
         * Provides at-least-once semantics with lower latency.
         */
        UNALIGNED
    }
}
