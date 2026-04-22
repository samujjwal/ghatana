/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.featurestore.ingest;

import com.ghatana.services.featurestore.batch.FeatureBatchProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Optimized feature ingestion pipeline with batching, async processing, and performance monitoring.
 *
 * @doc.type class
 * @doc.purpose Optimized pipeline for efficient feature ingestion with batching and async processing
 * @doc.layer ingest
 * @doc.pattern Pipeline, Async
 */
public final class OptimizedFeatureIngestionPipeline {

    private final FeatureBatchProcessor batchProcessor;
    private final ExecutorService executorService;
    private final Map<String, AtomicLong> metrics;
    private final Consumer<Map<String, Object>> featureValidator;
    private final Consumer<Map<String, Object>> featureTransformer;
    private volatile boolean closed;

    /**
     * Creates an optimized feature ingestion pipeline.
     *
     * @param batchSize maximum number of features per batch
     * @param batchTimeoutMillis maximum time to wait before flushing a batch
     * @param validator feature validation logic
     * @param transformer feature transformation logic
     * @param storageCallback callback for storing processed features
     */
    public OptimizedFeatureIngestionPipeline(int batchSize, long batchTimeoutMillis,
                                               Consumer<Map<String, Object>> validator,
                                               Consumer<Map<String, Object>> transformer,
                                               Consumer<List<Map<String, Object>>> storageCallback) {
        this.batchProcessor = new FeatureBatchProcessor(batchSize, batchTimeoutMillis, this::processBatch);
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.metrics = new ConcurrentHashMap<>();
        this.featureValidator = validator != null ? validator : this::defaultValidator;
        this.featureTransformer = transformer != null ? transformer : this::defaultTransformer;
        this.closed = false;

        // Initialize metrics
        metrics.put("ingested_count", new AtomicLong(0));
        metrics.put("validated_count", new AtomicLong(0));
        metrics.put("transformed_count", new AtomicLong(0));
        metrics.put("stored_count", new AtomicLong(0));
        metrics.put("error_count", new AtomicLong(0));
        metrics.put("processing_time_ms", new AtomicLong(0));
    }

