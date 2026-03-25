package com.ghatana.core.connectors;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import io.activej.promise.Promise;

/**
 * Abstraction for event sinks where events can be delivered (e.g., Kafka, file, external API).
 *
 * <p><b>Purpose</b><br>
 * Provides a uniform interface for publishing {@link EventLogStore.EventEntry} instances
 * to external systems.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EventSink sink = KafkaEventSink.create(config);
 * sink.start().get();
 * sink.send(tenant, entry).get();
 * sink.stop().get();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Adapter for external event sinks
 * @doc.layer core
 * @doc.pattern Adapter
 */
public interface EventSink {

    /**
     * Start the sink and allocate resources.
     *
     * @return Promise completes when sink is ready
     */
    Promise<Void> start();

    /**
     * Stop the sink and release resources.
     *
     * @return Promise completes when sink is stopped
     */
    Promise<Void> stop();

    /**
     * Send an event entry to the sink.
     *
     * @param tenant the tenant context
     * @param entry  the event entry to publish
     * @return Promise completes with void on success
     */
    Promise<Void> send(TenantContext tenant, EventLogStore.EventEntry entry);

    /**
     * Flush any buffered events. Optional.
     *
     * @return Promise completes when flush finishes
     */
    default Promise<Void> flush() {
        return Promise.complete();
    }

    /**
     * Close the sink (synchronous) - default no-op.
     */
    default void close() {
    }
}

