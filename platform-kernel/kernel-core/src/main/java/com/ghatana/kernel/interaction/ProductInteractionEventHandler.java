package com.ghatana.kernel.interaction;

import io.activej.promise.Promise;

/**
 * Public SPI implemented by subscribers to product interaction events.
 *
 * @param <Event> event payload type
 *
 * @doc.type interface
 * @doc.purpose Receive governed product interaction events through the Kernel broker
 * @doc.layer kernel
 * @doc.pattern SPI
 */
public interface ProductInteractionEventHandler<Event> {

    String subscriberId();

    String topic();

    Class<Event> eventType();

    Promise<Void> handle(ProductInteractionEventEnvelope<Event> envelope);
}
