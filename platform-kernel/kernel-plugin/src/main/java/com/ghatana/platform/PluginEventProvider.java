package com.ghatana.platform.plugin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Durable event provider for plugin interactions.
 *
 * <p>Provides persistent storage for plugin events enabling replay, DLQ management,
 * and idempotency checks. This is the durable pubsub component for the plugin broker.</p>
 *
 * @doc.type interface
 * @doc.purpose Durable storage for plugin events with replay and DLQ capabilities
 * @doc.layer kernel-plugin
 * @doc.pattern Repository
 */
public interface PluginEventProvider {

    /**
     * Stores a published plugin event.
     *
     * @param event the event to store
     * @return true if stored successfully
     */
    boolean store(PluginEvent event);

    /**
     * Retrieves an event by its ID.
     *
     * @param eventId the event ID
     * @return the event if found
     */
    Optional<PluginEvent> get(String eventId);

    /**
     * Retrieves events for a specific topic.
     *
     * @param topic the topic
     * @param limit maximum number of events to retrieve
     * @return list of events
     */
    List<PluginEvent> getByTopic(String topic, int limit);

    /**
     * Retrieves events for replay within a time range.
     *
     * @param topic the topic
     * @param fromTimestampMs start timestamp in milliseconds
     * @param toTimestampMs end timestamp in milliseconds
     * @param limit maximum number of events to retrieve
     * @return list of events for replay
     */
    List<PluginEvent> getEventsForReplay(String topic, long fromTimestampMs, long toTimestampMs, int limit);

    /**
     * Sends a failed event to the Dead Letter Queue.
     *
     * @param event the event to send to DLQ
     * @param reasonCode the failure reason
     * @return true if sent to DLQ successfully
     */
    boolean sendToDlq(PluginEvent event, String reasonCode);

    /**
     * Retrieves events from the Dead Letter Queue.
     *
     * @param topic the topic
     * @param limit maximum number of events to retrieve
     * @return list of DLQ events
     */
    List<PluginEvent> getDlqEvents(String topic, int limit);

    /**
     * Deletes events older than the specified timestamp (cleanup).
     *
     * @param beforeTimestampMs delete events older than this timestamp
     * @return number of events deleted
     */
    long deleteEventsBefore(long beforeTimestampMs);

    /**
     * Plugin event record.
     */
    record PluginEvent(
            String eventId,
            String topic,
            String sourcePluginId,
            Object payload,
            Instant publishedAt,
            String status,
            String reasonCode
    ) {
        public PluginEvent {
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("eventId must not be blank");
            }
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("topic must not be blank");
            }
        }
    }
}
