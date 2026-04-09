/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.event.spi.EventCloudConnector;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link EventCloudConnector} implementation backed by Data-Cloud's {@link EventLogStore}.
 *
 * <p>Selected when {@code EVENT_CLOUD_TRANSPORT=eventlog}. This connector stores events
 * directly in the Data-Cloud event log, providing durable append-only persistence with
 * multi-tenant isolation. It uses the topic name as the event type and routes all
 * operations through the Data-Cloud SPI.
 *
 * <p>Unlike the {@link DataCloudBackedEventCloud} (which implements the synchronous AEP
 * facade), this connector preserves the full async {@link Promise}-based contract for
 * callers that can work natively with ActiveJ Promises.
 *
 * <p>The default tenant used for connector-level operations is configurable. When
 * AEP routes through this connector, the tenant is resolved from the pipeline or
 * agent context.
 *
 * @doc.type class
 * @doc.purpose EventCloudConnector backed by Data-Cloud EventLogStore
 * @doc.layer product
 * @doc.pattern Adapter, SPI Implementation
 */
public final class DataCloudEventCloudConnector implements EventCloudConnector {

    private static final Logger log = LoggerFactory.getLogger(DataCloudEventCloudConnector.class);

    private static final String DEFAULT_TENANT = "aep-system";

    private final EventLogStore eventLogStore;
    private final String defaultTenantId;

    /**
     * Creates a connector with the default system tenant.
     *
     * @param eventLogStore Data-Cloud event log store
     */
    public DataCloudEventCloudConnector(EventLogStore eventLogStore) {
        this(eventLogStore, DEFAULT_TENANT);
    }

    /**
     * Creates a connector with a specific default tenant.
     *
     * @param eventLogStore   Data-Cloud event log store
     * @param defaultTenantId default tenant identifier for connector-level operations
     */
    public DataCloudEventCloudConnector(EventLogStore eventLogStore, String defaultTenantId) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
        this.defaultTenantId = Objects.requireNonNull(defaultTenantId, "defaultTenantId required");
    }

    @Override
    public Promise<String> publish(String topic, byte[] payload) {
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(payload, "payload required");

        UUID eventId = UUID.randomUUID();
        EventEntry entry = EventEntry.builder()
            .eventId(eventId)
            .eventType(topic)
            .payload(ByteBuffer.wrap(payload))
            .build();

        TenantContext tenant = TenantContext.of(defaultTenantId);

        return eventLogStore.append(tenant, entry)
            .map(offset -> {
                log.debug("[event-cloud-connector] Published event={} topic={} offset={}",
                    eventId, topic, offset);
                return eventId.toString();
            })
            .then(Promise::of, e -> {
                log.error("[event-cloud-connector] Publish failed topic={}: {}",
                    topic, e.getMessage(), e);
                return Promise.ofException(e);
            });
    }

    @Override
    public Promise<ConnectorSubscription> subscribe(
            String topic,
            String consumerGroup,
            EventPayloadHandler handler) {
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(consumerGroup, "consumerGroup required");
        Objects.requireNonNull(handler, "handler required");

        TenantContext tenant = TenantContext.of(defaultTenantId);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        return eventLogStore.getLatestOffset(tenant)
            .then(latestOffset ->
                eventLogStore.tail(tenant, latestOffset, entry -> {
                    if (!cancelled.get() && topic.equals(entry.eventType())) {
                        byte[] data = new byte[entry.payload().remaining()];
                        entry.payload().duplicate().get(data);
                        handler.onEvent(
                            entry.eventId().toString(),
                            entry.eventType(),
                            data);
                    }
                }))
            .map(storeSubscription -> new ConnectorSubscription() {
                @Override
                public void cancel() {
                    cancelled.set(true);
                    storeSubscription.cancel();
                    log.debug("[event-cloud-connector] Subscription cancelled topic={} group={}",
                        topic, consumerGroup);
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get();
                }
            });
    }

    /**
     * Publish an event with explicit tenant context.
     *
     * @param tenantId tenant identifier
     * @param topic    event type / topic
     * @param payload  event payload bytes
     * @return promise of assigned event ID
     */
    public Promise<String> publish(String tenantId, String topic, byte[] payload) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(payload, "payload required");

        UUID eventId = UUID.randomUUID();
        EventEntry entry = EventEntry.builder()
            .eventId(eventId)
            .eventType(topic)
            .payload(ByteBuffer.wrap(payload))
            .build();

        TenantContext tenant = TenantContext.of(tenantId);

        return eventLogStore.append(tenant, entry)
            .map(offset -> eventId.toString());
    }
}
