package com.ghatana.platform.cache.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghatana.platform.cache.DistributedCacheService.CacheBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.activej.async.function.AsyncFunction;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Publisher for cache invalidation events to notify other services.
 * @doc.layer platform
 * @doc.pattern Publisher
 *
 * Publishes cache invalidation events to a message bus (Kafka, RabbitMQ, or in-memory)
 * whenever cache operations occur (delete key, delete pattern, bulk delete).
 *
 * Event format:
 * {
 *   "eventId": "550e8400-e29b-41d4-a716-446655440000",
 *   "tenantId": "tenant:123",
 *   "operationType": "SINGLE_KEY_DELETE|PATTERN_DELETE|BULK_DELETE",
 *   "cacheKey": "cache:key:or:pattern",
 *   "keyCount": 1,
 *   "timestamp": "2026-04-05T10:30:00Z",
 *   "correlationId": "trace:id:from:context",
 *   "source": "TutorPutorContentCacheService|DataCloudQueryCacheService"
 * }
 *
 * Published events enable:
 * - Cross-service cache synchronization
 * - Cache coherency in distributed systems
 * - Audit trails for cache operations
 * - Metrics on cache invalidation patterns
 *
 * Thread-safe: Uses async/promise-based approach.
 */
public final class CacheInvalidationEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationEventPublisher.class);

    private final AsyncMessageBus messageBus;
    private final String serviceSource;
    private final String tenantId;

    /**
     * Creates a cache invalidation event publisher.
     *
     * @param messageBus async message bus for publishing events (Kafka, RabbitMQ, in-memory)
     * @param serviceSource name of service publishing events (e.g., "TutorPutorContentCacheService")
     * @param tenantId tenant context ID (null for platform events)
     */
    public CacheInvalidationEventPublisher(
            AsyncMessageBus messageBus,
            String serviceSource,
            String tenantId
    ) {
        this.messageBus = messageBus;
        this.serviceSource = serviceSource;
        this.tenantId = tenantId;
    }

    /**
     * Publishes single key deletion event asynchronously.
     *
     * Returns immediately; publication happens in background.
     * Failures are logged but not thrown (non-blocking).
     *
     * @param cacheKey the key being deleted
     * @param correlationId request correlation ID for tracing
     * @return Promise<Void> completed when event is published
     */
    public Promise<Void> publishSingleKeyDelete(String cacheKey, String correlationId) {
        CacheInvalidationEvent event = new CacheInvalidationEvent(
            UUID.randomUUID().toString(),
            tenantId,
            OperationType.SINGLE_KEY_DELETE,
            cacheKey,
            1,
            Instant.now().toString(),
            correlationId,
            serviceSource
        );

        return messageBus.publish("cache-invalidation-events", event)
            .thenApply(unused -> {
                log.info("Published cache invalidation event",
                    "eventId", event.eventId,
                    "operationType", event.operationType,
                    "cacheKey", event.cacheKey,
                    "correlationId", event.correlationId);
                return null;
            })
            .exceptionally(exception -> {
                log.warn("Failed to publish cache invalidation event", exception,
                    "cacheKey", cacheKey,
                    "correlationId", correlationId,
                    "error", exception.getMessage());
                return null;  // Non-blocking: don't throw
            });
    }

    /**
     * Publishes pattern-based deletion event asynchronously.
     *
     * WARNING: Pattern deletes can affect many keys.
     * Example: "learning-path:user:123:*" deletes all paths for user 123.
     *
     * @param pattern the deletion pattern (glob)
     * @param keyCount number of keys matched and deleted
     * @param correlationId request correlation ID for tracing
     * @return Promise<Void> completed when event is published
     */
    public Promise<Void> publishPatternDelete(String pattern, long keyCount, String correlationId) {
        CacheInvalidationEvent event = new CacheInvalidationEvent(
            UUID.randomUUID().toString(),
            tenantId,
            OperationType.PATTERN_DELETE,
            pattern,
            keyCount,
            Instant.now().toString(),
            correlationId,
            serviceSource
        );

        return messageBus.publish("cache-invalidation-events", event)
            .thenApply(unused -> {
                log.info("Published pattern cache invalidation event",
                    "eventId", event.eventId,
                    "operationType", event.operationType,
                    "pattern", event.cacheKey,
                    "keyCount", event.keyCount,
                    "correlationId", event.correlationId);
                return null;
            })
            .exceptionally(exception -> {
                log.warn("Failed to publish pattern invalidation event", exception,
                    "pattern", pattern,
                    "keyCount", keyCount,
                    "error", exception.getMessage());
                return null;  // Non-blocking: don't throw
            });
    }

    /**
     * Publishes bulk deletion event asynchronously.
     *
     * Used for large-scale cache clearing (e.g., tenant offboarding, schema migration).
     *
     * @param bulkOperationKey identifier for the bulk operation
     * @param keyCount total keys deleted
     * @param correlationId request correlation ID for tracing
     * @return Promise<Void> completed when event is published
     */
    public Promise<Void> publishBulkDelete(String bulkOperationKey, long keyCount, String correlationId) {
        CacheInvalidationEvent event = new CacheInvalidationEvent(
            UUID.randomUUID().toString(),
            tenantId,
            OperationType.BULK_DELETE,
            bulkOperationKey,
            keyCount,
            Instant.now().toString(),
            correlationId,
            serviceSource
        );

        return messageBus.publish("cache-invalidation-events", event)
            .thenApply(unused -> {
                log.info("Published bulk cache invalidation event",
                    "eventId", event.eventId,
                    "operationType", event.operationType,
                    "bulkOperationKey", event.cacheKey,
                    "keyCount", event.keyCount,
                    "correlationId", event.correlationId);
                return null;
            })
            .exceptionally(exception -> {
                log.warn("Failed to publish bulk invalidation event", exception,
                    "bulkOperationKey", bulkOperationKey,
                    "keyCount", keyCount,
                    "error", exception.getMessage());
                return null;  // Non-blocking: don't throw
            });
    }

    /**
     * Event payload for cache invalidation.
     */
    static class CacheInvalidationEvent {
        @JsonProperty
        String eventId;

        @JsonProperty
        String tenantId;

        @JsonProperty
        OperationType operationType;

        @JsonProperty
        String cacheKey;

        @JsonProperty
        long keyCount;

        @JsonProperty
        String timestamp;

        @JsonProperty
        String correlationId;

        @JsonProperty
        String source;

        CacheInvalidationEvent() {}

        CacheInvalidationEvent(
                String eventId,
                String tenantId,
                OperationType operationType,
                String cacheKey,
                long keyCount,
                String timestamp,
                String correlationId,
                String source
        ) {
            this.eventId = eventId;
            this.tenantId = tenantId;
            this.operationType = operationType;
            this.cacheKey = cacheKey;
            this.keyCount = keyCount;
            this.timestamp = timestamp;
            this.correlationId = correlationId;
            this.source = source;
        }
    }

    enum OperationType {
        SINGLE_KEY_DELETE,
        PATTERN_DELETE,
        BULK_DELETE
    }
}

