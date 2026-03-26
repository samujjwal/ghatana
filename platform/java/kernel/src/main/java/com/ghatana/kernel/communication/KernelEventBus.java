package com.ghatana.kernel.communication;

import java.util.function.Consumer;

/**
 * Event bus for kernel-wide event publishing and subscription.
 *
 * <p>Provides asynchronous event-driven communication between kernel
 * modules and components with support for filtering and routing.</p>
 *
 * @doc.type interface
 * @doc.purpose Event-driven communication infrastructure
 * @doc.layer core
 * @doc.pattern Publisher-Subscriber
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface KernelEventBus {

    /**
     * Publishes an event to all subscribers.
     *
     * @param event the event to publish
     */
    void publishEvent(Event event);

    /**
     * Subscribes to events of a specific type.
     *
     * @param eventType the event type to subscribe to
     * @param handler the event handler
     * @return subscription handle for unsubscribing
     */
    Subscription subscribe(EventType eventType, EventHandler handler);

    /**
     * Subscribes to all events matching a filter.
     *
     * @param filter the event filter
     * @param handler the event handler
     * @return subscription handle
     */
    Subscription subscribe(EventFilter filter, EventHandler handler);

    /**
     * Unsubscribes from events.
     *
     * @param subscription the subscription to cancel
     */
    void unsubscribe(Subscription subscription);

    /**
     * Publishes an event asynchronously.
     *
     * @param event the event to publish
     * @return promise that completes when event is published
     */
    io.activej.promise.Promise<Void> publishEventAsync(Event event);

    /**
     * Represents an event.
     */
    interface Event {
        String getEventId();
        EventType getEventType();
        String getSource();
        Object getPayload();
        long getTimestamp();
        java.util.Map<String, String> getMetadata();
    }

    /**
     * Event type enumeration.
     */
    enum EventType {
        MODULE_STARTED,
        MODULE_STOPPED,
        CAPABILITY_REGISTERED,
        SECURITY_EVENT,
        AUDIT_EVENT,
        TELEMETRY_EVENT,
        CUSTOM
    }

    /**
     * Event handler functional interface.
     */
    @FunctionalInterface
    interface EventHandler extends Consumer<Event> {
        void accept(Event event);
    }

    /**
     * Event filter for selective subscription.
     */
    @FunctionalInterface
    interface EventFilter {
        boolean matches(Event event);
    }

    /**
     * Subscription handle for managing subscriptions.
     */
    interface Subscription {
        String getSubscriptionId();
        EventType getEventType();
        boolean isActive();
        void cancel();
    }
}
