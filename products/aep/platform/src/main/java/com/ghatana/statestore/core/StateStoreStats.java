package com.ghatana.statestore.core;

import java.time.Instant;
import java.util.Map;

/**
 * Statistics about a state store's current status and performance metrics.
 * 
 * Day 27 Implementation: Provides monitoring data for state store health and performance.
 */
public class StateStoreStats {
    
    private final long totalKeys;
    private final long totalSizeBytes;
    private final long keysWithTtl;
    private final long expiredKeys;
    private final long totalReads;
    private final long totalWrites;
    private final long totalDeletes;
    private final double avgReadLatencyMs;
    private final double avgWriteLatencyMs;
    private final int checkpointCount;
    private final Instant lastCheckpointTime;
    private final String backendType;
    private final long memoryUsageBytes;
    private final long diskUsageBytes;
    private final boolean healthy;
    private final long uptimeMs;
    private final Map<String, Object> additionalMetrics;
    
    public StateStoreStats(long totalKeys, long totalSizeBytes, long keysWithTtl, 
                          long expiredKeys, long totalReads, long totalWrites,
                          long totalDeletes, double avgReadLatencyMs, double avgWriteLatencyMs,
                          int checkpointCount, Instant lastCheckpointTime, String backendType,
                          long memoryUsageBytes, long diskUsageBytes, boolean healthy,
                          long uptimeMs, Map<String, Object> additionalMetrics) {
        this.totalKeys = totalKeys;
        this.totalSizeBytes = totalSizeBytes;
        this.keysWithTtl = keysWithTtl;
        this.expiredKeys = expiredKeys;
        this.totalReads = totalReads;
        this.totalWrites = totalWrites;
        this.totalDeletes = totalDeletes;
        this.avgReadLatencyMs = avgReadLatencyMs;
        this.avgWriteLatencyMs = avgWriteLatencyMs;
        this.checkpointCount = checkpointCount;
        this.lastCheckpointTime = lastCheckpointTime;
        this.backendType = backendType;
        this.memoryUsageBytes = memoryUsageBytes;
        this.diskUsageBytes = diskUsageBytes;
        this.healthy = healthy;
        this.uptimeMs = uptimeMs;
        this.additionalMetrics = additionalMetrics;
    }
    
    // Getters
    public long getTotalKeys() {
        return totalKeys;
    }

    public long getTotalSizeBytes() {
        return totalSizeBytes;
    }

    public long getKeysWithTtl() {
        return keysWithTtl;
    }

    public long getExpiredKeys() {
        return expiredKeys;
    }

    public long getTotalReads() {
        return totalReads;
    }

    public long getTotalWrites() {
        return totalWrites;
    }

    public long getTotalDeletes() {
        return totalDeletes;
    }

    public double getAvgReadLatencyMs() {
        return avgReadLatencyMs;
    }

    public double getAvgWriteLatencyMs() {
        return avgWriteLatencyMs;
    }

    public int getCheckpointCount() {
        return checkpointCount;
    }

    public Instant getLastCheckpointTime() {
        return lastCheckpointTime;
    }

    public String getBackendType() {
        return backendType;
    }

    public long getMemoryUsageBytes() {
        return memoryUsageBytes;
    }

    public long getDiskUsageBytes() {
        return diskUsageBytes;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public long getUptimeMs() {
        return uptimeMs;
    }

    public Map<String, Object> getAdditionalMetrics() {
        return additionalMetrics;
    }
    
    /**
     * Create basic stats for a healthy store.
     */
    public static StateStoreStats healthy(String backendType) {
        return new StateStoreStats(0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0, null,
                                  backendType, 0, 0, true, 0, Map.of());
    }
    
    /**
     * Create basic stats for an unhealthy store.
     */
    public static StateStoreStats unhealthy(String backendType, String reason) {
        return new StateStoreStats(-1, -1, -1, -1, -1, -1, -1, -1.0, -1.0, -1, null,
                                  backendType, -1, -1, false, -1, Map.of("error", reason));
    }
}