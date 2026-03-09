package com.ghatana.datacloud.application.realtime;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ghatana.platform.observability.util.BlockingExecutors.blockingExecutor;

/**
 * WebSocket-based real-time execution monitoring.
 *
 * <p><b>Purpose</b><br>
 * Provides real-time workflow execution monitoring via WebSocket:
 * - Live execution tracking
 * - Status updates
 * - Error streaming
 * - Performance metrics
 * - Offline support with caching
 *
 * <p><b>Features</b><br>
 * - WebSocket connections
 * - Execution event streaming
 * - Offline event caching
 * - Sync when online
 * - Connection management
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RealtimeExecutionMonitor monitor = new RealtimeExecutionMonitor();
 *
 * // Subscribe to execution
 * monitor.subscribe(executionId, listener);
 *
 * // Stream status update
 * monitor.streamExecutionUpdate(executionId, update);
 *
 * // Get offline events
 * List<ExecutionEvent> events = monitor.getOfflineEvents();
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose Real-time execution monitoring
 * @doc.layer application
 * @doc.pattern Observer (Events)
 */
public class RealtimeExecutionMonitor {

    private static final Logger logger = LoggerFactory.getLogger(RealtimeExecutionMonitor.class);

    private final Map<String, ExecutionSubscription> subscriptions = new ConcurrentHashMap<>();
    private final List<ExecutionEvent> offlineEventCache = new CopyOnWriteArrayList<>();
    private final ExecutionStateManager stateManager = new ExecutionStateManager();
    private boolean isOnline = true;
    private static final int MAX_OFFLINE_CACHE = 10000;

    /**
     * Subscribe to execution updates.
     *
     * @param executionId the execution ID
     * @param listener the event listener
     */
    public void subscribe(String executionId, ExecutionListener listener) {
        if (executionId == null || executionId.isEmpty()) {
            throw new IllegalArgumentException("executionId cannot be null");
        }

        ExecutionSubscription subscription = new ExecutionSubscription(executionId, listener);
        subscriptions.put(executionId, subscription);

        logger.info("Subscribed to execution: {}", executionId);
    }

    /**
     * Unsubscribe from execution updates.
     *
     * @param executionId the execution ID
     */
    public void unsubscribe(String executionId) {
        if (subscriptions.remove(executionId) != null) {
            logger.info("Unsubscribed from execution: {}", executionId);
        }
    }

    /**
     * Stream execution update event.
     *
     * @param executionId the execution ID
     * @param update the execution update
     */
    public void streamExecutionUpdate(String executionId, ExecutionUpdate update) {
        if (executionId == null || update == null) {
            throw new IllegalArgumentException("executionId and update cannot be null");
        }

        ExecutionEvent event = new ExecutionEvent(
                UUID.randomUUID().toString(),
                executionId,
                update,
                Instant.now(),
                isOnline
        );

        // Update state
        stateManager.updateExecutionState(executionId, update);

        // Cache if offline
        if (!isOnline) {
            cacheOfflineEvent(event);
            logger.debug("Cached offline event for execution: {}", executionId);
            return;
        }

        // Broadcast if online
        broadcast(event);
    }

    /**
     * Stream error event.
     *
     * @param executionId the execution ID
     * @param error the error message
     * @param exception the exception
     */
    public void streamError(String executionId, String error, Throwable exception) {
        if (executionId == null || error == null) {
            throw new IllegalArgumentException("executionId and error cannot be null");
        }

        ExecutionError executionError = new ExecutionError(error,
                exception != null ? exception.getMessage() : null,
                exception != null ? exception.getClass().getName() : null);

        ExecutionUpdate update = new ExecutionUpdate(
                executionId,
                "ERROR",
                executionError,
                Instant.now()
        );

        streamExecutionUpdate(executionId, update);
        logger.error("Execution error: {} - {}", executionId, error);
    }

