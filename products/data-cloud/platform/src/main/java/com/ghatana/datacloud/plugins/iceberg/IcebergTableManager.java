package com.ghatana.datacloud.plugins.iceberg;

import io.activej.promise.Promise;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.types.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;

/**
 * Manages Apache Iceberg tables for EventCloud L2 (COOL tier) storage.
 *
 * <p><b>Purpose</b><br>
 * Handles table lifecycle operations including:
 * <ul>
 *   <li>Table creation with proper schema and partitioning</li>
 *   <li>Schema evolution for backward compatibility</li>
 *   <li>Time-travel queries for historical analysis</li>
 *   <li>Snapshot management and expiration</li>
 *   <li>Table compaction for query performance</li>
 * </ul>
 *
 * <p><b>Schema Design</b><br>
 * Events are stored with the following schema:
 * <pre>
 * - id: string (UUID)
 * - tenant_id: string (partition key)
 * - event_type_name: string
 * - event_type_version: string
 * - stream_name: string (partition key)
 * - partition_id: int
 * - event_offset: long
 * - occurred_at: timestamp
 * - detected_at: timestamp
 * - ingested_at: timestamp
 * - headers: string (JSON)
 * - payload: string (JSON)
 * - content_type: string
 * - correlation_id: string
 * - causation_id: string
 * - idempotency_key: string
 * - detection_date: date (partition key)
 * </pre>
 *
 * <p><b>Partitioning Strategy</b><br>
 * Tables are partitioned by:
 * <ol>
 *   <li><b>tenant_id</b>: For tenant isolation and efficient pruning</li>
 *   <li><b>stream_name</b>: For stream-specific queries</li>
 *   <li><b>detection_date</b>: For time-range queries (day granularity)</li>
 * </ol>
 *
 * <p><b>Thread Safety</b><br>
 * All methods are async (Promise-based) and safe for concurrent calls.
 * Iceberg provides MVCC for concurrent reads during writes.
 *
 * @doc.type class
 * @doc.purpose Iceberg table management for L2 storage
 * @doc.layer plugin
 * @doc.pattern Manager, Repository
 */
