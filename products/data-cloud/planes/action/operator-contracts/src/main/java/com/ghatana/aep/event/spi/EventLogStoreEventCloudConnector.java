/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.event.spi;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.EventLogStore.Subscription;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * {@link EventCloudConnector} implementation backed by Data Cloud {@link EventLogStore}.
 *
 * <p>WS2-6: Bridges AEP EventCloud SPI to Data Cloud Event Plane public SPI.
 * Selected when {@code EVENT_CLOUD_TRANSPORT=eventlog}.
 *
 * <p>This connector uses the canonical EventLogStore interface for event storage
 * and retrieval, providing a direct bridge to the Data Cloud Event Plane without
 * requiring external transport protocols.
 *
 * @doc.type class
 * @doc.purpose EventCloudConnector implementation using Data Cloud EventLogStore SPI
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class EventLogStoreEventCloudConnector implements EventCloudConnector {

    private static final Logger log = LoggerFactory.getLogger(EventLogStoreEventCloudConnector.class);

    private final EventLogStore eventLogStore;
    private final Executor blockingExecutor;

    /**
     * Creates an EventLogStore-backed connector.
     *
     * @param eventLogStore    Data Cloud EventLogStore implementation
     * @param blockingExecutor executor for blocking operations
     */
    public EventLogStoreEventCloudConnector(EventLogStore eventLogStore, Executor blockingExecutor) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor required");
        log.info("[EventLogStoreEventCloudConnector] initialised with EventLogStore: {}", eventLogStore.getClass().getSimpleName());
    }

    @Override
    public Promise<String> publish(String topic, byte[] payload) {
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(payload, "payload required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            // Convert topic/stream to EventEntry using the builder pattern
            EventEntry entry = EventEntry.builder()
                .eventId(UUID.randomUUID())
                .eventType(topic)
                .eventVersion("1.0")
                .timestamp(java.time.Instant.now())
                .payload(java.nio.ByteBuffer.wrap(payload))
                .contentType("application/octet-stream")
                .headers(Map.of("topic", topic))
                .build();

            TenantContext tenant = TenantContext.of("default");
            Offset offset = eventLogStore.append(tenant, entry).getResult();

            String eventId = entry.eventId().toString();
            log.debug("[EventLogStore] publish ok topic={} eventId={}", topic, eventId);
            return eventId;
        });
    }

    @Override
    public Promise<ConnectorSubscription> subscribe(
            String topic, String consumerGroup, EventPayloadHandler handler) {
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(consumerGroup, "consumerGroup required");
        Objects.requireNonNull(handler, "handler required");

        TenantContext tenant = TenantContext.of("default");

        return Promise.ofBlocking(blockingExecutor, () -> {
            // Create tail subscription from EventLogStore
            Subscription eventLogSubscription = eventLogStore.tail(
                tenant,
                Offset.zero(),
                entry -> {
                    String eventId = entry.eventId().toString();
                    ByteBuffer payloadBuffer = entry.payload();
                    byte[] payload = new byte[payloadBuffer.remaining()];
                    payloadBuffer.get(payload);
                    handler.onEvent(eventId, topic, payload);
                }
            ).getResult();

            log.info("[EventLogStore] subscribe topic={} group={}", topic, consumerGroup);

            // Wrap EventLogStore Subscription in ConnectorSubscription
            return new ConnectorSubscription() {
                private volatile boolean cancelled = false;

                @Override
                public void cancel() {
                    cancelled = true;
                    eventLogSubscription.cancel();
                    log.debug("[EventLogStore] subscription cancelled for topic={}", topic);
                }

                @Override
                public boolean isCancelled() {
                    return cancelled || eventLogSubscription.isCancelled();
                }
            };
        });
    }
}