/**
 * @doc.type interface
 * @doc.purpose Async message bus abstraction for publishing cache invalidation events.
 * @doc.layer platform
 * @doc.pattern Interface
 *
 * Implementations:
 * - KafkaAsyncMessageBus — for production (distributed, persistent)
 * - RabbitMQAsyncMessageBus — for enterprise (AMQP)
 * - InMemoryAsyncMessageBus — for testing/single-node deployments
 *
 * Thread-safe: All publish operations are asynchronous.
 */
interface AsyncMessageBus {
    /**
     * Publishes an event to a topic asynchronously.
     *
     * @param topic the message topic (e.g., "cache-invalidation-events")
     * @param event the event payload
     * @return Promise<Void> completed when event is published (or queued)
     */
    Promise<Void> publish(String topic, Object event);

    /**
     * Subscribes to events from a topic.
     *
     * @param topic the message topic
     * @param handler async function to handle received events
     * @return Promise<Void> subscription setup promise
     */
    Promise<Void> subscribe(String topic, AsyncFunction<Object, Void> handler);

    /**
     * Checks if the message bus is healthy and connected.
     *
     * @return Promise<Boolean> true if bus is operational
     */
    Promise<Boolean> isHealthy();

    /**
     * Closes the message bus and releases resources.
     *
     * @return Promise<Void> completion promise
     */
    Promise<Void> close();
}

