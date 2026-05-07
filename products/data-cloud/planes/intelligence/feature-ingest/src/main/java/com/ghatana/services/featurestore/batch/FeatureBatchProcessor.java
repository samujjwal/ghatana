/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.featurestore.batch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Batch processor for feature ingestion operations.
 * Accumulates features into batches and processes them efficiently when the batch reaches capacity or timeout.
 *
 * @doc.type class
 * @doc.purpose Batch processor for efficient feature ingestion
 * @doc.layer ingest
 * @doc.pattern Batching
 */
public final class FeatureBatchProcessor {

    private final int batchSize;
    private final long batchTimeoutMillis;
    private final Consumer<List<Map<String, Object>>> batchProcessor;
    private final List<Map<String, Object>> currentBatch;
    private final Object lock = new Object();
    private volatile long lastFlushTime;
    private volatile boolean closed;

    /**
     * Creates a new feature batch processor.
     *
     * @param batchSize maximum number of features per batch
     * @param batchTimeoutMillis maximum time to wait before flushing a batch (milliseconds)
     * @param batchProcessor consumer that processes completed batches
     */
    public FeatureBatchProcessor(int batchSize, long batchTimeoutMillis, 
                                  Consumer<List<Map<String, Object>>> batchProcessor) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        if (batchTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Batch timeout must be positive");
        }
        if (batchProcessor == null) {
            throw new IllegalArgumentException("Batch processor cannot be null");
        }

        this.batchSize = batchSize;
        this.batchTimeoutMillis = batchTimeoutMillis;
        this.batchProcessor = batchProcessor;
        this.currentBatch = new ArrayList<>(batchSize);
        this.lastFlushTime = System.currentTimeMillis();
        this.closed = false;
    }

    /**
     * Adds a feature to the batch processor.
     * If the batch reaches capacity, it will be flushed automatically.
     *
     * @param feature the feature to add
     * @throws IllegalStateException if the processor is closed
     */
    public void addFeature(Map<String, Object> feature) {
        if (closed) {
            throw new IllegalStateException("Batch processor is closed");
        }
        if (feature == null) {
            throw new IllegalArgumentException("Feature cannot be null");
        }

        synchronized (lock) {
            currentBatch.add(feature);

            if (currentBatch.size() >= batchSize) {
                flushBatch();
            }
        }
    }

    /**
     * Adds multiple features to the batch processor.
     *
     * @param features the features to add
     * @throws IllegalStateException if the processor is closed
     */
    public void addFeatures(List<Map<String, Object>> features) {
        if (closed) {
            throw new IllegalStateException("Batch processor is closed");
        }
        if (features == null) {
            throw new IllegalArgumentException("Features cannot be null");
        }

        synchronized (lock) {
            for (Map<String, Object> feature : features) {
                currentBatch.add(feature);
                if (currentBatch.size() >= batchSize) {
                    flushBatch();
                }
            }
        }
    }

    /**
     * Flushes the current batch if it contains any features.
     * This method is thread-safe.
     */
    public void flush() {
        synchronized (lock) {
            flushBatch();
        }
    }

    /**
     * Checks if a timeout flush is needed based on the configured timeout.
     *
     * @return true if a timeout flush is needed
     */
    public boolean needsTimeoutFlush() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastFlush = currentTime - lastFlushTime;
        return !currentBatch.isEmpty() && timeSinceLastFlush >= batchTimeoutMillis;
    }

    /**
     * Performs a timeout flush if needed.
     */
    public void flushIfTimeout() {
        if (needsTimeoutFlush()) {
            flush();
        }
    }

    /**
     * Closes the batch processor and flushes any remaining features.
     * After closing, no more features can be added.
     */
    public void close() {
        synchronized (lock) {
            if (!closed) {
                flushBatch();
                closed = true;
            }
        }
    }

    /**
     * Checks if the processor is closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns the current batch size.
     *
     * @return current batch size
     */
    public int getCurrentBatchSize() {
        synchronized (lock) {
            return currentBatch.size();
        }
    }

    /**
     * Returns the configured batch size.
     *
     * @return batch size
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Returns the configured batch timeout.
     *
     * @return batch timeout in milliseconds
     */
    public long getBatchTimeoutMillis() {
        return batchTimeoutMillis;
    }

    private void flushBatch() {
        if (currentBatch.isEmpty()) {
            return;
        }

        List<Map<String, Object>> batchToProcess = new ArrayList<>(currentBatch);
        currentBatch.clear();
        lastFlushTime = System.currentTimeMillis();

        try {
            batchProcessor.accept(batchToProcess);
        } catch (Exception e) {
            // Log error but don't fail the entire processor
            System.err.println("Error processing batch: " + e.getMessage());
        }
    }

    /**
     * Builder for creating FeatureBatchProcessor instances.
     */
    public static final class Builder {
        private int batchSize = 100;
        private long batchTimeoutMillis = 5000;
        private Consumer<List<Map<String, Object>>> batchProcessor;

        /**
         * Sets the batch size.
         *
         * @param batchSize maximum number of features per batch
         * @return this builder
         */
        public Builder withBatchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * Sets the batch timeout.
         *
         * @param batchTimeoutMillis maximum time to wait before flushing (milliseconds)
         * @return this builder
         */
        public Builder withBatchTimeoutMillis(long batchTimeoutMillis) {
            this.batchTimeoutMillis = batchTimeoutMillis;
            return this;
        }

        /**
         * Sets the batch processor.
         *
         * @param batchProcessor consumer that processes completed batches
         * @return this builder
         */
        public Builder withBatchProcessor(Consumer<List<Map<String, Object>>> batchProcessor) {
            this.batchProcessor = batchProcessor;
            return this;
        }

        /**
         * Builds the FeatureBatchProcessor.
         *
         * @return new FeatureBatchProcessor instance
         */
        public FeatureBatchProcessor build() {
            if (batchProcessor == null) {
                throw new IllegalStateException("Batch processor must be set");
            }
            return new FeatureBatchProcessor(batchSize, batchTimeoutMillis, batchProcessor);
        }
    }

    /**
     * Creates a feature with standard metadata.
     *
     * @param featureName name of the feature
     * @param value feature value
     * @param source source of the feature
     * @return feature map with metadata
     */
    public static Map<String, Object> createFeature(String featureName, Object value, String source) {
        Map<String, Object> feature = new HashMap<>();
        feature.put("feature_name", featureName);
        feature.put("value", value);
        feature.put("source", source);
        feature.put("ingestion_timestamp", Instant.now());
        return feature;
    }

    /**
     * Creates a batch processor with default settings.
     *
     * @param batchProcessor consumer that processes completed batches
     * @return new FeatureBatchProcessor with batch size 100 and timeout 5000ms
     */
    public static FeatureBatchProcessor createDefault(Consumer<List<Map<String, Object>>> batchProcessor) {
        return new Builder()
            .withBatchSize(100)
            .withBatchTimeoutMillis(5000)
            .withBatchProcessor(batchProcessor)
            .build();
    }
}
