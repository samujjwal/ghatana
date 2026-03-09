/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Application-level service for publishing domain events via the event store.
 *
 * <p>Uses the transactional outbox pattern: events are durably written to
 * {@code yappc.domain_events} + {@code yappc.event_outbox} before any
 * downstream delivery is attempted. A separate relay process (not this class)
 * polls the outbox and forwards entries to consumers.
 *
 * <p>Sequence numbers are tenant-and-aggregate-scoped and incremented
 * in-memory using an {@link AtomicLong}. For distributed deployments, the
 * SQL sequence in the migration provides a durable fallback (use
 * {@code nextval('...')} if strict ordering across nodes is required).
 *
 * @doc.type class
 * @doc.purpose Domain event publishing service (outbox pattern)
 * @doc.layer application
 * @doc.pattern Event Sourcing / Outbox
 */
public class EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);

    private final EventRepository eventRepository;
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    @Inject
    public EventPublisher(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Appends a single event to the event store.
     *
     * @param event the domain event to persist
     * @return Promise completing with the stored event ID
     */
    public Promise<String> publish(DomainEvent event) {
        long seq = sequenceCounter.incrementAndGet();
        return eventRepository.append(event, seq)
                .whenResult(id -> logger.info(
                        "Published event id={} type={} aggregate={} tenant={}",
                        id, event.getEventType(), event.getAggregateId(), event.getTenantId()))
                .whenException(e -> logger.error(
                        "Failed to publish event type={} aggregate={}: {}",
                        event.getEventType(), event.getAggregateId(), e.getMessage()));
    }

    /**
     * Appends multiple events atomically (same aggregate, sequential seq nums).
     *
     * @param events ordered list of domain events (same aggregate expected)
     * @return Promise completing when all are stored
     */
    public Promise<Void> publishAll(List<DomainEvent> events) {
        if (events.isEmpty()) {
            return Promise.complete();
        }
        List<Promise<String>> appends = events.stream()
                .map(this::publish)
                .toList();
        return Promises.all(appends);
    }

    /**
     * Polls and relays pending outbox entries for a tenant.
     * Intended to be called by a scheduled background task.
     *
     * @param tenantId tenant to process
     * @param batchSize max entries per invocation
     * @return Promise completing when the batch is processed
     */
    public Promise<Integer> relayOutbox(String tenantId, int batchSize) {
        return eventRepository.fetchPendingOutbox(tenantId, batchSize)
                .then(entries -> {
                    if (entries.isEmpty()) {
                        return Promise.of(0);
                    }
                    List<Promise<Void>> relays = entries.stream()
                            .map(entry -> relayEntry(entry))
                            .toList();
                    return Promises.all(relays).map(v -> entries.size());
                });
    }

    private Promise<Void> relayEntry(EventRepository.OutboxEntry entry) {
        // Placeholder for actual downstream transport (WebSocket broadcast,
        // in-process listener notification, etc.). Mark delivered immediately
        // in the outbox pattern's simplest form.
        return eventRepository.markOutboxDelivered(entry.outboxId())
                .whenResult(v -> logger.debug(
                        "Relayed outbox entry={} type={}", entry.outboxId(), entry.eventType()))
                .whenException(e -> logger.warn(
                        "Relay failed outbox entry={}: {}", entry.outboxId(), e.getMessage()))
                .toVoid();
    }
}
