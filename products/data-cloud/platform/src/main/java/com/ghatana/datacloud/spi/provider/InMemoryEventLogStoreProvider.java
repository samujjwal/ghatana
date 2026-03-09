package com.ghatana.datacloud.spi.provider;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ServiceLoader provider for EventLogStore used by platform integrations.
 */
public final class InMemoryEventLogStoreProvider implements EventLogStore {

    private final Map<String, List<EventEntry>> store = new ConcurrentHashMap<>();
    private final Map<String, Long> offsets = new ConcurrentHashMap<>();

    @Override
    public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
        List<EventEntry> entries = store.computeIfAbsent(tenant.tenantId(), ignored -> new ArrayList<>());
        synchronized (entries) {
            entries.add(entry);
            long offset = offsets.compute(tenant.tenantId(), (key, value) -> value == null ? 1L : value + 1L);
            return Promise.of(Offset.of(offset));
        }
    }

    @Override
    public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
        List<Offset> results = new ArrayList<>(entries.size());
        for (EventEntry entry : entries) {
            results.add(append(tenant, entry).getResult());
        }
        return Promise.of(results);
    }

    @Override
    public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
        List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
        long startOffset = normalizedReadOffset(from);
        return Promise.of(entries.stream().skip(startOffset).limit(limit).toList());
    }

    @Override
    public Promise<List<EventEntry>> readByTimeRange(
            TenantContext tenant,
            Instant startTime,
            Instant endTime,
            int limit) {
        List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
        return Promise.of(entries.stream()
            .filter(entry -> !entry.timestamp().isBefore(startTime) && entry.timestamp().isBefore(endTime))
            .limit(limit)
            .toList());
    }

    @Override
    public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) {
        List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
        long startOffset = normalizedReadOffset(from);
        return Promise.of(entries.stream()
            .skip(startOffset)
            .filter(entry -> eventType.equals(entry.eventType()))
            .limit(limit)
            .toList());
    }

    @Override
    public Promise<Offset> getLatestOffset(TenantContext tenant) {
        Long offset = offsets.get(tenant.tenantId());
        return Promise.of(offset != null ? Offset.of(offset) : Offset.zero());
    }

    @Override
    public Promise<Offset> getEarliestOffset(TenantContext tenant) {
        return Promise.of(Offset.zero());
    }

    @Override
    public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
        List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
        int startIndex = tailStartIndex(from, entries.size());
        for (int i = startIndex; i < entries.size(); i++) {
            handler.accept(entries.get(i));
        }

        return Promise.of(new Subscription() {
            private volatile boolean cancelled;

            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        });
    }

    private static long normalizedReadOffset(Offset offset) {
        return Math.max(0L, numericOffsetValue(offset));
    }

    private static int tailStartIndex(Offset from, int entryCount) {
        long offsetValue = numericOffsetValue(from);
        if (offsetValue < 0) {
            return entryCount;
        }
        return (int) Math.min(offsetValue, (long) entryCount);
    }

    private static long numericOffsetValue(Offset offset) {
        try {
            return Long.parseLong(offset.value());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Offset must be numeric: " + offset.value(), e);
        }
    }
}

