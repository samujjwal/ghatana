package com.ghatana.statestore.checkpoint;

import java.time.Instant;

/**
 * Information about a checkpoint.
 * 
 * Day 27 Implementation: Metadata for checkpoint tracking and management.
 */
public class CheckpointInfo {
    
    private final String checkpointId;
    private final Instant creationTime;
    private final long sizeBytes;
    private final String description;
    private final CheckpointStatus status;
    
    public CheckpointInfo(String checkpointId, Instant creationTime, long sizeBytes, 
                         String description, CheckpointStatus status) {
        this.checkpointId = checkpointId;
        this.creationTime = creationTime;
        this.sizeBytes = sizeBytes;
        this.description = description;
        this.status = status;
    }
    
    public String getCheckpointId() {
        return checkpointId;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getDescription() {
        return description;
    }

    public CheckpointStatus getStatus() {
        return status;
    }
    
    @Override
    public String toString() {
        return "CheckpointInfo{" +
                "id='" + checkpointId + '\'' +
                ", created=" + creationTime +
                ", size=" + sizeBytes +
                ", status=" + status +
                '}';
    }
}