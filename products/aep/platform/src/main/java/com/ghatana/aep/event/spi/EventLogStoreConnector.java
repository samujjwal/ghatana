/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.event.spi;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link EventCloudConnector} implementation backed by the data-cloud
 * {@link EventLogStore} SPI.
 *
 * <p>This is the default production transport when
 * {@code EVENT_CLOUD_TRANSPORT=eventlog} (or when no override is set and
 * an {@code EventLogStore} provider is present on the classpath).
 *
 * @doc.type class
 * @doc.purpose EventCloudConnector backed by Data-Cloud EventLogStore
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public final class EventLogStoreConnector implements EventCloudConnector {

    private static final Logger log = LoggerFactory.getLogger(EventLogStoreConnector.class);
    private static final String HEADER_TOPIC = "aep.topic";
    private static final String SYSTEM_TENANT = "aep-system";

    private final EventLogStore eventLogStore;
    private final TenantContext systemTenant;

    /**
     * @param eventLogStore backing Data-Cloud store (never {@code null})
     * @param systemTenantId tenant context used for system-level events; defaults to {@code "aep-system"}
     */
    public EventLogStoreConnector(EventLogStore eventLogStore, String systemTenantId) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
        this.systemTenant = TenantContext.of(
                systemTenantId != null && !systemTenantId.isBlank() ? systemTenantId : SYSTEM_TENANT);
    }

    @Override
    public Promise<String> publish(String topic, byte[] payload) {
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(payload, "payload required");

        UUID eventId = UUID.randomUUID();
        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventId(eventId)
                .eventType(topic)
                .timestamp(Instant.now())
                .payload(payload)
                .contentType("application/json")
                .headers(Map.of(HEADER_TOPIC, topic))
                .idempotencyKey(eventId.toString())
                .build();

        return eventLogStore.append(systemTenant, entry)
                .map(ignored -> eventId.toString());
    }

    @Override
    public Promise<ConnectorSubscription> subscribe(
            String topic, String consumerGroup, EventPayloadHandler handler) {
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(consumerGroup, "consumerGroup required");
        Objects.requireNonNull(handler, "handler required");

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<EventLogStore.Subscription> delegateRef = new AtomicReference<>();

        return eventLogStore.getLatestOffset(systemTenant)
                .then(offset -> eventLogStore.tail(systemTenant, offset, entry -> {
                    if (!cancelled.get() && topic.equals(entry.eventType())) {
                        handler.onEvent(entry.eventId().toString(), topic, toBytes(entry.payload()));
                    }
                }))
                .map(subscription -> {
                    if (cancelled.get()) {
                        subscription.cancel();
                    } else {
                        delegateRef.set(subscription);
                    }
                    return (ConnectorSubscription) new ConnectorSubscription() {
                        @Override
                        public void cancel() {
                            cancelled.set(true);
                            EventLogStore.Subscription d = delegateRef.getAndSet(null);
                            if (d != null) {
                                d.cancel();
                            }
                        }

                        @Override
                        public boolean isCancelled() {
                            EventLogStore.Subscription d = delegateRef.get();
                            return cancelled.get() || (d != null && d.isCancelled());
                        }
                    };
                });
    }

    private static byte[] toBytes(java.nio.ByteBuffer buffer) {
        java.nio.ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
