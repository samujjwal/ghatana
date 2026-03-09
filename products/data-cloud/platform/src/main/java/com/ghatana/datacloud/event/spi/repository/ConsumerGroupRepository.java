package com.ghatana.datacloud.event.spi.repository;

import com.ghatana.datacloud.event.common.Offset;
import com.ghatana.datacloud.event.common.PartitionId;
import com.ghatana.datacloud.event.model.ConsumerGroup;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for ConsumerGroup persistence operations.
 *
 * <p><b>Purpose</b><br>
 * Provides data access operations for ConsumerGroup entities.
 * Consumer groups coordinate event consumption across multiple consumers.
 *
 * <p><b>Offset Tracking</b><br>
 * Each consumer group tracks committed offsets per partition:
 * <pre>
 * {
 *   partition_0: 42,
 *   partition_1: 128,
 *   partition_2: 96
 * }
 * </pre>
 *
 * @see ConsumerGroup
 * @doc.type interface
 * @doc.purpose Repository for ConsumerGroup persistence
 * @doc.layer spi
 * @doc.pattern Repository
 */
public interface ConsumerGroupRepository {

    // ==================== Read Operations ====================

    /**
     * Find consumer group by ID.
     *
     * @param tenantId tenant for isolation
     * @param groupId consumer group UUID
     * @return Promise with group if found
     */
    Promise<Optional<ConsumerGroup>> findById(String tenantId, String groupId);

    /**
     * Find consumer group by name and stream.
     *
     * @param tenantId tenant for isolation
     * @param groupName consumer group name
     * @param streamId stream UUID
     * @return Promise with group if found
     */
    Promise<Optional<ConsumerGroup>> findByNameAndStream(
        String tenantId,
        String groupName,
        String streamId
    );

    /**
     * Find consumer groups by stream.
     *
     * @param tenantId tenant for isolation
     * @param streamId stream UUID
     * @return Promise with list of groups for stream
     */
    Promise<List<ConsumerGroup>> findByStream(String tenantId, String streamId);

    /**
     * Find all consumer groups for tenant.
     *
     * @param tenantId tenant for isolation
     * @return Promise with list of all groups
     */
    Promise<List<ConsumerGroup>> findAll(String tenantId);

    // ==================== Offset Operations ====================

    /**
     * Get committed offsets for consumer group.
     *
     * @param tenantId tenant for isolation
     * @param groupId consumer group UUID
     * @return Promise with partition to offset mapping
     */
    Promise<Map<PartitionId, Offset>> getCommittedOffsets(String tenantId, String groupId);

    /**
     * Get committed offset for specific partition.
     *
     * @param tenantId tenant for isolation
     * @param groupId consumer group UUID
     * @param partitionId partition ID
     * @return Promise with committed offset (or EARLIEST if not committed)
     */
    Promise<Offset> getCommittedOffset(
        String tenantId,
        String groupId,
        PartitionId partitionId
    );

    /**
     * Commit offsets for consumer group.
     *
     * @param tenantId tenant for isolation
     * @param groupId consumer group UUID
     * @param offsets partition to offset mapping
     * @return Promise completing when committed
     */
    Promise<Void> commitOffsets(
        String tenantId,
        String groupId,
        Map<PartitionId, Offset> offsets
    );

    /**
     * Commit single offset.
     *
     * @param tenantId tenant for isolation
     * @param groupId consumer group UUID
     * @param partitionId partition ID
     * @param offset offset to commit
     * @return Promise completing when committed
     */
    Promise<Void> commitOffset(
        String tenantId,
        String groupId,
        PartitionId partitionId,
        Offset offset
    );

    /**
     * Calculate lag (difference between latest and committed offset).
     *
     * @param tenantId tenant for isolation
     * @param groupId consumer group UUID
     * @return Promise with partition to lag mapping
     */
    Promise<Map<PartitionId, Long>> calculateLag(String tenantId, String groupId);

    /**
     * Calculate total lag across all partitions.
     *
     * @param tenantId tenant for isolation
     * @param groupId consumer group UUID
     * @return Promise with total lag
     */
    Promise<Long> calculateTotalLag(String tenantId, String groupId);

    // ==================== Persistence Operations ====================

    /**
     * Save consumer group.
     *
     * @param group consumer group to save
     * @return Promise with saved group
     */
    Promise<ConsumerGroup> save(ConsumerGroup group);

    /**
     * Update consumer group.
     *
     * @param group consumer group to update
     * @return Promise with updated group
     */
    Promise<ConsumerGroup> update(ConsumerGroup group);

    /**
     * Check if consumer group exists.
     *
     * @param tenantId tenant for isolation
     * @param groupName consumer group name
     * @param streamId stream UUID
     * @return Promise with existence flag
     */
    Promise<Boolean> exists(String tenantId, String groupName, String streamId);

    // ==================== Delete Operations ====================

    /**
     * Delete consumer group by ID.
     *
     * @param tenantId tenant for isolation
     * @param groupId consumer group UUID
     * @return Promise with deletion success flag
     */
    Promise<Boolean> deleteById(String tenantId, String groupId);

    /**
     * Delete consumer group by name and stream.
     *
     * @param tenantId tenant for isolation
     * @param groupName consumer group name
     * @param streamId stream UUID
     * @return Promise with deletion success flag
     */
    Promise<Boolean> deleteByNameAndStream(String tenantId, String groupName, String streamId);

    /**
     * Reset offsets to earliest.
     *
     * <p>Used for reprocessing all events.</p>
     *
     * @param tenantId tenant for isolation
     * @param groupId consumer group UUID
     * @return Promise completing when reset
     */
    Promise<Void> resetOffsetsToEarliest(String tenantId, String groupId);

    /**
     * Reset offsets to latest.
     *
     * <p>Used for skipping to newest events.</p>
     *
     * @param tenantId tenant for isolation
     * @param groupId consumer group UUID
     * @return Promise completing when reset
     */
    Promise<Void> resetOffsetsToLatest(String tenantId, String groupId);
}
