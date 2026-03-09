package com.ghatana.datacloud.entity.event;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Publisher for domain events.
 *
 * <p><b>Purpose</b><br>
 * Publishes domain events to registered subscribers. Supports both sync and async
 * event delivery. Implementations may use in-memory dispatch, message queues,
 * or event stores.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // In service after entity creation
 * EntityCreatedEvent event = new EntityCreatedEvent(tenantId, entity.getId(), entity.getCollectionName());
 * publisher.publish(event);
 * }</pre>
 *
 * <p><b>Integration</b><br>
 * The publisher can be wired to:
 * <ul>
 *   <li>In-memory event bus for same-JVM subscribers</li>
 *   <li>EventCloud for persistent event streams</li>
 *   <li>External message queues (Kafka, Redis Streams)</li>
 *   <li>Webhook dispatchers for HTTP notifications</li>
 * </ul>
 *
 * @see DomainEvent
 * @see DomainEventSubscriber
 * @doc.type interface
 * @doc.purpose Domain event publisher interface
 * @doc.layer domain
 * @doc.pattern Event Publisher (DDD)
 */
public interface DomainEventPublisher {

    /**
     * Publishes a domain event to all registered subscribers.
     *
     * @param event the event to publish (required)
     * @return Promise completing when event is dispatched (not necessarily processed)
     */
    Promise<Void> publish(DomainEvent event);

    /**
     * Publishes multiple domain events in order.
     *
     * @param events the events to publish (required)
     * @return Promise completing when all events are dispatched
     */
    Promise<Void> publishAll(List<DomainEvent> events);

    /**
     * Registers a subscriber for events of a specific type.
     *
     * @param eventType the event class to subscribe to
     * @param subscriber the subscriber to register
     * @param <T> the event type
     */
    <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventSubscriber<T> subscriber);

    /**
     * Unregisters a subscriber.
     *
     * @param eventType the event class
     * @param subscriber the subscriber to unregister
     * @param <T> the event type
     */
    <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventSubscriber<T> subscriber);
}
