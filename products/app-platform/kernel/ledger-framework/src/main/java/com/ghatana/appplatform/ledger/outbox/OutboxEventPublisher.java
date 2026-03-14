/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.outbox;

/**
 * Abstraction for delivering an {@link OutboxEntry} to the message broker (K17-003).
 *
 * <p>Implementations connect to Kafka, NATS, RabbitMQ, or any other broker.
 * Throw a {@link RuntimeException} to signal delivery failure; the relay will
 * increment the attempt counter and retry on the next poll cycle.
 *
 * <p>Example Kafka wiring:
 * <pre>{@code
 * OutboxEventPublisher publisher = entry ->
 *     kafkaProducer.send(new ProducerRecord<>(entry.eventType(), entry.payload())).get();
 * OutboxRelay relay = new OutboxRelay(store, publisher);
 * relay.start(1_000L);
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Event publisher abstraction for outbox relay (K17-003)
 * @doc.layer core
 * @doc.pattern Service
 */
@FunctionalInterface
public interface OutboxEventPublisher {

    /**
     * Delivers the outbox entry to the message broker.
     *
     * <p>This method is called from a non-eventloop thread by {@link OutboxRelay}.
     * Implementations should be synchronous and throw on failure.
     *
     * @param entry the outbox entry to deliver
     * @throws RuntimeException on delivery failure (triggers retry)
     */
    void publish(OutboxEntry entry);
}
