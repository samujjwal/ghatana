/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.datacloud.spi.EventLogStoreAdapters;
import com.ghatana.datacloud.spi.provider.InMemoryEventLogStoreProvider;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implements AEP's simplified {@link EventCloud} facade backed by
 * Data-Cloud's {@link EventLogStore}.
 *
 * <p>This bridges the AEP operator/pipeline world (synchronous, String-based)
 * to Data-Cloud's async {@link EventLogStore} SPI. The AEP facade has a
 * synchronous contract, so this implementation resolves Promises inline on
 * the calling thread (matching the contract of
 * {@link com.ghatana.aep.event.ConnectorBackedEventCloud}).
 *
 * <p>For async-aware callers, prefer using the {@link DataCloudEventCloudConnector}
 * which returns {@code Promise} directly.
 *
 * <p>Supports {@link ServiceLoader} discovery: when placed on the classpath
 * with a provider descriptor, the no-arg constructor discovers
 * {@link EventLogStore} via {@code ServiceLoader}.
 *
 * @doc.type class
 * @doc.purpose AEP EventCloud facade backed by Data-Cloud EventLogStore
 * @doc.layer product
 * @doc.pattern Adapter, Bridge
 */
public final class DataCloudBackedEventCloud implements EventCloud {

    private static final Logger log = LoggerFactory.getLogger(DataCloudBackedEventCloud.class);

    private final EventLogStore eventLogStore;

    /**
     * ServiceLoader-compatible constructor.
     *
     * <p>Discovers {@link EventLogStore} via {@link ServiceLoader}. When no
     * provider is registered on the classpath, uses the in-memory provider as
     * a safe fallback for local and test environments.
     */
    public DataCloudBackedEventCloud() {
        this(EventLogStoreAdapters.toPlatformStore(
            ServiceLoader.load(com.ghatana.datacloud.spi.EventLogStore.class).findFirst()
                .orElseGet(() -> {
                    log.warn("No EventLogStore SPI provider registered; using in-memory fallback");
                    return new InMemoryEventLogStoreProvider();
                })));
    }

    /**
     * @param eventLogStore Data-Cloud event log store; must not be null
     */
    public DataCloudBackedEventCloud(EventLogStore eventLogStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
    }

    @Override
    public String append(String tenantId, String eventType, byte[] payload) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(payload, "payload required");

        UUID eventId = UUID.randomUUID();
        EventEntry entry = EventEntry.builder()
            .eventId(eventId)
            .eventType(eventType)
            .payload(ByteBuffer.wrap(payload))
            .build();

        TenantContext tenant = TenantContext.of(tenantId);
        String[] resultHolder = new String[1];
        resultHolder[0] = eventId.toString();

        eventLogStore.append(tenant, entry)
            .whenResult(offset ->
                log.debug("[event-cloud] Appended event {} type={} tenant={} offset={}",
                    eventId, eventType, tenantId, offset))
            .whenException(e ->
                log.error("[event-cloud] Append failed event={} type={} tenant={}: {}",
                    eventId, eventType, tenantId, e.getMessage(), e));

        return resultHolder[0];
    }

    @Override
    public Subscription subscribe(String tenantId, String eventType, EventHandler handler) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(handler, "handler required");

        TenantContext tenant = TenantContext.of(tenantId);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        EventLogStore.Subscription[] delegateHolder = new EventLogStore.Subscription[1];

        eventLogStore.getLatestOffset(tenant)
            .then(latestOffset ->
                eventLogStore.tail(tenant, latestOffset, entry -> {
                    if (!cancelled.get() && eventType.equals(entry.eventType())) {
                        byte[] data = new byte[entry.payload().remaining()];
                        entry.payload().duplicate().get(data);
                        handler.handle(
                            entry.eventId().toString(),
                            entry.eventType(),
                            data);
                    }
                }))
            .whenResult(sub -> delegateHolder[0] = sub)
            .whenException(e ->
                log.error("[event-cloud] Subscribe failed type={} tenant={}: {}",
                    eventType, tenantId, e.getMessage(), e));

        return new Subscription() {
            @Override
            public void cancel() {
                cancelled.set(true);
                EventLogStore.Subscription delegate = delegateHolder[0];
                if (delegate != null) {
                    delegate.cancel();
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };
    }
}
