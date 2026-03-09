package com.ghatana.datacloud.infrastructure.event;

import com.ghatana.datacloud.entity.event.DomainEvent;
import com.ghatana.datacloud.entity.event.DomainEventPublisher;
import com.ghatana.datacloud.entity.event.DomainEventSubscriber;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementation of DomainEventPublisher.
 *
 * <p><b>Purpose</b><br>
 * Provides synchronous in-memory event dispatch for same-JVM subscribers.
 * Suitable for single-node deployments and testing.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * InMemoryDomainEventPublisher publisher = new InMemoryDomainEventPublisher();
 *
 * // Subscribe to entity events
 * publisher.subscribe(EntityCreatedEvent.class, event -> {
 *     logger.info("Entity created: {}", event.getEntityId());
 *     return Promise.complete();
 * });
 *
 * // Publish event
 * publisher.publish(new EntityCreatedEvent(...));
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Uses ConcurrentHashMap and CopyOnWriteArrayList.
 *
 * <p><b>Limitations</b><br>
 * <ul>
 *   <li>Events are not persisted</li>
 *   <li>No retry on failure</li>
 *   <li>No distributed dispatch</li>
 * </ul>
 *
 * @see DomainEventPublisher
 * @doc.type class
 * @doc.purpose In-memory domain event publisher
 * @doc.layer infrastructure
 * @doc.pattern Event Publisher (DDD)
 */
public class InMemoryDomainEventPublisher implements DomainEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryDomainEventPublisher.class);

    private final Map<Class<? extends DomainEvent>, List<DomainEventSubscriber<?>>> subscribers =
            new ConcurrentHashMap<>();

    @Override
    public Promise<Void> publish(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        logger.debug("Publishing event: {} (id={})", event.getEventType(), event.getEventId());

        List<Promise<Void>> handlerPromises = new ArrayList<>();

        // Dispatch to exact type subscribers
        List<DomainEventSubscriber<?>> exactSubscribers = subscribers.get(event.getClass());
        if (exactSubscribers != null) {
            for (DomainEventSubscriber<?> subscriber : exactSubscribers) {
                handlerPromises.add(invokeSubscriber(subscriber, event));
            }
        }

        // Dispatch to parent type subscribers (e.g., EntityEvent for EntityCreatedEvent)
        for (Map.Entry<Class<? extends DomainEvent>, List<DomainEventSubscriber<?>>> entry : subscribers.entrySet()) {
            if (entry.getKey().isAssignableFrom(event.getClass()) && !entry.getKey().equals(event.getClass())) {
                for (DomainEventSubscriber<?> subscriber : entry.getValue()) {
                    handlerPromises.add(invokeSubscriber(subscriber, event));
                }
            }
        }

        if (handlerPromises.isEmpty()) {
            logger.trace("No subscribers for event: {}", event.getEventType());
            return Promise.complete();
        }

        return Promises.all(handlerPromises)
                .mapException(e -> {
                    logger.error("Error publishing event {}: {}", 
                            event.getEventId(), e.getMessage(), e);
                    return e;
                })
                .map(results -> (Void) null);
    }

    @Override
    public Promise<Void> publishAll(List<DomainEvent> events) {
        Objects.requireNonNull(events, "events must not be null");

        if (events.isEmpty()) {
            return Promise.complete();
        }

        List<Promise<Void>> publishPromises = new ArrayList<>();
        for (DomainEvent event : events) {
            publishPromises.add(publish(event));
        }

        return Promises.all(publishPromises).map(results -> null);
    }

    @Override
    public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventSubscriber<T> subscriber) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(subscriber, "subscriber must not be null");

        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(subscriber);
        logger.info("Subscribed {} to {}", subscriber.getName(), eventType.getSimpleName());
    }

    @Override
    public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventSubscriber<T> subscriber) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(subscriber, "subscriber must not be null");

        List<DomainEventSubscriber<?>> eventSubscribers = subscribers.get(eventType);
        if (eventSubscribers != null) {
            eventSubscribers.remove(subscriber);
            logger.info("Unsubscribed {} from {}", subscriber.getName(), eventType.getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends DomainEvent> Promise<Void> invokeSubscriber(DomainEventSubscriber<?> subscriber, T event) {
        try {
            DomainEventSubscriber<T> typedSubscriber = (DomainEventSubscriber<T>) subscriber;
            return typedSubscriber.handle(event)
                    .whenException(e -> logger.error("Subscriber {} failed to handle event {}: {}",
                            subscriber.getName(), event.getEventId(), e.getMessage(), e));
        } catch (Exception e) {
            logger.error("Error invoking subscriber {} for event {}: {}",
                    subscriber.getName(), event.getEventId(), e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    /**
     * Returns the number of subscribers for a specific event type.
     *
     * @param eventType the event type
     * @return subscriber count
     */
    public int getSubscriberCount(Class<? extends DomainEvent> eventType) {
        List<DomainEventSubscriber<?>> eventSubscribers = subscribers.get(eventType);
        return eventSubscribers != null ? eventSubscribers.size() : 0;
    }

    /**
     * Clears all subscribers. Primarily for testing.
     */
    public void clearAllSubscribers() {
        subscribers.clear();
        logger.info("Cleared all event subscribers");
    }
}
