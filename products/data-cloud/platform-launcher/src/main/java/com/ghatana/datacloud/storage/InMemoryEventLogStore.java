package com.ghatana.datacloud.storage;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory reference implementation of {@link EventLogStore} for development
 * and single-node test deployments where durability is not required.
 *
 * <p>Use {@link WarmTierEventLogStore} for production PostgreSQL-backed storage.
 *
 * @doc.type class
 * @doc.purpose In-memory EventLogStore for dev/test; not durable across restarts
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class InMemoryEventLogStore implements EventLogStore {

    private final Map<String, List<EventEntry>> store = new ConcurrentHashMap<>();
    private final Map<String, Long> offsets = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<EventEntry>>> tailListeners = new ConcurrentHashMap<>();

    @Override
    public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
        List<EventEntry> entries = store.computeIfAbsent(tenant.tenantId(), k -> new ArrayList<>());
        long offset;
        synchronized (entries) {
            entries.add(entry);
            offset = offsets.compute(tenant.tenantId(), (k, v) -> v == null ? 1L : v + 1L);
        }
        List<Consumer<EventEntry>> listeners = tailListeners.get(tenant.tenantId());
        if (listeners != null) {
            for (Consumer<EventEntry> listener : listeners) {
                listener.accept(entry);
            }
        }
        return Promise.of(Offset.of(offset));
    }

    @Override
    public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
        List<Offset> results = new ArrayList<>(entries.size());
        for (EventEntry entry : entries) {
            // y04-ok — synchronous Promise.of result is safe to unwrap here
            results.add(append(tenant, entry).getResult());
        }
        return Promise.of(results);
    }

    @Override
    public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
        List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
        long startOffset = normalizedReadOffset(from);
        return Promise.of(entries.stream()
            .skip(startOffset)
            .limit(limit)
            .toList());
    }

    @Override
    public Promise<List<EventEntry>> readByTimeRange(
            TenantContext tenant,
            Instant startTime,
            Instant endTime,
            int limit) {
        List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
        return Promise.of(entries.stream()
            .filter(e -> !e.timestamp().isBefore(startTime) && e.timestamp().isBefore(endTime))
            .limit(limit)
            .toList());
    }

    @Override
    public Promise<List<EventEntry>> readByType(
            TenantContext tenant,
            String eventType,
            Offset from,
            int limit) {
        List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
        long startOffset = normalizedReadOffset(from);
        return Promise.of(entries.stream()
            .skip(startOffset)
            .filter(e -> e.eventType().equals(eventType))
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
        List<EventEntry> existing = store.getOrDefault(tenant.tenantId(), List.of());
        int startIndex = tailStartIndex(from, existing.size());
        for (int i = startIndex; i < existing.size(); i++) {
            handler.accept(existing.get(i));
        }

        final boolean[] cancelled = {false};
        Consumer<EventEntry> guardedHandler = entry -> {
            if (!cancelled[0]) handler.accept(entry);
        };
        tailListeners
            .computeIfAbsent(tenant.tenantId(), k -> new CopyOnWriteArrayList<>())
            .add(guardedHandler);

        return Promise.of(new Subscription() {
            @Override
            public void cancel() {
                cancelled[0] = true;
                List<Consumer<EventEntry>> list = tailListeners.get(tenant.tenantId());
                if (list != null) list.remove(guardedHandler);
            }

            @Override
            public boolean isCancelled() {
                return cancelled[0];
            }
        });
    }

    // ── Offset utilities ──────────────────────────────────────────────────────

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
            throw new IllegalArgumentException(
                "Offset must be numeric for InMemoryEventLogStore: '" + offset.value() + "'", e);
        }
    }
}
