package com.ghatana.datacloud.event.spi.repository;

import com.ghatana.datacloud.event.common.Offset;
import com.ghatana.datacloud.event.common.PartitionId;
import com.ghatana.datacloud.event.model.Event;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Event persistence operations.
 *
 * <p><b>Purpose</b><br>
 * Provides data access operations for Event entities. Unlike StoragePlugin
 * which focuses on append/read operations, this repository provides
 * CRUD-style operations for administrative and query use cases.
 *
 * <p><b>Usage Context</b><br>
 * <ul>
 *   <li><b>StoragePlugin</b>: High-throughput event ingestion/tailing</li>
 *   <li><b>EventRepository</b>: Admin queries, search, management</li>
 * </ul>
 *
 * <p><b>Implementation Notes</b><br>
 * <ul>
 *   <li>All methods are async (Promise-based)</li>
 *   <li>Tenant isolation enforced on all operations</li>
 *   <li>Events are immutable (no update operations)</li>
 * </ul>
 *
 * @see Event
 * @see com.ghatana.datacloud.event.spi.StoragePlugin
 * @doc.type interface
 * @doc.purpose Repository for Event persistence
 * @doc.layer spi
 * @doc.pattern Repository
 */
public interface EventRepository {

    // ==================== Read Operations ====================

    /**
     * Find event by ID.
     *
     * @param tenantId tenant for isolation
     * @param eventId event UUID
     * @return Promise with event if found
     */
    Promise<Optional<Event>> findById(String tenantId, String eventId);

    /**
     * Find event by idempotency key.
     *
     * @param tenantId tenant for isolation
     * @param idempotencyKey unique idempotency key
     * @return Promise with event if found
     */
    Promise<Optional<Event>> findByIdempotencyKey(String tenantId, String idempotencyKey);

    /**
     * Find events by correlation ID.
     *
     * @param tenantId tenant for isolation
     * @param correlationId correlation ID
     * @param limit maximum results
     * @return Promise with list of correlated events
     */
    Promise<List<Event>> findByCorrelationId(String tenantId, String correlationId, int limit);

    /**
     * Find events by causation ID.
     *
     * @param tenantId tenant for isolation
     * @param causationId causation ID
     * @param limit maximum results
     * @return Promise with list of caused events
     */
    Promise<List<Event>> findByCausationId(String tenantId, String causationId, int limit);

    /**
     * Find events by event type.
     *
     * @param tenantId tenant for isolation
     * @param eventTypeName event type name
     * @param startTime start time (inclusive)
     * @param endTime end time (exclusive)
     * @param limit maximum results
     * @return Promise with list of events
     */
    Promise<List<Event>> findByEventType(
        String tenantId,
        String eventTypeName,
        Instant startTime,
        Instant endTime,
        int limit
    );

    /**
     * Find events by stream and partition.
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param partitionId partition ID
     * @param startOffset start offset (inclusive)
     * @param limit maximum results
     * @return Promise with list of events
     */
    Promise<List<Event>> findByStreamPartition(
        String tenantId,
        String streamName,
        PartitionId partitionId,
        Offset startOffset,
        int limit
    );

    // ==================== Count Operations ====================

    /**
     * Count events for tenant.
     *
     * @param tenantId tenant for isolation
     * @return Promise with event count
     */
    Promise<Long> countByTenant(String tenantId);

    /**
     * Count events by event type.
     *
     * @param tenantId tenant for isolation
     * @param eventTypeName event type name
     * @return Promise with event count
     */
    Promise<Long> countByEventType(String tenantId, String eventTypeName);

    /**
     * Count events by stream.
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @return Promise with event count
     */
    Promise<Long> countByStream(String tenantId, String streamName);

    // ==================== Persistence Operations ====================

    /**
     * Save new event.
     *
     * <p>Events are immutable; this creates a new record.</p>
     *
     * @param event event to save
     * @return Promise with saved event (with assigned ID and offset)
     */
    Promise<Event> save(Event event);

    /**
     * Save batch of events.
     *
     * @param events events to save
     * @return Promise with saved events
     */
    Promise<List<Event>> saveAll(List<Event> events);

    /**
     * Check if event exists by ID.
     *
     * @param tenantId tenant for isolation
     * @param eventId event UUID
     * @return Promise with existence flag
     */
    Promise<Boolean> existsById(String tenantId, String eventId);

    /**
     * Check if event exists by idempotency key.
     *
     * @param tenantId tenant for isolation
     * @param idempotencyKey unique idempotency key
     * @return Promise with existence flag
     */
    Promise<Boolean> existsByIdempotencyKey(String tenantId, String idempotencyKey);

    // ==================== Delete Operations ====================

    /**
     * Delete events before time (for retention).
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param beforeTime delete events with detectionTime before this
     * @return Promise with count of deleted events
     */
    Promise<Long> deleteBeforeTime(String tenantId, String streamName, Instant beforeTime);

    /**
     * Delete events before offset (for compaction).
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param partitionId partition ID
     * @param beforeOffset delete events before this offset
     * @return Promise with count of deleted events
     */
    Promise<Long> deleteBeforeOffset(
        String tenantId,
        String streamName,
        PartitionId partitionId,
        Offset beforeOffset
    );
}
