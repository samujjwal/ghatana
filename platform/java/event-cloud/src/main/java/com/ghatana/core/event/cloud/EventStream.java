package com.ghatana.core.event.cloud;

import com.ghatana.core.event.cloud.EventCloud.EventConsumer;

/**
 * Backpressure-aware event stream.
 * Consumers must call request(n) to receive events.
 *
 * @doc.type interface
 * @doc.purpose Backpressure-aware event stream interface
 * @doc.layer core
 * @doc.pattern Stream
 */
public interface EventStream extends AutoCloseable {
    /**
     * Request n events (backpressure demand).
     *
     * @param n number of events to request
     */
    void request(long n);

    /**
     * Register event consumer callback.
     *
     * @param consumer consumer to receive event chunks
     */
    void onEvent(EventConsumer consumer);

    /**
     * Pause event delivery.
     */
    void pause();

    /**
     * Resume event delivery.
     */
    void resume();

    /**
     * Close the stream and release resources.
     */
    @Override
    void close();
}
