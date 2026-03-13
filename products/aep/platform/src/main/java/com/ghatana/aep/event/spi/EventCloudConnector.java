/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.event.spi;

import io.activej.promise.Promise;

/**
 * SPI for AEP event-cloud transport.
 *
 * <p>Implementations are selected at startup via the {@code EVENT_CLOUD_TRANSPORT}
 * environment variable:
 * <ul>
 *   <li>{@code grpc} — gRPC-based transport (default)</li>
 *   <li>{@code http} — REST/HTTP transport</li>
 *   <li>{@code eventlog} — Data-Cloud {@code EventLogStore}-backed impl</li>
 * </ul>
 *
 * <p>All methods return {@link Promise} and must never block the ActiveJ
 * eventloop. Blocking IO must be wrapped with {@code Promise.ofBlocking(executor, …)}.
 *
 * @doc.type interface
 * @doc.purpose SPI for event-cloud transport — gRPC, HTTP, or EventLogStore.
 *              Selected by {@code EVENT_CLOUD_TRANSPORT} env var.
 * @doc.layer platform
 * @doc.pattern SPI
 */
public interface EventCloudConnector {

    /**
     * Published a raw event payload to the specified topic.
     *
     * @param topic   logical topic/stream name (never blank)
     * @param payload serialised event bytes
     * @return promise of the assigned event ID string
     */
    Promise<String> publish(String topic, byte[] payload);

    /**
     * Subscribes to events on the specified topic.
     *
     * <p>The returned {@link ConnectorSubscription} must be {@link ConnectorSubscription#cancel()
     * cancelled} when the consumer no longer needs events to avoid resource leaks.
     *
     * @param topic         logical topic/stream name
     * @param consumerGroup logical consumer group identifier (enables competing-consumer semantics)
     * @param handler       callback invoked for each received event
     * @return subscription handle
     */
    Promise<ConnectorSubscription> subscribe(String topic, String consumerGroup, EventPayloadHandler handler);

    /**
     * Callback for received event payloads.
     */
    @FunctionalInterface
    interface EventPayloadHandler {
        /**
         * @param eventId  unique event identifier
         * @param topic    topic/stream the event arrived on
         * @param payload  raw event bytes
         */
        void onEvent(String eventId, String topic, byte[] payload);
    }

    /**
     * Active subscription returned by {@link #subscribe}.
     */
    interface ConnectorSubscription {
        /** Cancels the subscription and releases underlying resources. */
        void cancel();

        /** @return {@code true} if {@link #cancel()} has been called */
        boolean isCancelled();
    }
}
