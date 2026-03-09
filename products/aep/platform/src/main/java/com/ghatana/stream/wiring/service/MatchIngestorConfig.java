package com.ghatana.stream.wiring.service;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the MatchIngestor service.
 */
public class MatchIngestorConfig {
    private final int maxBatchSize;
    private final Duration batchTimeout;
    private final Duration processedMatchRetentionTime;
    private final int maxRetries;
    private final Duration retryDelay;

    public MatchIngestorConfig(int maxBatchSize, Duration batchTimeout, 
                              Duration processedMatchRetentionTime, 
                              int maxRetries, Duration retryDelay) {
        this.maxBatchSize = maxBatchSize;
        this.batchTimeout = Objects.requireNonNull(batchTimeout, "batchTimeout cannot be null");
        this.processedMatchRetentionTime = Objects.requireNonNull(processedMatchRetentionTime, 
                                                                   "processedMatchRetentionTime cannot be null");
        this.maxRetries = maxRetries;
        this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay cannot be null");
    }

    public static MatchIngestorConfig defaults() {
        return new MatchIngestorConfig(
            100,                          // maxBatchSize
            Duration.ofSeconds(5),        // batchTimeout
            Duration.ofHours(24),         // processedMatchRetentionTime
            3,                            // maxRetries
            Duration.ofSeconds(1)         // retryDelay
        );
    }

    public static MatchIngestorConfig highThroughput() {
        return new MatchIngestorConfig(
            500,                          // maxBatchSize
            Duration.ofSeconds(2),        // batchTimeout
            Duration.ofHours(12),         // processedMatchRetentionTime
            5,                            // maxRetries
            Duration.ofMillis(500)        // retryDelay
        );
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public Duration getBatchTimeout() {
        return batchTimeout;
    }

    public Duration getProcessedMatchRetentionTime() {
        return processedMatchRetentionTime;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    @Override
    public String toString() {
        return "MatchIngestorConfig{" +
               "maxBatchSize=" + maxBatchSize +
               ", batchTimeout=" + batchTimeout +
               ", processedMatchRetentionTime=" + processedMatchRetentionTime +
               ", maxRetries=" + maxRetries +
               ", retryDelay=" + retryDelay +
               '}';
    }
}