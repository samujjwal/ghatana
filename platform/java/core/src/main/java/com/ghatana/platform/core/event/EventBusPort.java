/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.core.event;

/**
 * Platform-standard port for publishing domain events.
 *
 * <p>Replaces raw {@code Consumer<Object>} event publishers across domain packs.
 * The composition root binds an implementation that routes events to EventCloud,
 * Kafka outbox, or any other event infrastructure.
 *
 * <p>This is a {@link FunctionalInterface} so it can be used as a lambda or
 * method reference, enabling easy migration from {@code Consumer<Object>}:
 * <pre>{@code
 * // Before:  Consumer<Object> eventPublisher
 * // After:   EventBusPort eventBusPort
 * eventBusPort.publish(new OrderCreatedEvent(...));
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Platform-standard port for fire-and-forget domain event publishing
 * @doc.layer core
 * @doc.pattern Port
 */
@FunctionalInterface
public interface EventBusPort {

    /**
     * Publishes a domain event. Implementations must be non-blocking and should
     * handle serialization, routing, and delivery guarantees internally.
     *
     * @param event the domain event object (must not be null)
     */
    void publish(Object event);
}
