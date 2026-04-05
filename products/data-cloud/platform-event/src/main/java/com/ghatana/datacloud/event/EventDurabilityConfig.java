/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.event;

import java.time.Duration;

/**
 * Configuration for event durability settings.
 *
 * @doc.type class
 * @doc.purpose Configuration for event durability settings
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class EventDurabilityConfig {

    /** Default durability level */
    private EventDurabilityService.DurabilityLevel defaultDurabilityLevel = 
        EventDurabilityService.DurabilityLevel.MAJORITY_ACK;

    /** Timeout for durability acknowledgment */
    private Duration durabilityTimeout = Duration.ofSeconds(30);

    /** Number of replicas required for durability */
    private int requiredReplicaCount = 2;

    /** Enable fsync for durability */
    private boolean fsyncEnabled = true;

    /** Checkpoint interval in milliseconds */
    private long checkpointIntervalMs = 5000;

    /** Maximum checkpoint lag before forcing checkpoint */
    private long maxCheckpointLagMs = 30000;

    /** Enable CDC capture */
    private boolean cdcEnabled = true;

    /** CDC buffer size */
    private int cdcBufferSize = 1000;

    /** Replay batch size */
    private int replayBatchSize = 100;

    /** Maximum replay events per request */
    private long maxReplayEvents = 10000;

    // Getters and setters

    public EventDurabilityService.DurabilityLevel getDefaultDurabilityLevel() {
        return defaultDurabilityLevel;
    }

    public void setDefaultDurabilityLevel(EventDurabilityService.DurabilityLevel level) {
        this.defaultDurabilityLevel = level;
    }

    public Duration getDurabilityTimeout() {
        return durabilityTimeout;
    }

    public void setDurabilityTimeout(Duration timeout) {
        this.durabilityTimeout = timeout;
    }

    public int getRequiredReplicaCount() {
        return requiredReplicaCount;
    }

    public void setRequiredReplicaCount(int count) {
        this.requiredReplicaCount = count;
    }

    public boolean isFsyncEnabled() {
        return fsyncEnabled;
    }

    public void setFsyncEnabled(boolean enabled) {
        this.fsyncEnabled = enabled;
    }

    public long getCheckpointIntervalMs() {
        return checkpointIntervalMs;
    }

    public void setCheckpointIntervalMs(long interval) {
        this.checkpointIntervalMs = interval;
    }

    public long getMaxCheckpointLagMs() {
        return maxCheckpointLagMs;
    }

    public void setMaxCheckpointLagMs(long lag) {
        this.maxCheckpointLagMs = lag;
    }

    public boolean isCdcEnabled() {
        return cdcEnabled;
    }

    public void setCdcEnabled(boolean enabled) {
        this.cdcEnabled = enabled;
    }

    public int getCdcBufferSize() {
        return cdcBufferSize;
    }

    public void setCdcBufferSize(int size) {
        this.cdcBufferSize = size;
    }

    public int getReplayBatchSize() {
        return replayBatchSize;
    }

    public void setReplayBatchSize(int size) {
        this.replayBatchSize = size;
    }

    public long getMaxReplayEvents() {
        return maxReplayEvents;
    }

    public void setMaxReplayEvents(long max) {
        this.maxReplayEvents = max;
    }
}
