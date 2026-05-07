/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.event;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;

/**
 * Service for event replay operations.
 *
 * <p>Provides replay capabilities for CDC and recovery scenarios.
 *
 * @doc.type interface
 * @doc.purpose Event replay for CDC and recovery
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface EventReplayService {

    /**
     * Replay events from a specific offset.
     *
     * @param consumerId consumer identifier
     * @param fromOffset starting offset (inclusive)
     * @param handler callback for each replayed event
     * @return promise completing when replay finished
     */
    Promise<ReplayResult> replayFromOffset(String consumerId, long fromOffset, EventHandler handler);

    /**
     * Replay events from a checkpoint.
     *
     * @param consumerId consumer identifier
     * @param handler callback for each replayed event
     * @return promise completing when replay finished
     */
    Promise<ReplayResult> replayFromCheckpoint(String consumerId, EventHandler handler);

    /**
     * Replay events for a specific time range.
     *
     * @param startTime start timestamp (inclusive)
     * @param endTime end timestamp (exclusive)
     * @param handler callback for each replayed event
     * @return promise completing when replay finished
     */
    Promise<ReplayResult> replayForTimeRange(long startTime, long endTime, EventHandler handler);

    /**
     * Replay events for a specific tenant.
     *
     * @param tenantId tenant identifier
     * @param fromOffset starting offset
     * @param handler callback for each replayed event
     * @return promise completing when replay finished
     */
    Promise<ReplayResult> replayForTenant(String tenantId, long fromOffset, EventHandler handler);

    /**
     * Get replay status for a consumer.
     *
     * @param consumerId consumer identifier
     * @return promise of replay status
     */
    Promise<ReplayStatus> getReplayStatus(String consumerId);

    /**
     * Pause an ongoing replay.
     *
     * @param consumerId consumer identifier
     * @return promise completing when paused
     */
    Promise<Void> pauseReplay(String consumerId);

    /**
     * Resume a paused replay.
     *
     * @param consumerId consumer identifier
     * @return promise completing when resumed
     */
    Promise<Void> resumeReplay(String consumerId);

    /**
     * Cancel an ongoing replay.
     *
     * @param consumerId consumer identifier
     * @return promise completing when cancelled
     */
    Promise<Void> cancelReplay(String consumerId);

    /**
     * Event handler for replay operations.
     */
    @FunctionalInterface
    interface EventHandler {
        /**
         * Handle a replayed event.
         *
         * @param event the event
         * @return promise completing when handled
         */
        Promise<Void> handle(ReplayedEvent event);
    }

    /**
     * Replayed event data.
     */
    record ReplayedEvent(
        String id,
        String type,
        String tenantId,
        long offset,
        long timestamp,
        byte[] payload,
        int replayCount
    ) {
        /**
         * Check if this is a first-time replay.
         */
        public boolean isFirstReplay() {
            return replayCount == 1;
        }
    }

    /**
     * Result of a replay operation.
     */
    record ReplayResult(
        String consumerId,
        long startOffset,
        long endOffset,
        long eventsReplayed,
        long eventsProcessed,
        long eventsFailed,
        Duration duration,
        boolean completed,
        List<ReplayError> errors
    ) {
        /**
         * Check if replay completed successfully.
         */
        public boolean isSuccessful() {
            return completed && eventsFailed == 0;
        }

        /**
         * Get success rate.
         */
        public double successRate() {
            if (eventsReplayed == 0) return 1.0;
            return (double) eventsProcessed / eventsReplayed;
        }
    }

    /**
     * Replay error record.
     */
    record ReplayError(
        long offset,
        String eventId,
        String errorType,
        String errorMessage,
        boolean retryable
    ) {}

    /**
     * Replay status information.
     */
    record ReplayStatus(
        String consumerId,
        ReplayState state,
        long currentOffset,
        long targetOffset,
        long eventsReplayed,
        double progressPercent,
        Duration elapsedTime,
        Duration estimatedRemainingTime
    ) {
        /**
         * Calculate progress percentage.
         */
        public double calculateProgress() {
            if (targetOffset == 0) return 0.0;
            return (double) currentOffset / targetOffset * 100;
        }
    }

    /**
     * Replay state enum.
     */
    enum ReplayState {
        IDLE,
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
