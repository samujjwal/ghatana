package com.ghatana.kernel.interaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default in-memory implementation of ProductInteractionEventProvider.
 *
 * <p>This implementation uses in-memory storage and is suitable for testing,
 * development, and simple production scenarios. For production use with
 * persistent storage, implement the ProductInteractionEventProvider interface
 * with a database or message queue backed implementation.</p>
 *
 * @doc.type class
 * @doc.purpose Default in-memory event provider for product interactions
 * @doc.layer kernel
 * @doc.pattern Repository
 */
public final class InMemoryProductInteractionEventProvider implements ProductInteractionEventProvider {

    private final Map<String, StoredEvent> eventsByEventId = new ConcurrentHashMap<>();
    private final List<StoredEvent> dlqEvents = new CopyOnWriteArrayList<>();

    @Override
    public boolean store(ProductInteractionEventEnvelope<?> envelope, ProductInteractionStatus status) {
        StoredEvent stored = new StoredEvent(envelope, status, System.currentTimeMillis());
        eventsByEventId.put(envelope.eventId(), stored);
        return true;
    }

    @Override
    public Optional<ProductInteractionEventEnvelope<?>> get(String eventId) {
        StoredEvent stored = eventsByEventId.get(eventId);
        return Optional.ofNullable(stored).map(e -> e.envelope);
    }

    @Override
    public boolean updateStatus(String eventId, ProductInteractionStatus status) {
        StoredEvent stored = eventsByEventId.get(eventId);
        if (stored != null) {
            stored.status = status;
            return true;
        }
        return false;
    }

    @Override
    public boolean isDelivered(String eventId) {
        StoredEvent stored = eventsByEventId.get(eventId);
        return stored != null && stored.status == ProductInteractionStatus.SUCCEEDED;
    }

    @Override
    public boolean sendToDlq(ProductInteractionEventEnvelope<?> envelope, String reasonCode) {
        StoredEvent dlqEvent = new StoredEvent(envelope, ProductInteractionStatus.BLOCKED, System.currentTimeMillis());
        dlqEvent.dlqReasonCode = reasonCode;
        dlqEvents.add(dlqEvent);
        return true;
    }

    @Override
    public List<ProductInteractionEventEnvelope<?>> getDlqEvents(String topic, int limit) {
        List<ProductInteractionEventEnvelope<?>> events = new ArrayList<>();
        for (StoredEvent event : dlqEvents) {
            if (event.envelope.topic().equals(topic)) {
                events.add(event.envelope);
                if (events.size() >= limit) {
                    break;
                }
            }
        }
        return events;
    }

    @Override
    public List<ProductInteractionEventEnvelope<?>> getEventsForReplay(String topic, long fromTimestampMs, long toTimestampMs, int limit) {
        List<ProductInteractionEventEnvelope<?>> events = new ArrayList<>();
        for (StoredEvent event : eventsByEventId.values()) {
            if (!event.envelope.topic().equals(topic)) {
                continue;
            }
            if (event.timestampMs < fromTimestampMs || event.timestampMs > toTimestampMs) {
                continue;
            }
            events.add(event.envelope);
            if (events.size() >= limit) {
                break;
            }
        }
        return events;
    }

    @Override
    public long deleteEventsBefore(long beforeTimestampMs) {
        long count = eventsByEventId.values().stream()
            .filter(e -> e.timestampMs < beforeTimestampMs)
            .count();
        eventsByEventId.entrySet().removeIf(entry -> entry.getValue().timestampMs < beforeTimestampMs);
        return count;
    }

    /**
     * Returns the number of stored events.
     *
     * @return event count
     */
    public int size() {
        return eventsByEventId.size();
    }

    /**
     * Returns the number of DLQ events.
     *
     * @return DLQ event count
     */
    public int dlqSize() {
        return dlqEvents.size();
    }

    /**
     * Clears all stored events.
     */
    public void clear() {
        eventsByEventId.clear();
        dlqEvents.clear();
    }

    private static class StoredEvent {
        final ProductInteractionEventEnvelope<?> envelope;
        ProductInteractionStatus status;
        final long timestampMs;
        String dlqReasonCode;

        StoredEvent(ProductInteractionEventEnvelope<?> envelope, ProductInteractionStatus status, long timestampMs) {
            this.envelope = envelope;
            this.status = status;
            this.timestampMs = timestampMs;
        }
    }
}
