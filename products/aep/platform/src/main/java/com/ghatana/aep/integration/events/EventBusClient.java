package com.ghatana.aep.integration.events;

import io.activej.promise.Promise;
import java.util.function.Function;

/**
 * Client interface for publishing and subscribing to domain events.
 * 
 * <p>This abstraction allows services to communicate via events without
 * direct dependencies on each other, breaking circular dependency chains.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Publishing
 * eventBus.publish("tenant-1", new ExpertAnnotated(...));
 * 
 * // Subscribing
 * eventBus.subscribe("analytics-service", ExpertAnnotated.EVENT_TYPE, 
 *     event -> processAnnotation((ExpertAnnotated) event));
 * }</pre>
 * 
 * @doc.type interface
 * @doc.purpose Event bus abstraction for inter-service communication
 * @doc.layer infrastructure
 * @doc.pattern Port
 */
public interface EventBusClient {
    
    /**
     * Publishes an event to the event bus.
     * 
     * @param tenantId The tenant context for the event
     * @param event The event to publish (must be serializable)
     * @param <T> The event type
     * @return A promise that completes with the event ID when published
     */
    <T> Promise<String> publish(String tenantId, T event);
    
    /**
     * Publishes an event with explicit type specification.
     * 
     * @param tenantId The tenant context
     * @param eventType The event type for routing
     * @param aggregateId The aggregate/entity ID this event relates to
     * @param event The event payload
     * @param <T> The event type
     * @return A promise that completes with the event ID
     */
    <T> Promise<String> publish(String tenantId, String eventType, String aggregateId, T event);
    
    /**
     * Subscribes to events of a specific type.
     * 
     * @param subscriptionId Unique identifier for this subscription (for idempotent operations)
     * @param eventType The event type to subscribe to
     * @param handler The handler function that processes events
     * @return A promise that completes with the subscription when established
     */
    Promise<EventSubscription> subscribe(
        String subscriptionId,
        String eventType,
        Function<Object, Promise<Void>> handler
    );
    
    /**
     * Subscribes to events of a specific type with typed handler.
     * 
     * @param subscriptionId Unique identifier for this subscription
     * @param eventType The event type to subscribe to
     * @param eventClass The class of events to receive
     * @param handler The typed handler function
     * @param <T> The event type
     * @return A promise that completes with the subscription
     */
    <T> Promise<EventSubscription> subscribe(
        String subscriptionId,
        String eventType,
        Class<T> eventClass,
        Function<T, Promise<Void>> handler
    );
    
    /**
     * Subscribes to multiple event types.
     * 
     * @param subscriptionId Unique identifier for this subscription
     * @param eventTypes Array of event types to subscribe to
     * @param handler The handler function for all events
     * @return A promise that completes with the subscription
     */
    Promise<EventSubscription> subscribeMultiple(
        String subscriptionId,
        String[] eventTypes,
        Function<Object, Promise<Void>> handler
    );
    
    /**
     * Starts the event bus client (establishes connections).
     * 
     * @return A promise that completes when the client is ready
     */
    Promise<Void> start();
    
    /**
     * Stops the event bus client (closes connections gracefully).
     * 
     * @return A promise that completes when the client is stopped
     */
    Promise<Void> stop();
}
