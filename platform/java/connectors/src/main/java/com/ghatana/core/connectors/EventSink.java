package com.ghatana.core.connectors;

import com.ghatana.core.event.cloud.EventRecord;
import io.activej.promise.Promise;

/**
 * Abstraction for event sinks where events can be delivered (e.g., Kafka, file, external API).
 *
 * <p><b>Purpose</b><br>
 * Provides a uniform interface for publishing EventRecord instances to external systems.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EventSink sink = KafkaEventSink.create(config);
 * sink.start().get();
 * sink.send(eventRecord).get();
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
     * Send an event record to the sink.
     *
     * @param record EventRecord to send
     * @return Promise completes with void on success
     */
    Promise<Void> send(EventRecord record);

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

