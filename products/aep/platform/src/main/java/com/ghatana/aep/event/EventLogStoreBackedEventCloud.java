package com.ghatana.aep.event;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * EventCloud adapter backed by Data Cloud EventLogStore.
 */
public final class EventLogStoreBackedEventCloud implements EventCloud {

    private static final Logger log = LoggerFactory.getLogger(EventLogStoreBackedEventCloud.class);
    private static final String HEADER_EVENT_TYPE = "aep.eventType";

    private final EventLogStore eventLogStore;

    public EventLogStoreBackedEventCloud(EventLogStore eventLogStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
    }

    @Override
    public String append(String tenantId, String eventType, byte[] payload) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(payload, "payload required");

        UUID eventId = UUID.randomUUID();
        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(eventId)
            .eventType(eventType)
            .timestamp(Instant.now())
            .payload(payload)
            .contentType("application/json")
            .headers(Map.of(HEADER_EVENT_TYPE, eventType))
            .idempotencyKey(eventId.toString())
            .build();

        eventLogStore.append(TenantContext.of(tenantId), entry)
            .whenException(e -> log.error("Failed appending event: tenantId={}, eventType={}", tenantId, eventType, e));

        return eventId.toString();
    }

    @Override
    public Subscription subscribe(String tenantId, String eventType, EventHandler handler) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(handler, "handler required");

        TenantContext tenant = TenantContext.of(tenantId);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<EventLogStore.Subscription> delegateRef = new AtomicReference<>();

        eventLogStore.getLatestOffset(tenant)
            .then(offset -> eventLogStore.tail(tenant, offset, entry -> {
                if (!cancelled.get() && eventType.equals(entry.eventType())) {
                    handler.handle(entry.eventId().toString(), entry.eventType(), bytes(entry.payload()));
                }
            }))
            .whenResult(subscription -> {
                if (cancelled.get()) {
                    subscription.cancel();
                } else {
                    delegateRef.set(subscription);
                }
            })
            .whenException(e -> log.error("Failed subscription: tenantId={}, eventType={}", tenantId, eventType, e));

        return new Subscription() {
            @Override
            public void cancel() {
                cancelled.set(true);
                EventLogStore.Subscription delegate = delegateRef.getAndSet(null);
                if (delegate != null) {
                    delegate.cancel();
                }
            }

            @Override
            public boolean isCancelled() {
                EventLogStore.Subscription delegate = delegateRef.get();
                return cancelled.get() || (delegate != null && delegate.isCancelled());
            }
        };
    }

    private static byte[] bytes(java.nio.ByteBuffer buffer) {
        java.nio.ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
