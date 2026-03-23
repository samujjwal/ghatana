package com.ghatana.statestore.checkpoint;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for checkpoint coordinator.
 * Controls checkpoint behavior: intervals, timeouts, alignment mode, retention.
 */
public final class CheckpointConfig {
    
    private final boolean periodicCheckpointsEnabled;
    private final Duration checkpointInterval;
    private final Duration checkpointTimeout;
    private final boolean alignedCheckpoints;
    private final int retentionCount;
    private final Duration retentionDuration;
    private final Duration maxSnapshotAge;
    
    private CheckpointConfig(Builder builder) {
        this.periodicCheckpointsEnabled = builder.periodicCheckpointsEnabled;
        this.checkpointInterval = builder.checkpointInterval;
        this.checkpointTimeout = builder.checkpointTimeout;
        this.alignedCheckpoints = builder.alignedCheckpoints;
        this.retentionCount = builder.retentionCount;
        this.retentionDuration = builder.retentionDuration;
        this.maxSnapshotAge = builder.maxSnapshotAge;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Default configuration for testing.
     */
    public static CheckpointConfig defaults() {
        return builder().build();
    }
    
    public boolean isPeriodicCheckpointsEnabled() {
        return periodicCheckpointsEnabled;
    }
    
    public Duration getCheckpointInterval() {
        return checkpointInterval;
    }
    
    public Duration getCheckpointTimeout() {
        return checkpointTimeout;
    }
    
    public boolean isAlignedCheckpoints() {
        return alignedCheckpoints;
    }
    
    public int getRetentionCount() {
        return retentionCount;
    }
    
    public Duration getRetentionDuration() {
        return retentionDuration;
    }

    public Duration getMaxSnapshotAge() {
        return maxSnapshotAge;
    }

    @Override
    public String toString() {
        return String.format("CheckpointConfig{periodic=%s, interval=%s, timeout=%s, aligned=%s, retention=%d/%s}",
            periodicCheckpointsEnabled, checkpointInterval, checkpointTimeout, 
            alignedCheckpoints, retentionCount, retentionDuration);
    }
    
    public static class Builder {
        private boolean periodicCheckpointsEnabled = true;
        private Duration checkpointInterval = Duration.ofMinutes(5);
        private Duration checkpointTimeout = Duration.ofSeconds(30);
        private boolean alignedCheckpoints = true;
        private int retentionCount = 5;
        private Duration retentionDuration = Duration.ofHours(24);
        private Duration maxSnapshotAge = Duration.ofHours(48);
        
        public Builder periodicCheckpointsEnabled(boolean enabled) {
            this.periodicCheckpointsEnabled = enabled;
            return this;
        }
        
        public Builder checkpointInterval(Duration interval) {
            this.checkpointInterval = Objects.requireNonNull(interval, "Checkpoint interval cannot be null");
            return this;
        }
        
        public Builder checkpointTimeout(Duration timeout) {
            this.checkpointTimeout = Objects.requireNonNull(timeout, "Checkpoint timeout cannot be null");
            return this;
        }
        
        public Builder alignedCheckpoints(boolean aligned) {
            this.alignedCheckpoints = aligned;
            return this;
        }
        
        public Builder retentionCount(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("Retention count must be >= 1");
            }
            this.retentionCount = count;
            return this;
        }
        
        public Builder retentionDuration(Duration duration) {
            this.retentionDuration = Objects.requireNonNull(duration, "Retention duration cannot be null");
            return this;
        }

        public Builder maxSnapshotAge(Duration maxAge) {
            this.maxSnapshotAge = Objects.requireNonNull(maxAge, "Max snapshot age cannot be null");
            return this;
        }
        
        public CheckpointConfig build() {
            return new CheckpointConfig(this);
        }
    }
}
