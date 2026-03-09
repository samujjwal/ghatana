package com.ghatana.datacloud.event.spi;

import com.ghatana.datacloud.event.common.Offset;
import com.ghatana.datacloud.event.common.PartitionId;
import com.ghatana.datacloud.event.model.Event;
import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SPI for pluggable event storage backends.
 *
 * <p><b>Purpose</b><br>
 * Provides durable persistence for events. Implementations must ensure:
 * <ul>
 *   <li><b>Durability</b>: Events survive crashes</li>
 *   <li><b>Ordering</b>: Events ordered within partition by offset</li>
 *   <li><b>Isolation</b>: Tenant separation enforced on all operations</li>
 *   <li><b>Idempotency</b>: Duplicate detection via idempotencyKey</li>
 * </ul>
 *
 * <p><b>Implementations</b><br>
 * <ul>
 *   <li><b>PostgreSQLStoragePlugin</b>: L1 warm storage (primary)</li>
 *   <li><b>RedisStoragePlugin</b>: L0 hot cache (recent events)</li>
 *   <li><b>IcebergStoragePlugin</b>: L2 cool analytics (aggregations)</li>
 *   <li><b>S3StoragePlugin</b>: L4 cold archive (compliance)</li>
 * </ul>
 *
 * <p><b>Storage Tiers</b><br>
 * <pre>
 * L0 (Hot)   → Redis/Memory      → Real-time reads (&lt;10ms)
 * L1 (Warm)  → PostgreSQL        → Recent history (days)
 * L2 (Cool)  → Iceberg/Parquet   → Analytics (months)
 * L4 (Cold)  → S3/Glacier        → Archive (years)
 * </pre>
 *
 * <p><b>Thread Safety</b><br>
 * All methods are async (Promise-based) and safe for concurrent calls.
 *
 * @see com.ghatana.platform.plugin.Plugin
 * @see Event
 * @doc.type interface
 * @doc.purpose SPI for event storage backends
 * @doc.layer spi
 * @doc.pattern Plugin, Strategy
 */
public interface StoragePlugin extends Plugin {

    // ==================== Append Operations ====================

    /**
     * Append single event to storage.
     *
     * <p>The event is assigned the next offset in its partition.
     * Idempotency is checked via event.idempotencyKey.</p>
     *
     * @param event event to append (must have tenantId, streamName, partitionId)
     * @return Promise with assigned offset
     * @throws IllegalArgumentException if event is invalid
     * @throws DuplicateEventException if idempotencyKey already exists
     */
    Promise<Offset> append(Event event);

    /**
     * Append batch of events atomically.
     *
     * <p>All events must belong to the same tenant and stream.
     * Either all events are appended or none (atomic batch).</p>
     *
     * @param events events to append (non-empty, same tenant/stream)
     * @return Promise with list of assigned offsets (same order as input)
     * @throws IllegalArgumentException if batch is empty or spans multiple streams
     */
    Promise<List<Offset>> appendBatch(List<Event> events);

    // ==================== Read Operations ====================

    /**
     * Read event by ID.
     *
     * @param tenantId tenant for isolation
     * @param eventId event identifier (UUID)
     * @return Promise with event if found
     */
    Promise<Optional<Event>> readById(String tenantId, String eventId);

    /**
     * Read event by idempotency key.
     *
     * <p>Useful for checking duplicates or retrieving previously-sent events.</p>
     *
     * @param tenantId tenant for isolation
     * @param idempotencyKey unique idempotency key
     * @return Promise with event if found
     */
    Promise<Optional<Event>> readByIdempotencyKey(String tenantId, String idempotencyKey);

    /**
     * Read events from partition starting at offset.
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param partitionId partition identifier
     * @param startOffset starting offset (inclusive)
     * @param limit maximum events to read
     * @return Promise with list of events (ordered by offset)
     */
    Promise<List<Event>> readRange(
        String tenantId,
        String streamName,
        PartitionId partitionId,
        Offset startOffset,
        int limit
    );

    /**
     * Read events by time range across all partitions.
     *
     * <p>Uses detectionTime (not occurrenceTime) for range queries.</p>
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param startTime start time (inclusive)
     * @param endTime end time (exclusive)
     * @param limit maximum events to read
     * @return Promise with list of events (ordered by detectionTime)
     */
    Promise<List<Event>> readByTimeRange(
        String tenantId,
        String streamName,
        Instant startTime,
        Instant endTime,
        int limit
    );

    // ==================== Offset Management ====================

    /**
     * Get current (latest) offset for partition.
     *
     * <p>Returns -1 if partition is empty.</p>
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param partitionId partition identifier
     * @return Promise with current offset
     */
    Promise<Offset> getCurrentOffset(
        String tenantId,
        String streamName,
        PartitionId partitionId
    );

    /**
     * Get earliest (oldest) offset for partition.
     *
     * <p>Returns 0 for new partitions, or first available offset
     * if older events have been deleted.</p>
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param partitionId partition identifier
     * @return Promise with earliest offset
     */
    Promise<Offset> getEarliestOffset(
        String tenantId,
        String streamName,
        PartitionId partitionId
    );

    // ==================== Retention & Cleanup ====================

    /**
     * Delete events older than specified time.
     *
     * <p>Used for retention policy enforcement.</p>
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param beforeTime delete events with detectionTime before this
     * @return Promise with count of deleted events
     */
    Promise<Long> deleteBeforeTime(
        String tenantId,
        String streamName,
        Instant beforeTime
    );

    /**
     * Delete events before specified offset.
     *
     * <p>Used for offset-based retention or compaction.</p>
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param partitionId partition identifier
     * @param beforeOffset delete events before this offset (exclusive)
     * @return Promise with count of deleted events
     */
    Promise<Long> deleteBeforeOffset(
        String tenantId,
        String streamName,
        PartitionId partitionId,
        Offset beforeOffset
    );

    // ==================== Capabilities ====================

    /**
     * Get storage plugin capabilities.
     *
     * @return capabilities descriptor
     */
    Capabilities capabilities();

    /**
     * Storage plugin capabilities.
     */
    interface Capabilities {
        /**
         * Supports atomic transactions across multiple appends.
         */
        boolean supportsTransactions();

        /**
         * Supports real-time streaming (tailing).
         */
        boolean supportsStreaming();

        /**
         * Supports time-range queries.
         */
        boolean supportsTimeRangeQuery();

        /**
         * Supports log compaction (keeping latest per key).
         */
        boolean supportsCompaction();

        /**
         * Maximum events per batch append.
         */
        long maxBatchSize();

        /**
         * Recommended batch size for optimal throughput.
         */
        int recommendedBatchSize();
    }

    // ==================== Exceptions ====================

    /**
     * Exception for duplicate event detection.
     */
    class DuplicateEventException extends RuntimeException {
        private final String idempotencyKey;
        private final String existingEventId;

        public DuplicateEventException(String idempotencyKey, String existingEventId) {
            super("Duplicate event with idempotencyKey: " + idempotencyKey);
            this.idempotencyKey = idempotencyKey;
            this.existingEventId = existingEventId;
        }

        public String getIdempotencyKey() {
            return idempotencyKey;
        }

        public String getExistingEventId() {
            return existingEventId;
        }
    }
}