    /**
     * Broadcast event to all subscribers.
     *
     * @param event the event
     */
    private void broadcast(ExecutionEvent event) {
        ExecutionSubscription subscription = subscriptions.get(event.executionId());
        if (subscription != null) {
            try {
                subscription.listener().onExecutionEvent(event);
            } catch (Exception e) {
                logger.error("Error broadcasting event to execution: {}", event.executionId(), e);
                unsubscribe(event.executionId());
            }
        }
    }

    /**
     * Cache event for offline mode.
     *
     * @param event the event
     */
    private void cacheOfflineEvent(ExecutionEvent event) {
        offlineEventCache.add(event);

        // Cleanup if cache too large
        if (offlineEventCache.size() > MAX_OFFLINE_CACHE) {
            offlineEventCache.remove(0);
        }
    }

    /**
     * Get offline events and clear cache.
     *
     * @return list of cached events
     */
    public List<ExecutionEvent> getOfflineEvents() {
        List<ExecutionEvent> events = new ArrayList<>(offlineEventCache);
        offlineEventCache.clear();
        logger.debug("Retrieved {} offline events", events.size());
        return events;
    }

    /**
     * Sync offline events when going online.
     *
     * @return promise of sync completion
     */
    public Promise<Void> syncOfflineEvents() {
        return Promise.ofBlocking(blockingExecutor(), () -> {
            List<ExecutionEvent> events = getOfflineEvents();
            logger.info("Syncing {} offline events", events.size());

            for (ExecutionEvent event : events) {
                broadcast(event);
            }

            isOnline = true;
            logger.info("Offline events synced successfully");
            return null;
        });
    }

    /**
     * Set online/offline status.
     *
     * @param online true if online
     */
    public void setOnlineStatus(boolean online) {
        this.isOnline = online;
        logger.info("Online status changed: {}", online ? "ONLINE" : "OFFLINE");

        if (online && !offlineEventCache.isEmpty()) {
            syncOfflineEvents();
        }
    }

    /**
     * Get execution state.
     *
     * @param executionId the execution ID
     * @return execution state
     */
    public ExecutionState getExecutionState(String executionId) {
        return stateManager.getExecutionState(executionId);
    }

    /**
     * Get all active executions.
     *
     * @return list of active execution IDs
     */
    public List<String> getActiveExecutions() {
        return new ArrayList<>(subscriptions.keySet());
    }

    /**
     * Execution listener interface.
     */
    public interface ExecutionListener {
        void onExecutionEvent(ExecutionEvent event);
    }

    /**
     * Execution subscription.
     */
    private record ExecutionSubscription(String executionId, ExecutionListener listener) {}

    /**
     * Execution event.
     */
    public record ExecutionEvent(
            String eventId,
            String executionId,
            ExecutionUpdate update,
            Instant timestamp,
            boolean syncedOffline
    ) {}

    /**
     * Execution update.
     */
    public record ExecutionUpdate(
            String executionId,
            String status,
            Object data,
            Instant timestamp
    ) {}

    /**
     * Execution error.
     */
    public record ExecutionError(
            String message,
            String exceptionMessage,
            String exceptionType
    ) {}

    /**
     * Execution state.
     */
    public record ExecutionState(
            String executionId,
            String status,
            int completedNodes,
            int totalNodes,
            Instant startTime,
            Instant lastUpdate
    ) {}

    /**
     * Execution state manager.
     */
    private static class ExecutionStateManager {
        private final Map<String, ExecutionState> states = new ConcurrentHashMap<>();

        void updateExecutionState(String executionId, ExecutionUpdate update) {
            ExecutionState current = states.getOrDefault(executionId,
                    new ExecutionState(executionId, "RUNNING", 0, 0, Instant.now(), Instant.now()));

            int completed = current.completedNodes();
            if ("COMPLETED".equals(update.status())) {
                completed = current.totalNodes();
            } else if (update.data() instanceof Integer) {
                completed = (Integer) update.data();
            }

            ExecutionState newState = new ExecutionState(
                    executionId,
                    update.status(),
                    completed,
                    current.totalNodes(),
                    current.startTime(),
                    Instant.now()
            );

            states.put(executionId, newState);
        }

        ExecutionState getExecutionState(String executionId) {
            return states.get(executionId);
        }
    }
}

