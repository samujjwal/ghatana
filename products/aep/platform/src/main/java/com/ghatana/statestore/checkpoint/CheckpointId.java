package com.ghatana.statestore.checkpoint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a checkpoint or savepoint.
 * Immutable value object containing checkpoint ID, type, and creation timestamp.
 */
public final class CheckpointId {
    
    private final String id;
    private final CheckpointType type;
    private final Instant createdAt;
    
    private CheckpointId(String id, CheckpointType type, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Checkpoint ID cannot be null");
        this.type = Objects.requireNonNull(type, "Checkpoint type cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
    }
    
    /**
     * Create a new checkpoint ID.
     */
    public static CheckpointId checkpoint() {
        return new CheckpointId(
            UUID.randomUUID().toString(),
            CheckpointType.CHECKPOINT,
            Instant.now()
        );
    }
    
    /**
     * Create a new checkpoint ID with specific ID.
     */
    public static CheckpointId checkpoint(String id) {
        return new CheckpointId(id, CheckpointType.CHECKPOINT, Instant.now());
    }
    
    /**
     * Create a new savepoint ID with a name.
     */
    public static CheckpointId savepoint(String name) {
        return new CheckpointId(
            name,
            CheckpointType.SAVEPOINT,
            Instant.now()
        );
    }
    
    /**
     * Restore a checkpoint ID from stored values.
     */
    public static CheckpointId restore(String id, CheckpointType type, Instant createdAt) {
        return new CheckpointId(id, type, createdAt);
    }
    
    public String getId() {
        return id;
    }
    
    public CheckpointType getType() {
        return type;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public boolean isCheckpoint() {
        return type == CheckpointType.CHECKPOINT;
    }
    
    public boolean isSavepoint() {
        return type == CheckpointType.SAVEPOINT;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckpointId that = (CheckpointId) o;
        return Objects.equals(id, that.id) &&
               type == that.type;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }
    
    @Override
    public String toString() {
        return String.format("CheckpointId{id='%s', type=%s, createdAt=%s}", id, type, createdAt);
    }
}
