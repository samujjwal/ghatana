/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.event;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;

/**
 * Service interface for event durability guarantees.
 *
 * <p>Provides operations for ensuring event durability including:
 * <ul>
 *   <li>Durability acknowledgments (write-ahead persistence)</li>
 *   <li>Checkpoint management for CDC</li>
 *   <li>Event replay from checkpoints</li>
 *   <li>Durability level configuration</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Event durability guarantees for CDC and replay
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface EventDurabilityService {

    /**
     * Durability levels for event writes.
     *
     * <p>Each level carries an explicit {@link #strength()} value that is
     * independent of enum declaration order, making {@code meetsLevel()} and
     * any future comparisons immune to accidental enum reordering.
     */
    enum DurabilityLevel {
        /** Fire-and-forget, no durability guarantee */
        NONE(0),
        /** Acknowledged when written to leader */
        LEADER_ACK(1),
        /** Acknowledged when replicated to majority */
        MAJORITY_ACK(2),
        /** Acknowledged when replicated to all nodes */
        ALL_ACK(3),
        /** Acknowledged when fsynced to disk */
        FSYNC_ACK(4);

        private final int strength;

        DurabilityLevel(int strength) {
            this.strength = strength;
        }

        /**
         * Explicit numeric strength of this level.
         * Higher values require stronger guarantees.
         * This value is stable across enum reordering.
         *
         * @return non-negative strength value
         */
        public int strength() {
            return strength;
        }
    }

    /**
     * Write event with durability guarantee.
     *
     * @param event the event to write
     * @param level durability level required
     * @return promise completing when durability guarantee met
     */
    Promise<DurabilityResult> writeWithDurability(Event event, DurabilityLevel level);

    /**
     * Acknowledge event durability (used by downstream consumers).
     *
     * @param eventId event identifier
     * @param consumerId consumer acknowledging
     * @return promise completing when acknowledged
     */
    Promise<Void> acknowledgeDurability(String eventId, String consumerId);

    /**
     * Get current checkpoint for a consumer.
     *
     * @param consumerId consumer identifier
     * @return promise of checkpoint offset
     */
    Promise<Long> getCheckpoint(String consumerId);

    /**
     * Save checkpoint for a consumer.
     *
     * @param consumerId consumer identifier
     * @param offset event offset to checkpoint
     * @return promise completing when checkpoint saved
     */
    Promise<Void> saveCheckpoint(String consumerId, long offset);

    /**
     * Get durability status for an event.
     *
     * @param eventId event identifier
     * @return promise of durability status
     */
    Promise<DurabilityStatus> getDurabilityStatus(String eventId);

    /**
     * Wait for durability guarantee with timeout.
     *
     * @param eventId event identifier
     * @param level durability level to wait for
     * @param timeout maximum wait time
     * @return promise of true if durability achieved
     */
    Promise<Boolean> waitForDurability(String eventId, DurabilityLevel level, Duration timeout);

    /**
     * Result of durability write operation.
     */
    record DurabilityResult(
        String eventId,
        long offset,
        DurabilityLevel achievedLevel,
        long fsyncLatencyMs,
        long replicationLatencyMs,
        boolean successful
    ) {
        /**
         * Check if durability was successful.
         */
        public boolean isSuccessful() {
            return successful;
        }

        /**
         * Check if achieved level meets required level.
         *
         * <p>Uses explicit {@link DurabilityLevel#strength()} comparison rather than
         * enum ordinal, making this method immune to enum declaration order changes.
         */
        public boolean meetsLevel(DurabilityLevel required) {
            return achievedLevel.strength() >= required.strength();
        }
    }

    /**
     * Durability status for an event.
     */
    record DurabilityStatus(
        String eventId,
        DurabilityLevel currentLevel,
        int replicaCount,
        int requiredReplicaCount,
        boolean fsynced,
        List<String> acknowledgedConsumers
    ) {
        /**
         * Check if fully durable (fsynced and replicated).
         */
        public boolean isFullyDurable() {
            return fsynced && replicaCount >= requiredReplicaCount;
        }
    }

    /**
     * Event data structure for durability operations.
     */
    record Event(
        String id,
        String type,
        String tenantId,
        byte[] payload,
        long timestamp
    ) {}
}
