package com.ghatana.datacloud.client;

import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Store and retrieval interface for learning signals.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a standardized interface for storing and querying learning signals
 * that feed ML training pipelines and analytics.
 *
 * <p>
 * <b>Implementation Strategy</b><br>
 * This is typically implemented as a wrapper around a {@link com.ghatana.datacloud.spi.StoragePlugin}
 * targeting the {@code learning-signals} collection:
 * <ul>
 * <li>Uses EVENT record type (immutable, append-only)</li>
 * <li>Auto-partitioned by date for efficient queries</li>
 * <li>Indexed on tenantId, signalType, timestamp, source.plugin</li>
 * <li>Retention policy applied (configurable, typically 90+ days)</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Store a signal
 * learningSignalStore.store(signal).getResult();
 *
 * // Query signals for training
 * List<LearningSignal> signals = learningSignalStore.query(
 *     LearningSignalQuery.builder()
 *         .tenantId("tenant-123")
 *         .signalType(LearningSignal.SignalType.QUERY)
 *         .startTime(sevenDaysAgo)
 *         .endTime(now)
 *         .limit(10000)
 *         .build()
 * ).getResult();
 *
 * // Export for offline training
 * learningSignalStore.export(
 *     query,
 *     ExportFormat.PARQUET,
 *     "s3://training-data/signals/"
 * ).getResult();
 * }</pre>
 *
 * @see LearningSignal
 * @see com.ghatana.datacloud.spi.StoragePlugin
 * @doc.type interface
 * @doc.purpose Learning signal storage and retrieval
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface LearningSignalStore {

    /**
     * Stores a single learning signal.
     *
     * @param signal Signal to store
     * @return Promise with stored signal (with ID assigned)
     */
    Promise<LearningSignal> store(LearningSignal signal);

    /**
     * Stores multiple learning signals in batch.
     *
     * @param signals Signals to store
     * @return Promise with batch result
     */
    Promise<BatchStoreResult> storeBatch(List<LearningSignal> signals);

    /**
     * Queries learning signals.
     *
     * @param query Query specification
     * @return Promise with matching signals
     */
    Promise<List<LearningSignal>> query(LearningSignalQuery query);

    /**
     * Counts learning signals matching query.
     *
     * @param query Query specification
     * @return Promise with count
     */
    Promise<Long> count(LearningSignalQuery query);

    /**
     * Gets aggregate statistics for signals.
     *
     * @param query Query specification
     * @param aggregation Aggregation specification
     * @return Promise with aggregated results
     */
    Promise<Map<String, Object>> aggregate(LearningSignalQuery query, AggregationSpec aggregation);

    /**
     * Exports signals to external storage for offline training.
     *
     * @param query Query to select signals
     * @param format Export format
     * @param destination Destination path (S3, GCS, local filesystem)
     * @return Promise with export result
     */
    Promise<ExportResult> export(LearningSignalQuery query, ExportFormat format, String destination);

    /**
     * Deletes signals older than retention period.
     *
     * @param tenantId Tenant ID
     * @param retentionPeriod Signals older than this are deleted
     * @return Promise with deletion count
     */
    Promise<Long> purgeOldSignals(String tenantId, Duration retentionPeriod);

    /**
     * Query specification for learning signals.
     */
    record LearningSignalQuery(
            String tenantId,
            LearningSignal.SignalType signalType,
            Instant startTime,
            Instant endTime,
            String sourcePlugin,
            String sourceCollection,
            Map<String, Object> featureFilters,
            Integer limit,
            String continuationToken
    ) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String tenantId;
            private LearningSignal.SignalType signalType;
            private Instant startTime;
            private Instant endTime;
            private String sourcePlugin;
            private String sourceCollection;
            private Map<String, Object> featureFilters;
            private Integer limit = 1000;
            private String continuationToken;

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder signalType(LearningSignal.SignalType signalType) {
                this.signalType = signalType;
                return this;
            }

            public Builder startTime(Instant startTime) {
                this.startTime = startTime;
                return this;
            }

            public Builder endTime(Instant endTime) {
                this.endTime = endTime;
                return this;
            }

            public Builder sourcePlugin(String sourcePlugin) {
                this.sourcePlugin = sourcePlugin;
                return this;
            }

            public Builder sourceCollection(String sourceCollection) {
                this.sourceCollection = sourceCollection;
                return this;
            }

            public Builder featureFilters(Map<String, Object> featureFilters) {
                this.featureFilters = featureFilters;
                return this;
            }

            public Builder limit(Integer limit) {
                this.limit = limit;
                return this;
            }

            public Builder continuationToken(String continuationToken) {
                this.continuationToken = continuationToken;
                return this;
            }

            public LearningSignalQuery build() {
                return new LearningSignalQuery(
                        tenantId,
                        signalType,
                        startTime,
                        endTime,
                        sourcePlugin,
                        sourceCollection,
                        featureFilters,
                        limit,
                        continuationToken
                );
            }
        }
    }

    /**
     * Aggregation specification for signal analytics.
     */
    record AggregationSpec(
            List<String> groupByFields,
            Map<String, AggregationType> metrics
    ) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private List<String> groupByFields;
            private Map<String, AggregationType> metrics;

            public Builder groupBy(List<String> fields) {
                this.groupByFields = fields;
                return this;
            }

            public Builder metric(String field, AggregationType type) {
                if (this.metrics == null) {
                    this.metrics = new java.util.HashMap<>();
                }
                this.metrics.put(field, type);
                return this;
            }

            public AggregationSpec build() {
                return new AggregationSpec(groupByFields, metrics);
            }
        }
    }

    /**
     * Aggregation types.
     */
    enum AggregationType {
        COUNT,
        SUM,
        AVG,
        MIN,
        MAX,
        STDDEV
    }

    /**
     * Export formats.
     */
    enum ExportFormat {
        /**
         * Parquet - columnar format, best for ML training.
         */
        PARQUET,

        /**
         * JSON - human-readable, good for inspection.
         */
        JSON,

        /**
         * CSV - simple, compatible with many tools.
         */
        CSV,

        /**
         * Avro - schema evolution support.
         */
        AVRO
    }

    /**
     * Result of batch store operation.
     */
    record BatchStoreResult(
            int totalCount,
            int successCount,
            int failureCount,
            List<String> errors
    ) {

        public boolean isFullySuccessful() {
            return failureCount == 0;
        }
    }

    /**
     * Result of export operation.
     */
    record ExportResult(
            String destination,
            long recordCount,
            long sizeBytes,
            ExportFormat format,
            Instant exportedAt
    ) {
    }
}

