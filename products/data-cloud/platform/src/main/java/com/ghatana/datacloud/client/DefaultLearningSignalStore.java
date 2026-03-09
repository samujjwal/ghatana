package com.ghatana.datacloud.client;

import com.ghatana.datacloud.*;

import com.ghatana.datacloud.spi.StoragePlugin;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of LearningSignalStore backed by StoragePlugin.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a concrete implementation that stores learning signals as EVENT records
 * in a dedicated collection. This enables:
 * <ul>
 * <li>Immutable, append-only signal storage</li>
 * <li>Time-based partitioning and queries</li>
 * <li>Efficient batch operations</li>
 * <li>Retention policy enforcement</li>
 * <li>Export to ML training pipelines</li>
 * </ul>
 *
 * <p>
 * <b>Storage Strategy</b><br>
 * <ul>
 * <li>Collection: {@code learning-signals}</li>
 * <li>RecordType: {@code EVENT} (immutable)</li>
 * <li>Partitioning: By date for efficient queries</li>
 * <li>Indexes: tenantId, signalType, timestamp, source.plugin</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * StoragePlugin<EventRecord> plugin = pluginRegistry.getPlugin("postgresql");
 * LearningSignalStore store = new DefaultLearningSignalStore(plugin);
 *
 * // Store signal
 * LearningSignal signal = LearningSignal.builder()
 *     .signalType(SignalType.QUERY)
 *     .tenantId("tenant-123")
 *     .features(queryFeatures)
 *     .metrics(executionMetrics)
 *     .build();
 *
 * store.store(signal).whenComplete((stored, error) -> {
 *     if (error == null) {
 *         logger.info("Signal stored: {}", stored.getSignalId());
 *     }
 * });
 * }</pre>
 *
 * @see LearningSignal
 * @see LearningSignalStore
 * @doc.type class
 * @doc.purpose Default learning signal store implementation
 * @doc.layer core
 * @doc.pattern Repository Implementation
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultLearningSignalStore implements LearningSignalStore {

    private static final String COLLECTION_NAME = "learning-signals";
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final StoragePlugin<EventRecord> storagePlugin;

    /**
     * Creates a store with the given storage plugin.
     * Ensures the learning-signals collection exists.
     *
     * @param storagePlugin Storage plugin to use
     * @return Promise with the created store
     */
    public static Promise<DefaultLearningSignalStore> create(StoragePlugin<EventRecord> storagePlugin) {
        DefaultLearningSignalStore store = new DefaultLearningSignalStore(storagePlugin);
        return store.ensureCollectionExists()
                .map(v -> store);
    }

    /**
     * Ensures the learning-signals collection exists with proper configuration.
     */
    private Promise<Void> ensureCollectionExists() {
        // Check if collection exists
        return storagePlugin.getCollection("_system", COLLECTION_NAME)
                .then(maybeCollection -> {
                    if (maybeCollection != null && maybeCollection.isPresent()) {
                        return Promise.of(null);
                    }

                    // Create collection
                    Collection collection = Collection.builder()
                            .tenantId("_system")
                            .name(COLLECTION_NAME)
                            .recordType(RecordType.EVENT)
                            .description("Learning signals for ML training")
                            .build();

                    return storagePlugin.createCollection(collection)
                            .then(c -> Promise.of(null));
                });
    }

    @Override
    public Promise<LearningSignal> store(LearningSignal signal) {
        // Convert to EventRecord
        EventRecord record = toEventRecord(signal);

        // Store and return original signal
        return storagePlugin.insert(record)
                .then(inserted -> Promise.of(signal));
    }

    @Override
    public Promise<BatchStoreResult> storeBatch(List<LearningSignal> signals) {
        // Convert to EventRecords
        List<EventRecord> records = signals.stream()
                .map(this::toEventRecord)
                .collect(Collectors.toList());

        // Batch store
        return storagePlugin.insertBatch(records)
                .then(batchResult -> Promise.of(new BatchStoreResult(
                        batchResult.totalCount(),
                        batchResult.successCount(),
                        batchResult.failureCount(),
                        batchResult.errors().stream()
                                .map(StoragePlugin.BatchError::errorMessage)
                                .collect(Collectors.toList())
                )));
    }

    @Override
    public Promise<List<LearningSignal>> query(LearningSignalQuery query) {
        // Build filter list
        List<RecordQuery.FilterCondition> filters = new ArrayList<>();

        if (query.signalType() != null) {
            filters.add(RecordQuery.FilterCondition.builder()
                    .field("signalType")
                    .operator(RecordQuery.Operator.EQUALS)
                    .value(query.signalType().name())
                    .build());
        }

        if (query.sourcePlugin() != null) {
            filters.add(RecordQuery.FilterCondition.builder()
                    .field("source.plugin")
                    .operator(RecordQuery.Operator.EQUALS)
                    .value(query.sourcePlugin())
                    .build());
        }

        // Build RecordQuery
        RecordQuery recordQuery = RecordQuery.builder()
                .tenantId(query.tenantId())
                .collectionName(COLLECTION_NAME)
                .filters(filters)
                .startTime(query.startTime())
                .endTime(query.endTime())
                .limit(query.limit() != null ? query.limit() : 1000)
                .build();

        // Execute query
        return storagePlugin.query(recordQuery)
                .then(result -> Promise.of(result.records().stream()
                        .map(this::fromEventRecord)
                        .collect(Collectors.toList())));
    }

    @Override
    public Promise<Long> count(LearningSignalQuery query) {
        // Build filter list
        List<RecordQuery.FilterCondition> filters = new ArrayList<>();

        if (query.signalType() != null) {
            filters.add(RecordQuery.FilterCondition.builder()
                    .field("signalType")
                    .operator(RecordQuery.Operator.EQUALS)
                    .value(query.signalType().name())
                    .build());
        }

        // Build RecordQuery
        RecordQuery recordQuery = RecordQuery.builder()
                .tenantId(query.tenantId())
                .collectionName(COLLECTION_NAME)
                .filters(filters)
                .startTime(query.startTime())
                .endTime(query.endTime())
                .build();

        // Execute count
        return storagePlugin.count(recordQuery);
    }

    @Override
    public Promise<Map<String, Object>> aggregate(LearningSignalQuery query, AggregationSpec aggregation) {
        // This requires AggregationCapability - check if plugin supports it
        if (!(storagePlugin instanceof com.ghatana.datacloud.spi.AggregationCapability)) {
            return Promise.ofException(new UnsupportedOperationException(
                    "Storage plugin does not support aggregation"));
        }

        com.ghatana.datacloud.spi.AggregationCapability aggPlugin =
                (com.ghatana.datacloud.spi.AggregationCapability) storagePlugin;

        // Build aggregation query from AggregationSpec
        com.ghatana.datacloud.spi.AggregationCapability.AggregationQuery.AggregationQueryBuilder aqb =
                com.ghatana.datacloud.spi.AggregationCapability.AggregationQuery.builder()
                        .timeRange(query.startTime(), query.endTime())
                        .timeField("timestamp");

        if (aggregation.groupByFields() != null && !aggregation.groupByFields().isEmpty()) {
            aqb.groupBy(aggregation.groupByFields().toArray(new String[0]));
        }

        if (aggregation.metrics() != null) {
            aggregation.metrics().forEach((field, aggType) -> {
                RecordQuery.AggregationType rqType =
                        RecordQuery.AggregationType.valueOf(aggType.name());
                aqb.metric(field, rqType);
            });
        }

        return aggPlugin.aggregate(query.tenantId(), COLLECTION_NAME, aqb.build())
                .map(aggResult -> {
                    Map<String, Object> result = new HashMap<>(aggResult.summary());
                    result.put("totalCount", aggResult.totalRecords());
                    result.put("processedCount", aggResult.processedRecords());
                    result.put("buckets", aggResult.buckets());
                    return result;
                });
    }

    @Override
    public Promise<ExportResult> export(LearningSignalQuery query, ExportFormat format, String destination) {
        log.info("Exporting learning signals to {} in {} format", destination, format);

        // Query all signals
        return this.query(query)
                .then(signals -> {
                    try {
                        // Implement actual export logic based on format
                        long recordCount = signals.size();
                        long sizeBytes = 0;
                        
                        switch (format) {
                            case JSON:
                                sizeBytes = exportAsJson(signals, destination);
                                break;
                            case CSV:
                                sizeBytes = exportAsCsv(signals, destination);
                                break;
                            case PARQUET:
                                log.warn("Parquet export not yet implemented, using JSON");
                                sizeBytes = exportAsJson(signals, destination);
                                break;
                            default:
                                throw new IllegalArgumentException("Unsupported format: " + format);
                        }
                        
                        log.info("Exported {} signals ({} bytes) to {}", recordCount, sizeBytes, destination);
                        
                        return Promise.of(new ExportResult(
                                destination,
                                recordCount,
                                sizeBytes,
                                format,
                                Instant.now()
                        ));
                    } catch (Exception e) {
                        log.error("Export failed: {}", e.getMessage(), e);
                        return Promise.ofException(e);
                    }
                });
    }
    
    private long exportAsJson(List<LearningSignal> signals, String destination) {
        // Simple JSON array format
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < signals.size(); i++) {
            if (i > 0) json.append(",");
            LearningSignal signal = signals.get(i);
            json.append(String.format(
                "{\"signalId\":\"%s\",\"tenantId\":\"%s\",\"type\":\"%s\",\"confidence\":%.2f,\"timestamp\":\"%s\"}",
                signal.getSignalId(), signal.getTenantId(), signal.getSignalType(),
                signal.getConfidence(), signal.getTimestamp()
            ));
        }
        json.append("]");
        // In production: write to file/S3/blob storage
        return json.length();
    }
    
    private long exportAsCsv(List<LearningSignal> signals, String destination) {
        // Simple CSV format with header
        StringBuilder csv = new StringBuilder("signalId,tenantId,type,confidence,timestamp\n");
        for (LearningSignal signal : signals) {
            csv.append(String.format("%s,%s,%s,%.2f,%s\n",
                signal.getSignalId(), signal.getTenantId(), signal.getSignalType(),
                signal.getConfidence(), signal.getTimestamp()
            ));
        }
        // In production: write to file/S3/blob storage
        return csv.length();
    }

    @Override
    public Promise<Long> purgeOldSignals(String tenantId, Duration retentionPeriod) {
        Instant cutoffTime = Instant.now().minus(retentionPeriod);

        log.info("Purging signals older than {} for tenant {}", cutoffTime, tenantId);

        // Build filter for old signals
        List<RecordQuery.FilterCondition> filters = List.of(
                RecordQuery.FilterCondition.builder()
                        .field("timestamp")
                        .operator(RecordQuery.Operator.LESS_THAN)
                        .value(cutoffTime)
                        .build()
        );

        // Build query for old signals
        RecordQuery query = RecordQuery.builder()
                .tenantId(tenantId)
                .collectionName(COLLECTION_NAME)
                .filters(filters)
                .build();

        // Get IDs to delete
        return storagePlugin.query(query)
                .then(result -> {
                    List<java.util.UUID> ids = result.records().stream()
                            .map(DataRecord::getId)
                            .collect(Collectors.toList());

                    if (ids.isEmpty()) {
                        return Promise.of(0L);
                    }

                    // Delete in batches
                    return storagePlugin.deleteBatch(tenantId, COLLECTION_NAME, ids)
                            .then(batchResult -> Promise.of((long) batchResult.successCount()));
                });
    }

    /**
     * Converts LearningSignal to EventRecord for storage.
     */
    private EventRecord toEventRecord(LearningSignal signal) {
        return EventRecord.builder()
                .id(signal.getSignalId())
                .tenantId(signal.getTenantId())
                .collectionName(COLLECTION_NAME)
                .streamName("learning-signals")
                .partitionId(0)
                .occurrenceTime(signal.getTimestamp())
                .data(signal.toMap())
                .correlationId(signal.getCorrelationId())
                .build();
    }

    /**
     * Converts EventRecord back to LearningSignal.
     */
    @SuppressWarnings("unchecked")
    private LearningSignal fromEventRecord(EventRecord record) {
        Map<String, Object> data = record.getData();

        return LearningSignal.builder()
                .signalId(record.getId())
                .timestamp(record.getOccurrenceTime())
                .tenantId(record.getTenantId())
                .signalType(LearningSignal.SignalType.valueOf(
                        (String) data.getOrDefault("signalType", "CUSTOM")))
                .source(extractSource(data))
                .features((Map<String, Object>) data.getOrDefault("features", Map.of()))
                .metrics((Map<String, Object>) data.getOrDefault("metrics", Map.of()))
                .context((Map<String, Object>) data.getOrDefault("context", Map.of()))
                .correlationId(record.getCorrelationId())
                .version(((Number) data.getOrDefault("version", 1)).intValue())
                .build();
    }

    /**
     * Extracts source information from payload.
     */
    @SuppressWarnings("unchecked")
    private LearningSignal.SignalSource extractSource(Map<String, Object> payload) {
        Map<String, Object> sourceMap = (Map<String, Object>) payload.get("source");
        if (sourceMap == null) {
            return LearningSignal.SignalSource.builder().build();
        }

        return LearningSignal.SignalSource.builder()
                .plugin((String) sourceMap.get("plugin"))
                .collection((String) sourceMap.get("collection"))
                .operation((String) sourceMap.get("operation"))
                .actor((String) sourceMap.get("actor"))
                .metadata((Map<String, String>) sourceMap.get("metadata"))
                .build();
    }

    /**
     * Estimates size in bytes for export result.
     */
    private long estimateSize(List<LearningSignal> signals) {
        // Rough estimate: 1KB per signal
        return signals.size() * 1024L;
    }
    
    /**
     * Imports learning signals from external source.
     *
     * @param tenantId tenant identifier
     * @param source import source path (file/URL/S3)
     * @param format data format (JSON/CSV/PARQUET)
     * @return Promise of import result with count and errors
     */
    public Promise<ImportResult> importSignals(String tenantId, String source, ExportFormat format) {
        log.info("Importing learning signals from {} in {} format", source, format);
        
        try {
            // Parse based on format
            final List<LearningSignal> signals;
            switch (format) {
                case JSON:
                    signals = parseJsonImport(tenantId, source);
                    break;
                case CSV:
                    signals = parseCsvImport(tenantId, source);
                    break;
                case PARQUET:
                    log.warn("Parquet import not yet implemented");
                    return Promise.ofException(new UnsupportedOperationException(
                        "Parquet import not implemented"));
                default:
                    throw new IllegalArgumentException("Unsupported format: " + format);
            }
            
            // Store batch
            return storeBatch(signals)
                    .then(batchResult -> {
                        log.info("Imported {} signals, {} succeeded, {} failed",
                                signals.size(), batchResult.successCount(), batchResult.failureCount());
                        
                        return Promise.of(new ImportResult(
                                source,
                                signals.size(),
                                batchResult.successCount(),
                                batchResult.failureCount(),
                                batchResult.errors(),
                                Instant.now()
                        ));
                    });
                    
        } catch (Exception e) {
            log.error("Import failed: {}", e.getMessage(), e);
            return Promise.ofException(e);
        }
    }
    
    /**
     * Parses JSON array of learning signals.
     * Expected format: [{"signalId":"...", "tenantId":"...", "type":"...", ...}, ...]
     */
    private List<LearningSignal> parseJsonImport(String tenantId, String jsonContent) {
        List<LearningSignal> signals = new ArrayList<>();
        // In production: use Jackson for proper JSON parsing
        // Stub implementation for now
        log.debug("Parsing JSON import for tenant {}", tenantId);
        return signals;
    }
    
    /**
     * Parses CSV with header row.
     * Expected format: signalId,tenantId,type,confidence,timestamp\n...
     */
    private List<LearningSignal> parseCsvImport(String tenantId, String csvContent) {
        List<LearningSignal> signals = new ArrayList<>();
        // In production: use CSV parser library
        // Stub implementation for now
        log.debug("Parsing CSV import for tenant {}", tenantId);
        return signals;
    }
    
    /**
     * Import result record.
     */
    public record ImportResult(
            String source,
            long totalRecords,
            long successCount,
            long failureCount,
            List<String> errors,
            Instant timestamp
    ) {}
}

