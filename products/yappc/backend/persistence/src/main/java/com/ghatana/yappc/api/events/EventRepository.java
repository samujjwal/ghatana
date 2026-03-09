/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;

/**
 * Repository for persisting and querying domain events (event store).
 *
 * <p>Events are immutable once written. Append-only writes. Queries
 * support replay by aggregate and time-range scans.
 *
 * @doc.type interface
 * @doc.purpose Event store persistence port
 * @doc.layer domain
 * @doc.pattern Repository / Event Sourcing
 */
public interface EventRepository {

    /**
     * Appends an event to the store and inserts an outbox entry in the same
     * logical operation. Callers are responsible for managing the transaction.
     *
     * @param event       the domain event to append
     * @param sequenceNum monotonically increasing sequence for this aggregate
     * @return Promise completing with the stored event ID
     */
    Promise<String> append(DomainEvent event, long sequenceNum);

    /**
     * Returns all events for a given aggregate in sequence order.
     *
     * @param tenantId     tenant scope
     * @param aggregateId  aggregate identifier
     * @return Promise with ordered list of stored events
     */
    Promise<List<StoredEvent>> findByAggregate(String tenantId, String aggregateId);

    /**
     * Returns events of a specific type within a time window.
     *
     * @param tenantId  tenant scope
     * @param eventType e.g. {@code "canvas.node.created"}
     * @param from      start of window (inclusive)
     * @param to        end of window (inclusive)
     * @return Promise with matching stored events
     */
    Promise<List<StoredEvent>> findByTypeAndWindow(
            String tenantId, String eventType, Instant from, Instant to);

    /**
     * Fetches pending outbox entries (not yet published to downstream) up to
     * {@code limit} rows, ordered by {@code next_retry_at}.
     *
     * @param tenantId tenant scope
     * @param limit    max entries to return
     * @return Promise with pending outbox entries
     */
    Promise<List<OutboxEntry>> fetchPendingOutbox(String tenantId, int limit);

    /**
     * Marks an outbox entry as delivered.
     *
     * @param outboxId the outbox row ID
     * @return Promise completing when the row is updated
     */
    Promise<Void> markOutboxDelivered(String outboxId);

    /**
     * Marks an outbox entry as failed (increments attempt count, updates error).
     *
     * @param outboxId  the outbox row ID
     * @param error     error description
     * @param nextRetry when to retry
     * @return Promise completing when the row is updated
     */
    Promise<Void> markOutboxFailed(String outboxId, String error, Instant nextRetry);

    // -------------------------------------------------------------------------
    // Value objects returned by queries
    // -------------------------------------------------------------------------

    record StoredEvent(
            String eventId,
            String eventType,
            String aggregateId,
            String aggregateType,
            String tenantId,
            String userId,
            long sequenceNum,
            String payloadJson,
            Instant occurredAt,
            int schemaVersion) {}

    record OutboxEntry(
            String outboxId,
            String eventId,
            String tenantId,
            String eventType,
            String aggregateId,
            String payloadJson,
            int attempts) {}
}
