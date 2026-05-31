package com.ghatana.aep.event;

import java.util.List;
import java.util.Map;

/**
 * EventCloud facade for AEP integration (P4-07).
 *
 * <p>This interface wraps the core EventCloud functionality for use by
 * the AEP engine. It provides a simplified API for event storage and
 * retrieval operations with explicit replay-safe checkpoint support.
 *
 * <p>P4-07: Makes EventCloud persistence bridge explicit and replay-safe:
 * <ul>
 *   <li>Checkpoint management for durable state capture</li>
 *   <li>Replay support with idempotency guarantees</li>
 *   <li>Compensation strategy for rollback</li>
 *   <li>Replay mode (dry-run vs replay-with-side-effects)</li>
 * </ul>
 *
 * <p>The underlying implementation uses {@code com.ghatana.core.event.cloud.EventCloud}
 * from the platform/java/event-cloud module.
 *
 * @doc.type interface
 * @doc.purpose Simplified EventCloud facade for AEP with replay-safe checkpoint support
 * @doc.layer platform
 * @doc.pattern Facade
 * @since 1.0.0
 */
public interface EventCloud {

    /**
     * Append an event to the event cloud.
     *
     * @param tenantId tenant identifier
     * @param eventType event type
     * @param payload event payload
     * @return event ID
     */
    String append(String tenantId, String eventType, byte[] payload);

    /**
     * Subscribe to events of a specific type.
     *
     * @param tenantId tenant identifier
     * @param eventType event type to subscribe to
     * @param handler event handler
     * @return subscription handle
     */
    Subscription subscribe(String tenantId, String eventType, EventHandler handler);

    // ==================== P4-07: Checkpoint/Replay Support ====================

    /**
     * Create a checkpoint at the current position.
     *
     * @param tenantId tenant identifier
     * @param checkpointId checkpoint identifier
     * @param metadata checkpoint metadata
     * @return true if checkpoint created successfully
     */
    boolean createCheckpoint(String tenantId, String checkpointId, Map<String, Object> metadata);

    /**
     * Read a checkpoint by ID.
     *
     * @param tenantId tenant identifier
     * @param checkpointId checkpoint identifier
     * @return checkpoint metadata, or null if not found
     */
    Map<String, Object> readCheckpoint(String tenantId, String checkpointId);

    /**
     * Delete a checkpoint.
     *
     * @param tenantId tenant identifier
     * @param checkpointId checkpoint identifier
     * @return true if checkpoint deleted successfully
     */
    boolean deleteCheckpoint(String tenantId, String checkpointId);

    /**
     * List all checkpoints for a tenant.
     *
     * @param tenantId tenant identifier
     * @return list of checkpoint IDs with metadata
     */
    List<CheckpointInfo> listCheckpoints(String tenantId);

    /**
     * Replay events from a checkpoint.
     *
     * @param tenantId tenant identifier
     * @param checkpointId checkpoint identifier
     * @param mode replay mode (DRY_RUN or REPLAY_WITH_SIDE_EFFECTS)
     * @param handler event handler for replayed events
     * @return replay statistics
     */
    ReplayStatistics replay(String tenantId, String checkpointId, ReplayMode mode, EventHandler handler);

    /**
     * Event handler interface.
     */
    @FunctionalInterface
    interface EventHandler {
        void handle(String eventId, String eventType, byte[] payload);
    }

    /**
     * Subscription handle.
     */
    interface Subscription {
        void cancel();
        boolean isCancelled();
    }

    // ==================== Supporting Types ====================

    /**
     * Checkpoint information.
     */
    record CheckpointInfo(
        String checkpointId,
        long offset,
        Map<String, Object> metadata
    ) {}

    /**
     * Replay statistics.
     */
    record ReplayStatistics(
        int eventsProcessed,
        int eventsMatched,
        long durationMs
    ) {}

    /**
     * Replay mode.
     */
    enum ReplayMode {
        DRY_RUN,                  // Evaluate without side effects
        REPLAY_WITH_SIDE_EFFECTS   // Full replay with side effects
    }
}
