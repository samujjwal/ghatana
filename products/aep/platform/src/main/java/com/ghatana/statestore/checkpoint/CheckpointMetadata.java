package com.ghatana.statestore.checkpoint;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Metadata about a completed or in-progress checkpoint.
 * Contains checkpoint ID, status, operator acknowledgements, and timing information.
 */
public final class CheckpointMetadata {
    
    private final CheckpointId checkpointId;
    private final CheckpointStatus status;
    private final Instant startTime;
    private final Instant completeTime;
    private final Map<String, OperatorCheckpointInfo> operatorAcks;
    private final String failureReason;
    
    private CheckpointMetadata(Builder builder) {
        this.checkpointId = Objects.requireNonNull(builder.checkpointId, "Checkpoint ID cannot be null");
        this.status = Objects.requireNonNull(builder.status, "Status cannot be null");
        this.startTime = Objects.requireNonNull(builder.startTime, "Start time cannot be null");
        this.completeTime = builder.completeTime;
        this.operatorAcks = Collections.unmodifiableMap(builder.operatorAcks);
        this.failureReason = builder.failureReason;
    }
    
    public static Builder builder(CheckpointId checkpointId) {
        return new Builder(checkpointId);
    }
    
    public CheckpointId getCheckpointId() {
        return checkpointId;
    }
    
    public CheckpointStatus getStatus() {
        return status;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Instant getCompleteTime() {
        return completeTime;
    }
    
    public Duration getDuration() {
        if (completeTime == null) {
            return Duration.between(startTime, Instant.now());
        }
        return Duration.between(startTime, completeTime);
    }
    
    public Map<String, OperatorCheckpointInfo> getOperatorAcks() {
        return operatorAcks;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public boolean isComplete() {
        return status == CheckpointStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == CheckpointStatus.FAILED;
    }
    
    @Override
    public String toString() {
        return String.format("CheckpointMetadata{id=%s, status=%s, duration=%s, operators=%d}", 
            checkpointId, status, getDuration(), operatorAcks.size());
    }
    
    public static class Builder {
        private final CheckpointId checkpointId;
        private CheckpointStatus status = CheckpointStatus.IN_PROGRESS;
        private Instant startTime = Instant.now();
        private Instant completeTime;
        private Map<String, OperatorCheckpointInfo> operatorAcks = Collections.emptyMap();
        private String failureReason;
        
        private Builder(CheckpointId checkpointId) {
            this.checkpointId = checkpointId;
        }
        
        public Builder status(CheckpointStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder completeTime(Instant completeTime) {
            this.completeTime = completeTime;
            return this;
        }
        
        public Builder operatorAcks(Map<String, OperatorCheckpointInfo> operatorAcks) {
            this.operatorAcks = operatorAcks;
            return this;
        }
        
        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }
        
        public CheckpointMetadata build() {
            return new CheckpointMetadata(this);
        }
    }
    
    /**
     * Information about an operator's checkpoint acknowledgement.
     */
    public static class OperatorCheckpointInfo {
        private final String operatorId;
        private final Instant ackTime;
        private final long stateSize;
        private final String snapshotPath;
        
        public OperatorCheckpointInfo(String operatorId, Instant ackTime, long stateSize, String snapshotPath) {
            this.operatorId = operatorId;
            this.ackTime = ackTime;
            this.stateSize = stateSize;
            this.snapshotPath = snapshotPath;
        }
        
        public String getOperatorId() {
            return operatorId;
        }
        
        public Instant getAckTime() {
            return ackTime;
        }
        
        public long getStateSize() {
            return stateSize;
        }
        
        public String getSnapshotPath() {
            return snapshotPath;
        }
    }
}