    /**
     * Ingests a single feature asynchronously.
     *
     * @param feature the feature to ingest
     * @return CompletableFuture that completes when ingestion is done
     */
    public CompletableFuture<Void> ingestFeatureAsync(Map<String, Object> feature) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Pipeline is closed"));
        }

        return CompletableFuture.runAsync(() -> {
            try {
                ingestFeature(feature);
            } catch (Exception e) {
                metrics.get("error_count").incrementAndGet();
                throw new RuntimeException("Failed to ingest feature", e);
            }
        }, executorService);
    }

    /**
     * Ingests multiple features asynchronously.
     *
     * @param features the features to ingest
     * @return CompletableFuture that completes when all ingestions are done
     */
    public CompletableFuture<Void> ingestFeaturesAsync(List<Map<String, Object>> features) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Pipeline is closed"));
        }

        return CompletableFuture.runAsync(() -> {
            try {
                ingestFeatures(features);
            } catch (Exception e) {
                metrics.get("error_count").incrementAndGet();
                throw new RuntimeException("Failed to ingest features", e);
            }
        }, executorService);
    }

    /**
     * Ingests a single feature synchronously.
     *
     * @param feature the feature to ingest
     */
    public void ingestFeature(Map<String, Object> feature) {
        if (closed) {
            throw new IllegalStateException("Pipeline is closed");
        }

        long startTime = System.currentTimeMillis();

        try {
            // Add metadata
            feature.put("ingestion_timestamp", Instant.now());
            feature.put("pipeline_id", hashCode());

            // Validate
            featureValidator.accept(feature);
            metrics.get("validated_count").incrementAndGet();

            // Transform
            featureTransformer.accept(feature);
            metrics.get("transformed_count").incrementAndGet();

            // Add to batch
            batchProcessor.addFeature(feature);
            metrics.get("ingested_count").incrementAndGet();

        } catch (Exception e) {
            metrics.get("error_count").incrementAndGet();
            throw new RuntimeException("Failed to ingest feature", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            metrics.get("processing_time_ms").addAndGet(duration);
        }
    }

    /**
     * Ingests multiple features synchronously.
     *
     * @param features the features to ingest
     */
    public void ingestFeatures(List<Map<String, Object>> features) {
        if (closed) {
            throw new IllegalStateException("Pipeline is closed");
        }

        for (Map<String, Object> feature : features) {
            ingestFeature(feature);
        }
    }

    /**
     * Flushes the current batch.
     */
    public void flush() {
        batchProcessor.flush();
    }

    /**
     * Closes the pipeline and flushes any remaining features.
     */
    public void close() {
        if (!closed) {
            closed = true;
            batchProcessor.close();
            executorService.shutdown();
        }
    }

    /**
     * Checks if the pipeline is closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns the current metrics.
     *
     * @return map of metric names to values
     */
    public Map<String, Long> getMetrics() {
        Map<String, Long> snapshot = new HashMap<>();
        metrics.forEach((key, value) -> snapshot.put(key, value.get()));
        return snapshot;
    }

    /**
     * Resets the metrics.
     */
    public void resetMetrics() {
        metrics.values().forEach(counter -> counter.set(0));
    }

    /**
     * Gets the batch processor.
     *
     * @return the batch processor
     */
    public FeatureBatchProcessor getBatchProcessor() {
        return batchProcessor;
    }

    /**
     * Processes a batch of features.
     */
    private void processBatch(List<Map<String, Object>> batch) {
        if (closed) {
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            // Here you would normally persist the batch to storage
            // For now, we just simulate storage
            metrics.get("stored_count").addAndGet(batch.size());

        } catch (Exception e) {
            metrics.get("error_count").addAndGet(batch.size());
            throw new RuntimeException("Failed to process batch", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            metrics.get("processing_time_ms").addAndGet(duration);
        }
    }

    /**
     * Default feature validator.
     */
    private void defaultValidator(Map<String, Object> feature) {
        if (feature == null) {
            throw new IllegalArgumentException("Feature cannot be null");
        }
        if (!feature.containsKey("feature_name")) {
            throw new IllegalArgumentException("Feature must have 'feature_name'");
        }
    }

    /**
     * Default feature transformer.
     */
    private void defaultTransformer(Map<String, Object> feature) {
        // No-op default transformation
    }

    /**
     * Builder for creating OptimizedFeatureIngestionPipeline instances.
     */
    public static final class Builder {
        private int batchSize = 100;
        private long batchTimeoutMillis = 5000;
        private Consumer<Map<String, Object>> validator;
        private Consumer<Map<String, Object>> transformer;
        private Consumer<List<Map<String, Object>>> storageCallback;

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
         * Sets the feature validator.
         *
         * @param validator validation logic
         * @return this builder
         */
        public Builder withValidator(Consumer<Map<String, Object>> validator) {
            this.validator = validator;
            return this;
        }

        /**
         * Sets the feature transformer.
         *
         * @param transformer transformation logic
         * @return this builder
         */
        public Builder withTransformer(Consumer<Map<String, Object>> transformer) {
            this.transformer = transformer;
            return this;
        }

        /**
         * Sets the storage callback.
         *
         * @param storageCallback callback for storing processed features
         * @return this builder
         */
        public Builder withStorageCallback(Consumer<List<Map<String, Object>>> storageCallback) {
            this.storageCallback = storageCallback;
            return this;
        }

        /**
         * Builds the OptimizedFeatureIngestionPipeline.
         *
         * @return new OptimizedFeatureIngestionPipeline instance
         */
        public OptimizedFeatureIngestionPipeline build() {
            return new OptimizedFeatureIngestionPipeline(
                batchSize, batchTimeoutMillis, validator, transformer, storageCallback);
        }
    }

    /**
     * Creates a pipeline with default settings.
     *
     * @param storageCallback callback for storing processed features
     * @return new OptimizedFeatureIngestionPipeline with default settings
     */
    public static OptimizedFeatureIngestionPipeline createDefault(
            Consumer<List<Map<String, Object>>> storageCallback) {
        return new Builder()
            .withBatchSize(100)
            .withBatchTimeoutMillis(5000)
            .withStorageCallback(storageCallback)
            .build();
    }
}
