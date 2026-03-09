package com.ghatana.datacloud.entity.event;

import io.activej.promise.Promise;

/**
 * Subscriber for domain events.
 *
 * <p><b>Purpose</b><br>
 * Handles domain events of a specific type. Subscribers can perform side effects,
 * update read models, trigger workflows, or send notifications.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * public class EntityCreatedHandler implements DomainEventSubscriber<EntityCreatedEvent> {
 *     @Override
 *     public Promise<Void> handle(EntityCreatedEvent event) {
 *         // Update search index
 *         // Send notification
 *         // Trigger workflow
 *         return Promise.complete();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Error Handling</b><br>
 * If the handle method returns a failed Promise, the event may be retried
 * depending on the publisher's retry policy.
 *
 * @param <T> the event type this subscriber handles
 * @see DomainEvent
 * @see DomainEventPublisher
 * @doc.type interface
 * @doc.purpose Domain event subscriber interface
 * @doc.layer domain
 * @doc.pattern Event Subscriber (DDD)
 */
@FunctionalInterface
public interface DomainEventSubscriber<T extends DomainEvent> {

    /**
     * Handles a domain event.
     *
     * @param event the event to handle (never null)
     * @return Promise completing when handling is done
     */
    Promise<Void> handle(T event);

    /**
     * Returns the subscriber name for logging and debugging.
     *
     * @return subscriber name
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
