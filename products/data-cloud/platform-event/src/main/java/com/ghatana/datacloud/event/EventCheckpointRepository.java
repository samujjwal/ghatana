/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.event;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository for event checkpoint tracking.
 *
 * <p>Manages consumer offsets and CDC checkpoint persistence.
 *
 * @doc.type interface
 * @doc.purpose Checkpoint tracking for CDC and event replay
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface EventCheckpointRepository {

    /**
     * Save checkpoint for a consumer group.
     *
     * @param consumerId consumer identifier
     * @param partition partition number
     * @param offset event offset
     * @return promise completing when saved
     */
    Promise<Void> saveCheckpoint(String consumerId, int partition, long offset);

    /**
     * Get checkpoint for a consumer group.
     *
     * @param consumerId consumer identifier
     * @param partition partition number
     * @return promise of checkpoint offset (empty if not set)
     */
    Promise<Optional<Long>> getCheckpoint(String consumerId, int partition);

    /**
     * Get all checkpoints for a consumer group.
     *
     * @param consumerId consumer identifier
     * @return promise of partition -> offset map
     */
    Promise<List<Checkpoint>> getAllCheckpoints(String consumerId);

    /**
     * Delete checkpoint for a consumer group.
     *
     * @param consumerId consumer identifier
     * @param partition partition number
     * @return promise completing when deleted
     */
    Promise<Void> deleteCheckpoint(String consumerId, int partition);

    /**
     * Get all consumer groups.
     *
     * @return promise of consumer group IDs
     */
    Promise<List<String>> getConsumerGroups();

    /**
     * Get lag information for a consumer.
     *
     * @param consumerId consumer identifier
     * @return promise of lag information per partition
     */
    Promise<List<PartitionLag>> getConsumerLag(String consumerId);

    /**
     * Checkpoint record.
     */
    record Checkpoint(
        String consumerId,
        int partition,
        long offset,
        long timestamp,
        String metadata
    ) {}

    /**
     * Partition lag information.
     */
    record PartitionLag(
        int partition,
        long currentOffset,
        long committedOffset,
        long lag,
        boolean isCaughtUp
    ) {
        /**
         * Calculate lag from current and committed offsets.
         */
        public static PartitionLag of(int partition, long current, long committed) {
            long lag = current - committed;
            return new PartitionLag(partition, current, committed, lag, lag == 0);
        }
    }
}
