package com.ghatana.core.connectors;

import io.activej.promise.Promise;

/**
 * Abstraction for external event sources (connectors) that produce events to be ingested.
 *
 * <p><b>Purpose</b><br>
 * Provides a uniform adapter interface for various event sources (Kafka, HTTP webhooks,
 * files, etc.) so the ingestion layer can consume events in a consistent manner.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EventSource source = kafkaEventSourceFactory.create(config);
 * source.start().get();
 * IngestEvent event = source.next().get();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Implementations should document thread-safety guarantees. Consumers typically call
 * `next()` from a single-threaded ingestion loop.
 *
 * <p><b>Architecture Role</b><br>
 * Core connector adapter (ingestion boundary). Implementations live in
 * `core/connectors` or adapter modules and must not depend on product modules.
 *
 * @doc.type interface
 * @doc.purpose Adapter for external event sources
 * @doc.layer core
 * @doc.pattern Adapter
 */
public interface EventSource {

    /**
     * Start the source, allocate resources and begin listening/consuming.
     *
     * @return Promise that completes when the source is started
     */
    Promise<Void> start();

    /**
     * Stop the source and release resources.
     *
     * @return Promise that completes when the source is stopped
     */
    Promise<Void> stop();

    /**
     * Read the next available event from the source.
     * This call should be backpressure-aware (non-blocking) and return a Promise
     * that completes with the next event when available.
     *
     * @return Promise of next IngestEvent
     */
    Promise<IngestEvent> next();

    /**
     * Close the source and free resources. Equivalent to stop().
     */
    default void close() {
        // default no-op; implementations may override to perform synchronous cleanup
    }
}

