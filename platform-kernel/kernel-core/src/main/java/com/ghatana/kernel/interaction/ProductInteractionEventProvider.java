package com.ghatana.kernel.interaction;

import com.ghatana.kernel.bridge.port.BridgeContext;

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
     * Retrieves an event by its event ID within a tenant/workspace scope.
     *
     * @param context trusted tenant/workspace context
     * @param eventId the event ID
     * @return the event envelope if found
     */
    Optional<ProductInteractionEventEnvelope<?>> get(BridgeContext context, String eventId);

    /**
     * Updates the delivery status of an event within a tenant/workspace scope.
     *
     * @param context trusted tenant/workspace context
     * @param eventId the event ID
     * @param status the new status
     * @return true if updated successfully
     */
    boolean updateStatus(BridgeContext context, String eventId, ProductInteractionStatus status);

    /**
     * Checks if an event has been previously processed within a tenant/workspace scope.
     *
     * @param context trusted tenant/workspace context
     * @param eventId the event ID
     * @return true if the event was already delivered successfully
     */
    boolean isDelivered(BridgeContext context, String eventId);

    /**
     * Sends a failed event to the Dead Letter Queue.
     *
     * @param envelope the event envelope
     * @param reasonCode the failure reason
     * @return true if sent to DLQ successfully
     */
    boolean sendToDlq(ProductInteractionEventEnvelope<?> envelope, String reasonCode);

    /**
     * Retrieves events from the Dead Letter Queue for a tenant/workspace scoped topic.
     *
     * @param context trusted tenant/workspace context
     * @param topic the topic
     * @param limit maximum number of events to retrieve
     * @return list of DLQ events
     */
    List<ProductInteractionEventEnvelope<?>> getDlqEvents(BridgeContext context, String topic, int limit);

    /**
     * Retrieves events for replay within a tenant/workspace scoped time range.
     *
     * @param context trusted tenant/workspace context
     * @param topic the topic
     * @param fromTimestampMs start timestamp in milliseconds
     * @param toTimestampMs end timestamp in milliseconds
     * @param limit maximum number of events to retrieve
     * @return list of events for replay
     */
    List<ProductInteractionEventEnvelope<?>> getEventsForReplay(
            BridgeContext context,
            String topic,
            long fromTimestampMs,
            long toTimestampMs,
            int limit);

    /**
     * Deletes tenant/workspace scoped events older than the specified timestamp (cleanup).
     *
     * @param context trusted tenant/workspace context
     * @param beforeTimestampMs delete events older than this timestamp
     * @return number of events deleted
     */
    long deleteEventsBefore(BridgeContext context, long beforeTimestampMs);
}
