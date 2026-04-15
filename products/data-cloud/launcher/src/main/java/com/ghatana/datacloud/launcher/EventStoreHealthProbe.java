package com.ghatana.datacloud.launcher;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Event-store-backed standalone health snapshot supplier.
 *
 * @doc.type class
 * @doc.purpose Produce standalone event-store health snapshots from the platform event store SPI
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class EventStoreHealthProbe implements Supplier<Map<String, Object>> {

    private final EventLogStore eventLogStore;
    private final long timeoutMillis;

    public EventStoreHealthProbe(EventLogStore eventLogStore, long timeoutMillis) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore must not be null");
        this.timeoutMillis = Math.max(1L, timeoutMillis);
    }

    @Override
    public Map<String, Object> get() {
        long startedAt = System.nanoTime();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> latestOffset = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        eventLogStore.getLatestOffset(TenantContext.of("health-check", Map.of("probe", "event_store")))
            .whenResult(offset -> {
                latestOffset.set(offset);
                latch.countDown();
            })
            .whenException(error -> {
                failure.set(error);
                latch.countDown();
            });

        try {
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return failureSnapshot("Event store probe timed out", startedAt);
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return failureSnapshot("Event store probe interrupted", startedAt);
        }

        Throwable error = failure.get();
        if (error != null) {
            return failureSnapshot(error.getClass().getSimpleName() + ": " + error.getMessage(), startedAt);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", "UP");
        snapshot.put("latencyMs", durationMs(startedAt));
        snapshot.put("timeoutMs", timeoutMillis);
        snapshot.put("latestOffset", String.valueOf(latestOffset.get()));
        return snapshot;
    }

    private Map<String, Object> failureSnapshot(String message, long startedAt) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", "DOWN");
        snapshot.put("latencyMs", durationMs(startedAt));
        snapshot.put("timeoutMs", timeoutMillis);
        snapshot.put("message", message);
        return snapshot;
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}