/**
 * @doc.type class
 * @doc.purpose Subscriber for cache invalidation events to synchronize distributed caches.
 * @doc.layer platform
 * @doc.pattern Subscriber
 *
 * Listens for cache invalidation events from other services and applies them locally.
 * Ensures cache coherency in distributed deployments.
 *
 * Applies events based on operation type:
 * - SINGLE_KEY_DELETE: delete(cacheKey)
 * - PATTERN_DELETE: deletePattern(cacheKey)
 * - BULK_DELETE: deletePattern(bulkOperationKey)
 *
 * Thread-safe: Uses async/promise-based approach.
 */
class CacheInvalidationEventSubscriber {
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationEventSubscriber.class);

    private final AsyncMessageBus messageBus;
    private final CacheBackend cacheBackend;
    private final String serviceSource;

    /**
     * Creates a cache invalidation event subscriber.
     *
     * @param messageBus async message bus for subscribing to events
     * @param cacheBackend the cache backend to apply invalidations to
     * @param serviceSource name of this service (to avoid re-processing own events)
     */
    public CacheInvalidationEventSubscriber(
            AsyncMessageBus messageBus,
            CacheBackend cacheBackend,
            String serviceSource
    ) {
        this.messageBus = messageBus;
        this.cacheBackend = cacheBackend;
        this.serviceSource = serviceSource;
    }

    /**
     * Starts listening for cache invalidation events.
     *
     * Subscribes to "cache-invalidation-events" topic and applies changes locally.
     * Ignores events from this service to avoid duplicate processing.
     *
     * @return Promise<Void> subscription setup promise
     */
    public Promise<Void> start() {
        return messageBus.subscribe("cache-invalidation-events", event -> {
            if (!(event instanceof CacheInvalidationEventPublisher.CacheInvalidationEvent)) {
                return Promise.complete(null);
            }

            CacheInvalidationEventPublisher.CacheInvalidationEvent invalidationEvent =
                (CacheInvalidationEventPublisher.CacheInvalidationEvent) event;

            // Avoid re-processing our own events
            if (serviceSource.equals(invalidationEvent.source)) {
                return Promise.complete(null);
            }

            return applyInvalidationEvent(invalidationEvent);
        });
    }

    private Promise<Void> applyInvalidationEvent(
            CacheInvalidationEventPublisher.CacheInvalidationEvent event
    ) {
        return Promise.of(() -> {
            try {
                switch (event.operationType) {
                    case SINGLE_KEY_DELETE:
                        long deletedCount = cacheBackend.deleteKey(event.cacheKey);
                        log.info("Applied single key invalidation from event",
                            "eventId", event.eventId,
                            "cacheKey", event.cacheKey,
                            "source", event.source,
                            "deleted", deletedCount);
                        break;

                    case PATTERN_DELETE:
                        long patternDeleteCount = cacheBackend.deletePattern(event.cacheKey);
                        log.info("Applied pattern invalidation from event",
                            "eventId", event.eventId,
                            "pattern", event.cacheKey,
                            "source", event.source,
                            "deleted", patternDeleteCount);
                        break;

                    case BULK_DELETE:
                        long bulkDeleteCount = cacheBackend.deletePattern(event.cacheKey);
                        log.info("Applied bulk invalidation from event",
                            "eventId", event.eventId,
                            "bulkOperationKey", event.cacheKey,
                            "source", event.source,
                            "deleted", bulkDeleteCount);
                        break;
                }
                return null;
            } catch (Exception e) {
                log.warn("Failed to apply cache invalidation event", e,
                    "eventId", event.eventId,
                    "operationType", event.operationType,
                    "error", e.getMessage());
                return null;  // Non-blocking: don't throw
            }
        });
    }
}