public class IcebergTableManager implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(IcebergTableManager.class);

    // ==================== Schema Definition ====================

    /**
     * Iceberg schema for EventCloud events.
     */
    public static final Schema EVENT_SCHEMA = new Schema(
            // Primary identifier
            required(1, "id", Types.StringType.get(), "Event UUID"),
            
            // Tenant and organization
            required(2, "tenant_id", Types.StringType.get(), "Tenant identifier"),
            
            // Event type
            required(3, "event_type_name", Types.StringType.get(), "Event type name"),
            required(4, "event_type_version", Types.StringType.get(), "Event type version"),
            
            // Stream and partition
            required(5, "stream_name", Types.StringType.get(), "Event stream name"),
            required(6, "partition_id", Types.IntegerType.get(), "Partition identifier"),
            required(7, "event_offset", Types.LongType.get(), "Event offset in partition"),
            
            // Timestamps
            required(8, "occurred_at", Types.TimestampType.withZone(), "When event occurred"),
            required(9, "detected_at", Types.TimestampType.withZone(), "When event was detected"),
            required(10, "ingested_at", Types.TimestampType.withZone(), "When event was ingested"),
            
            // Content
            optional(11, "headers", Types.StringType.get(), "Event headers as JSON"),
            required(12, "payload", Types.StringType.get(), "Event payload as JSON"),
            optional(13, "content_type", Types.StringType.get(), "Content MIME type"),
            
            // Correlation
            optional(14, "correlation_id", Types.StringType.get(), "Correlation ID for tracing"),
            optional(15, "causation_id", Types.StringType.get(), "Causation ID for event chain"),
            optional(16, "idempotency_key", Types.StringType.get(), "Idempotency key for dedup"),
            
            // Derived partition column
            required(17, "detection_date", Types.DateType.get(), "Detection date for partitioning"),
            
            // Metadata
            optional(18, "created_by", Types.StringType.get(), "Creator identifier")
    );

    // ==================== Fields ====================

    private final Catalog catalog;
    private final IcebergStorageConfig config;
    private final Namespace namespace;

    // ==================== Constructor ====================

    /**
     * Creates an IcebergTableManager.
     *
     * @param catalog the Iceberg catalog
     * @param config  configuration
     */
    public IcebergTableManager(Catalog catalog, IcebergStorageConfig config) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.config = Objects.requireNonNull(config, "config");
        this.namespace = Namespace.of(config.getDatabaseName());
    }

    // ==================== Table Operations ====================

    /**
     * Creates the events table if it doesn't exist.
     *
     * @param tableName table name (usually "events")
     * @return Promise completing when table is ready
     */
    public Promise<Table> createTableIfNotExists(String tableName) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            TableIdentifier tableId = TableIdentifier.of(namespace, tableName);

            if (catalog.tableExists(tableId)) {
                log.info("Table already exists: {}", tableId);
                return catalog.loadTable(tableId);
            }

            // Create partition spec based on config
            PartitionSpec partitionSpec = buildPartitionSpec();

            // Create table with properties
            Map<String, String> properties = buildTableProperties();

            Table table = catalog.createTable(tableId, EVENT_SCHEMA, partitionSpec, properties);

            log.info("Created Iceberg table: {} with partitioning: {}", tableId, partitionSpec);
            return table;
        });
    }

    /**
     * Loads an existing table.
     *
     * @param tableName table name
     * @return Promise with loaded table
     */
    public Promise<Table> loadTable(String tableName) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            TableIdentifier tableId = TableIdentifier.of(namespace, tableName);
            return catalog.loadTable(tableId);
        });
    }

    /**
     * Checks if a table exists.
     *
     * @param tableName table name
     * @return Promise with true if exists
     */
    public Promise<Boolean> tableExists(String tableName) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            TableIdentifier tableId = TableIdentifier.of(namespace, tableName);
            return catalog.tableExists(tableId);
        });
    }

    /**
     * Drops a table (USE WITH CAUTION).
     *
     * @param tableName table name
     * @return Promise completing when table is dropped
     */
    public Promise<Boolean> dropTable(String tableName) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            TableIdentifier tableId = TableIdentifier.of(namespace, tableName);
            boolean dropped = catalog.dropTable(tableId, true);
            if (dropped) {
                log.warn("Dropped Iceberg table: {}", tableId);
            }
            return dropped;
        });
    }

    // ==================== Query Operations ====================

    /**
     * Scans a table with filters.
     *
     * @param table    table to scan
     * @param tenantId tenant filter
     * @param streamName stream filter (optional)
     * @param startTime start time filter (optional)
     * @param endTime   end time filter (optional)
     * @param limit     max records
     * @return Promise with list of records
     */
    public Promise<List<Record>> scanTable(
            Table table,
            String tenantId,
            String streamName,
            Instant startTime,
            Instant endTime,
            int limit) {
        
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            // Build filter expression
            Expression filter = Expressions.equal("tenant_id", tenantId);

            if (streamName != null && !streamName.isBlank()) {
                filter = Expressions.and(filter, Expressions.equal("stream_name", streamName));
            }

            if (startTime != null) {
                filter = Expressions.and(filter,
                        Expressions.greaterThanOrEqual("detected_at", startTime.toEpochMilli() * 1000));
            }

            if (endTime != null) {
                filter = Expressions.and(filter,
                        Expressions.lessThan("detected_at", endTime.toEpochMilli() * 1000));
            }

            // Execute scan
            List<Record> records = new ArrayList<>();
            try (CloseableIterable<Record> results = IcebergGenerics.read(table)
                    .where(filter)
                    .build()) {
                
                int count = 0;
                for (Record record : results) {
                    if (count >= limit) break;
                    records.add(record);
                    count++;
                }
            }

            log.debug("Scanned {} records from table {} for tenant {}",
                    records.size(), table.name(), tenantId);

            return records;
        });
    }

    /**
     * Scans a table at a specific snapshot (time-travel).
     *
     * @param table        table to scan
     * @param snapshotTime timestamp for the snapshot
     * @param tenantId     tenant filter
     * @param limit        max records
     * @return Promise with list of records
     */
    public Promise<List<Record>> scanAtSnapshot(
            Table table,
            Instant snapshotTime,
            String tenantId,
            int limit) {
        
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            // Find snapshot at or before the given time
            Long snapshotId = findSnapshotAtTime(table, snapshotTime);

            if (snapshotId == null) {
                log.warn("No snapshot found at or before {}", snapshotTime);
                return Collections.emptyList();
            }

            Expression filter = Expressions.equal("tenant_id", tenantId);

            List<Record> records = new ArrayList<>();
            try (CloseableIterable<Record> results = IcebergGenerics.read(table)
                    .useSnapshot(snapshotId)
                    .where(filter)
                    .build()) {
                
                int count = 0;
                for (Record record : results) {
                    if (count >= limit) break;
                    records.add(record);
                    count++;
                }
            }

            log.debug("Time-travel scan: {} records at snapshot {} ({})",
                    records.size(), snapshotId, snapshotTime);

            return records;
        });
    }

    // ==================== Snapshot Operations ====================

    /**
     * Finds the snapshot ID at or before a given time.
     *
     * @param table        table
     * @param snapshotTime target time
     * @return snapshot ID or null if not found
     */
    private Long findSnapshotAtTime(Table table, Instant snapshotTime) {
        long targetMs = snapshotTime.toEpochMilli();
        Long bestSnapshot = null;
        long bestTime = Long.MIN_VALUE;

        for (org.apache.iceberg.Snapshot snapshot : table.snapshots()) {
            long snapshotMs = snapshot.timestampMillis();
            if (snapshotMs <= targetMs && snapshotMs > bestTime) {
                bestTime = snapshotMs;
                bestSnapshot = snapshot.snapshotId();
            }
        }

        return bestSnapshot;
    }

    /**
     * Lists all snapshots for a table.
     *
     * @param table table
     * @return list of snapshot info
     */
    public List<SnapshotInfo> listSnapshots(Table table) {
        List<SnapshotInfo> snapshots = new ArrayList<>();
        
        for (org.apache.iceberg.Snapshot snapshot : table.snapshots()) {
            snapshots.add(new SnapshotInfo(
                    snapshot.snapshotId(),
                    Instant.ofEpochMilli(snapshot.timestampMillis()),
                    snapshot.operation(),
                    snapshot.summary()
            ));
        }

        return snapshots;
    }

    /**
     * Expires old snapshots.
     *
     * @param table table
     * @return Promise completing when snapshots are expired
     */
    public Promise<Void> expireSnapshots(Table table) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            long olderThanMs = System.currentTimeMillis() -
                    config.getSnapshotRetention().toMillis();

            table.expireSnapshots()
                    .expireOlderThan(olderThanMs)
                    .retainLast(config.getMaxSnapshotsToKeep())
                    .commit();

            log.info("Expired snapshots older than {} for table {}",
                    Instant.ofEpochMilli(olderThanMs), table.name());
            return null;
        });
    }

    // ==================== Record Conversion ====================

    /**
     * Creates a GenericRecord from event data.
     *
     * @param eventData map of event fields
     * @return Iceberg record
     */
    public GenericRecord createRecord(Map<String, Object> eventData) {
        GenericRecord record = GenericRecord.create(EVENT_SCHEMA);

        record.setField("id", eventData.get("id"));
        record.setField("tenant_id", eventData.get("tenantId"));
        record.setField("event_type_name", eventData.get("eventTypeName"));
        record.setField("event_type_version", eventData.get("eventTypeVersion"));
        record.setField("stream_name", eventData.get("streamName"));
        record.setField("partition_id", eventData.get("partitionId"));
        record.setField("event_offset", eventData.get("eventOffset"));

        // Timestamps
        Instant occurredAt = (Instant) eventData.get("occurrenceTime");
        Instant detectedAt = (Instant) eventData.get("detectionTime");
        Instant ingestedAt = (Instant) eventData.getOrDefault("ingestedAt", Instant.now());

        record.setField("occurred_at", occurredAt.atOffset(ZoneOffset.UTC));
        record.setField("detected_at", detectedAt.atOffset(ZoneOffset.UTC));
        record.setField("ingested_at", ingestedAt.atOffset(ZoneOffset.UTC));

        // Content
        record.setField("headers", eventData.get("headers"));
        record.setField("payload", eventData.get("payload"));
        record.setField("content_type", eventData.get("contentType"));

        // Correlation
        record.setField("correlation_id", eventData.get("correlationId"));
        record.setField("causation_id", eventData.get("causationId"));
        record.setField("idempotency_key", eventData.get("idempotencyKey"));

        // Derived partition column
        LocalDate detectionDate = detectedAt.atZone(ZoneOffset.UTC).toLocalDate();
        record.setField("detection_date", detectionDate);

        record.setField("created_by", eventData.get("createdBy"));

        return record;
    }

    // ==================== Helper Methods ====================

    /**
     * Builds the partition spec based on configuration.
     */
    private PartitionSpec buildPartitionSpec() {
        PartitionSpec.Builder builder = PartitionSpec.builderFor(EVENT_SCHEMA)
                .identity("tenant_id")
                .identity("stream_name");

        // Add time-based partition
        switch (config.getPartitionGranularity()) {
            case HOUR -> builder.hour("detected_at");
            case DAY -> builder.day("detected_at");
            case MONTH -> builder.month("detected_at");
            case YEAR -> builder.year("detected_at");
        }

        return builder.build();
    }

    /**
     * Builds table properties from configuration.
     */
    private Map<String, String> buildTableProperties() {
        Map<String, String> props = new HashMap<>();

        // File format
        props.put("write.format.default", config.getFileFormat().name().toLowerCase());

        // Compression
        String codec = config.getCompressionCodec();
        switch (config.getFileFormat()) {
            case PARQUET -> props.put("write.parquet.compression-codec", codec);
            case ORC -> props.put("write.orc.compression-codec", codec);
            case AVRO -> props.put("write.avro.compression-codec", codec);
        }

        // File sizes
        props.put("write.target-file-size-bytes", String.valueOf(config.getTargetFileSizeBytes()));
        props.put("read.split.target-size", String.valueOf(config.getSplitSizeBytes()));

        // Commit properties
        if (config.isCommitRetryEnabled()) {
            props.put("commit.retry.num-retries", String.valueOf(config.getCommitRetryAttempts()));
        }

        // Compaction
        if (config.isCompactionEnabled()) {
            props.put("write.merge.mode", "copy-on-write");
        }

        return props;
    }

    @Override
    public void close() {
        // Catalog cleanup if needed
        if (catalog instanceof Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException e) {
                log.warn("Error closing catalog", e);
            }
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Snapshot information record.
     */
    public record SnapshotInfo(
            long snapshotId,
            Instant timestamp,
            String operation,
            Map<String, String> summary
    ) {}
}
