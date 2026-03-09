package com.ghatana.datacloud.event.spi.repository;

import com.ghatana.datacloud.event.model.EventStream;
import com.ghatana.datacloud.event.model.Partition;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for EventStream persistence operations.
 *
 * <p><b>Purpose</b><br>
 * Provides data access operations for EventStream entities.
 * EventStreams are the logical conduits for events of a single EventType.
 *
 * <p><b>Relationship Model</b><br>
 * <pre>
 * EventType (1) ←── (N) EventStream (1) ←── (N) Partition
 * </pre>
 *
 * <p><b>Stream Properties</b><br>
 * <ul>
 *   <li>Partitioned for parallelism</li>
 *   <li>Tiered retention (hot/warm/cool/cold)</li>
 *   <li>Optional compaction</li>
 * </ul>
 *
 * @see EventStream
 * @see Partition
 * @doc.type interface
 * @doc.purpose Repository for EventStream persistence
 * @doc.layer spi
 * @doc.pattern Repository
 */
public interface EventStreamRepository {

    // ==================== Read Operations ====================

    /**
     * Find stream by ID.
     *
     * @param tenantId tenant for isolation
     * @param streamId stream UUID
     * @return Promise with stream if found
     */
    Promise<Optional<EventStream>> findById(String tenantId, String streamId);

    /**
     * Find stream by name.
     *
     * @param tenantId tenant for isolation
     * @param name stream name
     * @return Promise with stream if found
     */
    Promise<Optional<EventStream>> findByName(String tenantId, String name);

    /**
     * Find streams by event type.
     *
     * @param tenantId tenant for isolation
     * @param eventTypeName event type name
     * @return Promise with list of streams for the type
     */
    Promise<List<EventStream>> findByEventType(String tenantId, String eventTypeName);

    /**
     * Find all streams for tenant.
     *
     * @param tenantId tenant for isolation
     * @return Promise with list of all streams
     */
    Promise<List<EventStream>> findAll(String tenantId);

    /**
     * Find active streams for tenant.
     *
     * @param tenantId tenant for isolation
     * @return Promise with list of active streams
     */
    Promise<List<EventStream>> findAllActive(String tenantId);

    /**
     * Find stream with partitions eagerly loaded.
     *
     * @param tenantId tenant for isolation
     * @param streamId stream UUID
     * @return Promise with stream and partitions
     */
    Promise<Optional<EventStream>> findByIdWithPartitions(String tenantId, String streamId);

    // ==================== Partition Operations ====================

    /**
     * Get partitions for stream.
     *
     * @param tenantId tenant for isolation
     * @param streamId stream UUID
     * @return Promise with list of partitions
     */
    Promise<List<Partition>> findPartitions(String tenantId, String streamId);

    /**
     * Get partition by index.
     *
     * @param tenantId tenant for isolation
     * @param streamId stream UUID
     * @param partitionIndex partition index
     * @return Promise with partition if found
     */
    Promise<Optional<Partition>> findPartition(
        String tenantId,
        String streamId,
        int partitionIndex
    );

    /**
     * Update partition offsets.
     *
     * @param partition partition to update
     * @return Promise with updated partition
     */
    Promise<Partition> updatePartition(Partition partition);

    // ==================== Count Operations ====================

    /**
     * Count streams for tenant.
     *
     * @param tenantId tenant for isolation
     * @return Promise with count
     */
    Promise<Long> countByTenant(String tenantId);

    /**
     * Count streams by event type.
     *
     * @param tenantId tenant for isolation
     * @param eventTypeName event type name
     * @return Promise with count
     */
    Promise<Long> countByEventType(String tenantId, String eventTypeName);

    // ==================== Persistence Operations ====================

    /**
     * Save stream.
     *
     * <p>Creates partitions based on partitionCount.</p>
     *
     * @param stream stream to save
     * @return Promise with saved stream
     */
    Promise<EventStream> save(EventStream stream);

    /**
     * Update stream.
     *
     * @param stream stream to update
     * @return Promise with updated stream
     */
    Promise<EventStream> update(EventStream stream);

    /**
     * Update stream retention settings.
     *
     * @param tenantId tenant for isolation
     * @param streamId stream UUID
     * @param hotRetentionMinutes hot tier retention
     * @param warmRetentionDays warm tier retention
     * @param coolRetentionDays cool tier retention
     * @param coldRetentionDays cold tier retention
     * @return Promise with updated stream
     */
    Promise<EventStream> updateRetention(
        String tenantId,
        String streamId,
        Integer hotRetentionMinutes,
        Integer warmRetentionDays,
        Integer coolRetentionDays,
        Integer coldRetentionDays
    );

    /**
     * Scale stream (change partition count).
     *
     * <p>Note: Scaling down is not supported; only scaling up.</p>
     *
     * @param tenantId tenant for isolation
     * @param streamId stream UUID
     * @param newPartitionCount new partition count (must be >= current)
     * @return Promise with scaled stream
     */
    Promise<EventStream> scale(String tenantId, String streamId, int newPartitionCount);

    /**
     * Check if stream exists by name.
     *
     * @param tenantId tenant for isolation
     * @param name stream name
     * @return Promise with existence flag
     */
    Promise<Boolean> existsByName(String tenantId, String name);

    // ==================== Delete Operations ====================

    /**
     * Delete stream by ID.
     *
     * <p>Cascades to delete partitions. Only allowed if no events exist.</p>
     *
     * @param tenantId tenant for isolation
     * @param streamId stream UUID
     * @return Promise with deletion success flag
     */
    Promise<Boolean> deleteById(String tenantId, String streamId);

    /**
     * Soft delete (set active=false).
     *
     * @param tenantId tenant for isolation
     * @param streamId stream UUID
     * @return Promise with updated stream
     */
    Promise<EventStream> softDelete(String tenantId, String streamId);
}
