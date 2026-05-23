package com.ghatana.kernel.interaction;

import com.ghatana.kernel.bridge.port.BridgeContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        eventsByEventId.put(eventKey(envelope.tenantId(), envelope.workspaceId(), envelope.eventId()), stored);
        return true;
    }

    @Override
    public Optional<ProductInteractionEventEnvelope<?>> get(BridgeContext context, String eventId) {
        StoredEvent stored = eventsByEventId.get(eventKey(context, eventId));
        return Optional.ofNullable(stored).map(e -> e.envelope);
    }

    @Override
    public boolean updateStatus(BridgeContext context, String eventId, ProductInteractionStatus status) {
        StoredEvent stored = eventsByEventId.get(eventKey(context, eventId));
        if (stored != null) {
            stored.status = status;
            return true;
        }
        return false;
    }

    @Override
    public boolean isDelivered(BridgeContext context, String eventId) {
        StoredEvent stored = eventsByEventId.get(eventKey(context, eventId));
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
    public List<ProductInteractionEventEnvelope<?>> getDlqEvents(BridgeContext context, String topic, int limit) {
        List<ProductInteractionEventEnvelope<?>> events = new ArrayList<>();
        for (StoredEvent event : dlqEvents) {
            if (matchesScope(context, event.envelope) && event.envelope.topic().equals(topic)) {
                events.add(event.envelope);
                if (events.size() >= limit) {
                    break;
                }
            }
        }
        return events;
    }

    @Override
    public List<ProductInteractionEventEnvelope<?>> getEventsForReplay(
            BridgeContext context,
            String topic,
            long fromTimestampMs,
            long toTimestampMs,
            int limit) {
        List<ProductInteractionEventEnvelope<?>> events = new ArrayList<>();
        for (StoredEvent event : eventsByEventId.values()) {
            if (!matchesScope(context, event.envelope) || !event.envelope.topic().equals(topic)) {
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
    public long deleteEventsBefore(BridgeContext context, long beforeTimestampMs) {
        long count = eventsByEventId.values().stream()
            .filter(e -> matchesScope(context, e.envelope))
            .filter(e -> e.timestampMs < beforeTimestampMs)
            .count();
        eventsByEventId.entrySet().removeIf(entry ->
                matchesScope(context, entry.getValue().envelope)
                        && entry.getValue().timestampMs < beforeTimestampMs);
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

    private static String eventKey(BridgeContext context, String eventId) {
        Objects.requireNonNull(context, "context must not be null");
        return eventKey(context.getTenantId(), requireWorkspace(context), eventId);
    }

    private static String eventKey(String tenantId, String workspaceId, String eventId) {
        return tenantId + "::" + workspaceId + "::" + eventId;
    }

    private static boolean matchesScope(BridgeContext context, ProductInteractionEventEnvelope<?> envelope) {
        Objects.requireNonNull(context, "context must not be null");
        return Objects.equals(context.getTenantId(), envelope.tenantId())
                && Objects.equals(requireWorkspace(context), envelope.workspaceId());
    }

    private static String requireWorkspace(BridgeContext context) {
        String workspaceId = context.getWorkspaceId();
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("context workspaceId must not be blank");
        }
        return workspaceId;
    }
}
