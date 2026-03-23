package com.ghatana.aep.event;

/**
 * EventCloud facade for AEP integration.
 *
 * <p>This interface wraps the core EventCloud functionality for use by
 * the AEP engine. It provides a simplified API for event storage and
 * retrieval operations.
 *
 * <p>The underlying implementation uses {@code com.ghatana.core.event.cloud.EventCloud}
 * from the platform/java/event-cloud module.
 *
 * @doc.type interface
 * @doc.purpose Simplified EventCloud facade for AEP
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
}
