package com.ghatana.kernel.interaction;

import java.util.List;
import java.util.Optional;

/**
 * Provider contract for persistent product interaction event storage.
 *
 * <p>The event provider enables event replay, DLQ management, and idempotency
 * by storing published events with their delivery status. Implementations can back
 * this with databases, message queues, or other persistent storage.</p>
 *
 * @doc.type interface
 * @doc.purpose Persistent storage contract for product interaction events
 * @doc.layer kernel
 * @doc.pattern Repository
 */
public interface ProductInteractionEventProvider {

    /**
     * Stores a published event with its initial status.
     *
     * @param envelope the event envelope
     * @param status the initial delivery status
     * @return true if stored successfully
     */
    boolean store(ProductInteractionEventEnvelope<?> envelope, ProductInteractionStatus status);

    /**
     * Retrieves an event by its event ID.
     *
     * @param eventId the event ID
     * @return the event envelope if found
     */
    Optional<ProductInteractionEventEnvelope<?>> get(String eventId);

    /**
     * Updates the delivery status of an event.
     *
     * @param eventId the event ID
     * @param status the new status
     * @return true if updated successfully
     */
    boolean updateStatus(String eventId, ProductInteractionStatus status);

    /**
     * Checks if an event has been previously processed (idempotency check).
     *
     * @param eventId the event ID
     * @return true if the event was already delivered successfully
     */
    boolean isDelivered(String eventId);

    /**
     * Sends a failed event to the Dead Letter Queue.
     *
     * @param envelope the event envelope
     * @param reasonCode the failure reason
     * @return true if sent to DLQ successfully
     */
    boolean sendToDlq(ProductInteractionEventEnvelope<?> envelope, String reasonCode);

    /**
     * Retrieves events from the Dead Letter Queue for a topic.
     *
     * @param topic the topic
     * @param limit maximum number of events to retrieve
     * @return list of DLQ events
     */
    List<ProductInteractionEventEnvelope<?>> getDlqEvents(String topic, int limit);

    /**
     * Retrieves events for replay within a time range.
     *
     * @param topic the topic
     * @param fromTimestampMs start timestamp in milliseconds
     * @param toTimestampMs end timestamp in milliseconds
     * @param limit maximum number of events to retrieve
     * @return list of events for replay
     */
    List<ProductInteractionEventEnvelope<?>> getEventsForReplay(String topic, long fromTimestampMs, long toTimestampMs, int limit);

    /**
     * Deletes events older than the specified timestamp (cleanup).
     *
     * @param beforeTimestampMs delete events older than this timestamp
     * @return number of events deleted
     */
    long deleteEventsBefore(long beforeTimestampMs);
}
