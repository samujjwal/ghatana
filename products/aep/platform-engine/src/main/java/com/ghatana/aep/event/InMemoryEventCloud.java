package com.ghatana.aep.event;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory EventCloud implementation for testing and development.
 *
 * <p>This implementation stores events in memory and is suitable for:
 * <ul>
 *   <li>Unit testing</li>
 *   <li>Integration testing</li>
 *   <li>Development environments</li>
 * </ul>
 *
 * <p>For production use, configure a durable EventCloud implementation
 * such as PostgresEventCloudAdapter or KafkaEventCloudAdapter.
 *
 * @doc.type class
 * @doc.purpose In-memory EventCloud for testing and development
 * @doc.layer product
 * @doc.pattern Adapter
 * @since 1.0.0
 */
public class InMemoryEventCloud implements EventCloud {

    private final Map<String, List<StoredEvent>> eventsByTenant = new ConcurrentHashMap<>();
    private final List<SubscriptionEntry> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicLong eventCounter = new AtomicLong(0);

    @Override
    public String append(String tenantId, String eventType, byte[] payload) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(payload, "payload required");

        String eventId = tenantId + "-" + eventCounter.incrementAndGet();
        StoredEvent event = new StoredEvent(eventId, eventType, payload, System.currentTimeMillis());

        eventsByTenant.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>()).add(event);

        // Notify matching subscribers
        for (SubscriptionEntry sub : subscriptions) {
            if (!sub.cancelled && sub.tenantId.equals(tenantId) && sub.eventType.equals(eventType)) {
                try {
                    sub.handler.handle(eventId, eventType, payload);
                } catch (Exception e) {
                    // Log and continue — don't let one subscriber failure block others
                }
            }
        }

        return eventId;
    }

    @Override
    public Subscription subscribe(String tenantId, String eventType, EventHandler handler) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(handler, "handler required");

        SubscriptionEntry entry = new SubscriptionEntry(tenantId, eventType, handler);
        subscriptions.add(entry);

        return new Subscription() {
            @Override
            public void cancel() {
                entry.cancelled = true;
                subscriptions.remove(entry);
            }

            @Override
            public boolean isCancelled() {
                return entry.cancelled;
            }
        };
    }

    /**
     * Get all events for a tenant (for testing).
     *
     * @param tenantId tenant identifier
     * @return list of stored events, or empty list if no events
     */
    public List<StoredEvent> getEvents(String tenantId) {
        return List.copyOf(eventsByTenant.getOrDefault(tenantId, List.of()));
    }

    /**
     * Clear all events (for testing).
     */
    public void clear() {
        eventsByTenant.clear();
        subscriptions.clear();
        eventCounter.set(0);
    }

    /**
     * Stored event record.
     *
     * @param eventId   unique event identifier
     * @param eventType event type string
     * @param payload   raw event payload
     * @param timestamp epoch millis when appended
     */
    public record StoredEvent(String eventId, String eventType, byte[] payload, long timestamp) {}

    private static class SubscriptionEntry {
        final String tenantId;
        final String eventType;
        final EventHandler handler;
        volatile boolean cancelled;

        SubscriptionEntry(String tenantId, String eventType, EventHandler handler) {
            this.tenantId = tenantId;
            this.eventType = eventType;
            this.handler = handler;
        }
    }
}
