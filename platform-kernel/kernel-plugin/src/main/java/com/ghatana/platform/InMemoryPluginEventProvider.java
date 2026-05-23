package com.ghatana.platform.plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementation of PluginEventProvider for testing and development.
 *
 * <p>This implementation uses in-memory storage and is suitable for testing,
 * development, and simple production scenarios. For production use with
 * persistent storage, implement the PluginEventProvider interface with a
 * database or message queue backed implementation.</p>
 *
 * @doc.type class
 * @doc.purpose Default in-memory event provider for plugin interactions
 * @doc.layer kernel-plugin
 * @doc.pattern Repository
 */
public final class InMemoryPluginEventProvider implements PluginEventProvider {

    private final Map<String, PluginEvent> eventsByEventId = new ConcurrentHashMap<>();
    private final List<PluginEvent> dlqEvents = new CopyOnWriteArrayList<>();

    @Override
    public boolean store(PluginEvent event) {
        eventsByEventId.put(event.eventId(), event);
        return true;
    }

    @Override
    public Optional<PluginEvent> get(String eventId) {
        return Optional.ofNullable(eventsByEventId.get(eventId));
    }

    @Override
    public List<PluginEvent> getByTopic(String topic, int limit) {
        List<PluginEvent> events = new ArrayList<>();
        for (PluginEvent event : eventsByEventId.values()) {
            if (event.topic().equals(topic)) {
                events.add(event);
                if (events.size() >= limit) {
                    break;
                }
            }
        }
        return events;
    }

    @Override
    public List<PluginEvent> getEventsForReplay(String topic, long fromTimestampMs, long toTimestampMs, int limit) {
        List<PluginEvent> events = new ArrayList<>();
        for (PluginEvent event : eventsByEventId.values()) {
            if (!event.topic().equals(topic)) {
                continue;
            }
            long eventTimestampMs = event.publishedAt().toEpochMilli();
            if (eventTimestampMs < fromTimestampMs || eventTimestampMs > toTimestampMs) {
                continue;
            }
            events.add(event);
            if (events.size() >= limit) {
                break;
            }
        }
        return events;
    }

    @Override
    public boolean sendToDlq(PluginEvent event, String reasonCode) {
        PluginEvent dlqEvent = new PluginEvent(
                event.eventId(),
                event.topic(),
                event.sourcePluginId(),
                event.payload(),
                event.publishedAt(),
                "BLOCKED",
                reasonCode
        );
        dlqEvents.add(dlqEvent);
        return true;
    }

    @Override
    public List<PluginEvent> getDlqEvents(String topic, int limit) {
        List<PluginEvent> events = new ArrayList<>();
        for (PluginEvent event : dlqEvents) {
            if (event.topic().equals(topic)) {
                events.add(event);
                if (events.size() >= limit) {
                    break;
                }
            }
        }
        return events;
    }

    @Override
    public long deleteEventsBefore(long beforeTimestampMs) {
        long count = eventsByEventId.values().stream()
                .filter(e -> e.publishedAt().toEpochMilli() < beforeTimestampMs)
                .count();
        eventsByEventId.entrySet().removeIf(entry ->
                entry.getValue().publishedAt().toEpochMilli() < beforeTimestampMs);
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
}
