package com.ghatana.datacloud.storage;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.SubscriptionState;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    
    // P3-03: In-memory checkpoint storage for testing
    private final Map<String, Map<String, Checkpoint>> checkpoints = new ConcurrentHashMap<>();
    
    // P3-03: Track subscriptions for unsubscribe
    private final Map<String, Map<String, Subscription>> subscriptions = new ConcurrentHashMap<>();

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
        String subscriptionId = java.util.UUID.randomUUID().toString();

        // WS5-7: Track subscription state
        final SubscriptionState[] state = {SubscriptionState.ACTIVE};
        final Consumer<Throwable> errorHandler = null;

        Consumer<EventEntry> guardedHandler = entry -> {
            if (!cancelled[0]) {
                try {
                    handler.accept(entry);
                } catch (Throwable t) {
                    state[0] = SubscriptionState.ERROR;
                    if (errorHandler != null) {
                        errorHandler.accept(t);
                    }
                }
            }
        };
        tailListeners
            .computeIfAbsent(tenant.tenantId(), k -> new CopyOnWriteArrayList<>())
            .add(guardedHandler);

        Subscription subscription = new Subscription() {
            @Override
            public void cancel() {
                state[0] = SubscriptionState.CLOSED;
                cancelled[0] = true;
                List<Consumer<EventEntry>> list = tailListeners.get(tenant.tenantId());
                if (list != null) list.remove(guardedHandler);
                // P3-03: Remove from tracked subscriptions
                Map<String, Subscription> tenantSubs = subscriptions.get(tenant.tenantId());
                if (tenantSubs != null) tenantSubs.remove(subscriptionId);
            }

            @Override
            public boolean isCancelled() {
                return cancelled[0];
            }

            @Override
            public SubscriptionState getState() {
                return state[0];
            }

            @Override
            public void setErrorHandler(Consumer<Throwable> errorHandler) {
                // In-memory implementation does not support error callbacks
            }

            @Override
            public SubscriptionId getId() {
                return new SubscriptionId(subscriptionId);
            }
        };
        
        // P3-03: Track subscription for unsubscribe
        subscriptions.computeIfAbsent(tenant.tenantId(), k -> new ConcurrentHashMap<>())
            .put(subscriptionId, subscription);
        
        return Promise.of(subscription);
    }

    // ==================== Checkpoint Management (P3-03) ====================

    @Override
    public Promise<Optional<Checkpoint>> readCheckpoint(TenantContext tenant, String stream, String consumerGroup) {
        String key = stream + ":" + consumerGroup;
        Map<String, Checkpoint> tenantCheckpoints = checkpoints.get(tenant.tenantId());
        if (tenantCheckpoints == null) {
            return Promise.of(Optional.empty());
        }
        return Promise.of(Optional.ofNullable(tenantCheckpoints.get(key)));
    }

    @Override
    public Promise<Checkpoint> commitCheckpoint(TenantContext tenant, String stream, String consumerGroup, Offset offset, String idempotencyKey) {
        String key = stream + ":" + consumerGroup;
        Checkpoint checkpoint = new Checkpoint(stream, consumerGroup, offset, Instant.now(), idempotencyKey);
        checkpoints.computeIfAbsent(tenant.tenantId(), k -> new ConcurrentHashMap<>())
            .put(key, checkpoint);
        return Promise.of(checkpoint);
    }

    @Override
    public Promise<Boolean> deleteCheckpoint(TenantContext tenant, String stream, String consumerGroup) {
        String key = stream + ":" + consumerGroup;
        Map<String, Checkpoint> tenantCheckpoints = checkpoints.get(tenant.tenantId());
        if (tenantCheckpoints == null) {
            return Promise.of(false);
        }
        return Promise.of(tenantCheckpoints.remove(key) != null);
    }

    @Override
    public Promise<Map<String, Checkpoint>> getAllCheckpointsWithMetadata(TenantContext tenant) {
        Map<String, Checkpoint> tenantCheckpoints = checkpoints.get(tenant.tenantId());
        if (tenantCheckpoints == null) {
            return Promise.of(Map.of());
        }
        return Promise.of(Map.copyOf(tenantCheckpoints));
    }

    // ==================== Replay (P3-03) ====================

    @Override
    public Promise<List<EventEntry>> replay(TenantContext tenant, ReplaySpec spec) {
        List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
        long fromOffsetValue = numericOffsetValue(spec.fromOffset());
        long toOffsetValue = spec.toOffset().value().equals("-1") 
            ? Long.MAX_VALUE 
            : numericOffsetValue(spec.toOffset());
        
        return Promise.of(entries.stream()
            .skip(fromOffsetValue)
            .takeWhile(e -> {
                long eventOffset = numericOffsetValue(Offset.of(String.valueOf(entries.indexOf(e) + 1)));
                return eventOffset <= toOffsetValue;
            })
            .filter(e -> spec.eventTypes().isEmpty() || spec.eventTypes().contains(e.eventType()))
            .toList());
    }

    @Override
    public Promise<Void> unsubscribe(TenantContext tenant, SubscriptionId subscriptionId) {
        Map<String, Subscription> tenantSubs = subscriptions.get(tenant.tenantId());
        if (tenantSubs != null) {
            Subscription subscription = tenantSubs.remove(subscriptionId.value());
            if (subscription != null) {
                subscription.cancel();
            }
        }
        return Promise.of(null);
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
