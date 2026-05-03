package com.ghatana.digitalmarketing.application.event;

import com.ghatana.digitalmarketing.domain.event.DmOutboxEntry;
import io.activej.promise.Promise;

/**
 * Port for dispatching outbox entries to the DMOS event bus.
 *
 * <p>Implementations may write to Kafka, ActiveMQ, an in-process bus,
 * or a test double. The dispatcher must be idempotent: if the same
 * entry is dispatched twice the second invocation must be a no-op.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS event dispatcher port for outbox pattern (DMOS-F2-002)
 * @doc.layer product
 * @doc.pattern Port, Adapter
 */
public interface DmEventDispatcher {

    /**
     * Dispatches a single outbox entry to the event bus.
     *
     * @param entry the outbox entry to dispatch; never {@code null}
     * @return completed promise on success; failed promise with the cause on error
     */
    Promise<Void> dispatch(DmOutboxEntry entry);
}
