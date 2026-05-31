package com.ghatana.datacloud.spi.provider;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory reference implementation of {@link EventLogStore}.
 *
 * <p>Suitable for development, testing, and single-node deployments where
 * durability is not required. Registered as a Java {@link java.util.ServiceLoader}
 * provider so that platform modules can discover it via the SPI mechanism.
 *
 * @doc.type class
 * @doc.purpose In-memory reference implementation of EventLogStore for dev/test use
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class InMemoryEventLogStoreProvider implements EventLogStore {

    /** Header key used to persist the assigned offset inside each stored entry (FINDING-M4). */
    private static final String HEADER_OFFSET_KEY = "_x_dc_offset";

    private final Map<String, List<EventEntry>> store = new ConcurrentHashMap<>();
    private final Map<String, Long> offsets = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<EventEntry>>> tailListeners = new ConcurrentHashMap<>();
    private final AtomicLong totalTailSubscriptions = new AtomicLong();
    private final AtomicLong totalTailNotifications = new AtomicLong();

    @Override
    public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
        List<EventEntry> entries = store.computeIfAbsent(tenant.tenantId(), ignored -> new ArrayList<>());
        synchronized (entries) {
            // M4: compute offset first so we can embed it in the stored entry's headers
            long offset = offsets.compute(tenant.tenantId(), (key, value) -> value == null ? 1L : value + 1L);
            EventEntry storedEntry = withOffsetHeader(entry, offset);
            entries.add(storedEntry);
            notifyTailListeners(tenant.tenantId(), storedEntry);
            return Promise.of(Offset.of(offset));
        }
    }

    @Override
    public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
        List<EventEntry> tenantEntries = store.computeIfAbsent(tenant.tenantId(), ignored -> new ArrayList<>());
        List<Offset> results = new ArrayList<>(entries.size());
        synchronized (tenantEntries) {
            for (EventEntry entry : entries) {
                // M4: embed offset header before storing
                long offset = offsets.compute(tenant.tenantId(), (key, value) -> value == null ? 1L : value + 1L);
                EventEntry storedEntry = withOffsetHeader(entry, offset);
                tenantEntries.add(storedEntry);
                notifyTailListeners(tenant.tenantId(), storedEntry);
                results.add(Offset.of(offset));
            }
        }
        return Promise.of(results);
    }

    @Override
    public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
        List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
        long startOffset = normalizedReadOffset(from);
        // H2: filter by injected offset header value — not by list index (skip() was wrong)
        return Promise.of(entries.stream()
            .filter(e -> headerOffset(e) >= startOffset)
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
            .filter(entry -> !entry.timestamp().isBefore(startTime) && entry.timestamp().isBefore(endTime))
            .limit(limit)
            .toList());
    }

    @Override
    public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) {
        List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
        long startOffset = normalizedReadOffset(from);
        // H2: filter by injected offset header value — not by list index (skip() was wrong)
        return Promise.of(entries.stream()
            .filter(e -> headerOffset(e) >= startOffset)
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
        // L4: create guarded callback so cancellation is honoured between delivered entries.
        final String tenantId = tenant.tenantId();
        final boolean[] cancelled = {false};

        Consumer<EventEntry> guardedHandler = event -> {
            if (!cancelled[0]) {
                handler.accept(event);
            }
        };

        final Subscription sub = new Subscription() {
            @Override
            public void cancel() {
                cancelled[0] = true;
                List<Consumer<EventEntry>> listeners = tailListeners.get(tenantId);
                if (listeners != null) {
                    listeners.remove(guardedHandler);
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled[0];
            }
        };

        for (int i = startIndex; i < entries.size() && !sub.isCancelled(); i++) {
            handler.accept(entries.get(i));
        }

        tailListeners.computeIfAbsent(tenantId, ignored -> new CopyOnWriteArrayList<>()).add(guardedHandler);
        totalTailSubscriptions.incrementAndGet();

        return Promise.of(sub);
    }

    /**
     * Runtime telemetry snapshot for Runtime Truth posture exposure.
     */
    public Map<String, Object> tailRuntimeSnapshot() {
        return Map.of(
            "available", true,
            "configurable", false,
            "storeType", getClass().getSimpleName(),
            "mode", "push",
            "activeSubscribers", activeSubscriberCount(),
            "totalSubscriptions", totalTailSubscriptions.get(),
            "totalNotifications", totalTailNotifications.get());
    }

    private void notifyTailListeners(String tenantId, EventEntry entry) {
        List<Consumer<EventEntry>> listeners = tailListeners.get(tenantId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        totalTailNotifications.addAndGet(listeners.size());
        listeners.forEach(listener -> listener.accept(entry));
    }

    private int activeSubscriberCount() {
        return tailListeners.values().stream().mapToInt(List::size).sum();
    }

    /** Returns the stored offset value from an entry's headers, or Long.MAX_VALUE if absent. */
    private static long headerOffset(EventEntry entry) {
        String v = entry.headers().get(HEADER_OFFSET_KEY);
        return v != null ? Long.parseLong(v) : Long.MAX_VALUE;
    }

    /** Reconstructs an EventEntry with the given offset injected into its headers (FINDING-M4). */
    private static EventEntry withOffsetHeader(EventEntry entry, long offset) {
        Map<String, String> enriched = new HashMap<>(entry.headers());
        enriched.put(HEADER_OFFSET_KEY, Long.toString(offset));
        return EventEntry.builder()
            .eventId(entry.eventId())
            .eventType(entry.eventType())
            .eventVersion(entry.eventVersion())
            .timestamp(entry.timestamp())
            .payload(entry.payload())
            .contentType(entry.contentType())
            .headers(enriched)
            .idempotencyKey(entry.idempotencyKey().orElse(null))
            .build();
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

    // ==================== Checkpoint Management (P3-03) ====================

    private final Map<String, Map<String, Checkpoint>> checkpoints = new ConcurrentHashMap<>();

    @Override
    public Promise<Optional<Checkpoint>> readCheckpoint(TenantContext tenant, String stream, String consumerGroup) {
        Map<String, Checkpoint> streamCheckpoints = checkpoints.get(tenant.tenantId());
        if (streamCheckpoints == null) {
            return Promise.of(Optional.empty());
        }
        String key = stream + ":" + consumerGroup;
        return Promise.of(Optional.ofNullable(streamCheckpoints.get(key)));
    }

    @Override
    public Promise<Checkpoint> commitCheckpoint(TenantContext tenant, String stream, String consumerGroup, Offset offset, String idempotencyKey) {
        String key = stream + ":" + consumerGroup;
        Map<String, Checkpoint> streamCheckpoints = checkpoints.computeIfAbsent(tenant.tenantId(), k -> new ConcurrentHashMap<>());
        Checkpoint checkpoint = new Checkpoint(stream, consumerGroup, offset, Instant.now(), idempotencyKey);
        streamCheckpoints.put(key, checkpoint);
        return Promise.of(checkpoint);
    }

    @Override
    public Promise<Boolean> deleteCheckpoint(TenantContext tenant, String stream, String consumerGroup) {
        Map<String, Checkpoint> streamCheckpoints = checkpoints.get(tenant.tenantId());
        if (streamCheckpoints == null) {
            return Promise.of(false);
        }
        String key = stream + ":" + consumerGroup;
        return Promise.of(streamCheckpoints.remove(key) != null);
    }

    @Override
    public Promise<Map<String, Checkpoint>> getAllCheckpointsWithMetadata(TenantContext tenant) {
        Map<String, Checkpoint> streamCheckpoints = checkpoints.get(tenant.tenantId());
        return Promise.of(streamCheckpoints != null ? Map.copyOf(streamCheckpoints) : Map.of());
    }

    @Override
    public Promise<List<EventEntry>> replay(TenantContext tenant, ReplaySpec spec) {
        List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
        long fromOffsetValue = numericOffsetValue(spec.fromOffset());
        long toOffsetValue = spec.toOffset().value().equals("-1") ? Long.MAX_VALUE : numericOffsetValue(spec.toOffset());

        return Promise.of(entries.stream()
            .filter(e -> headerOffset(e) >= fromOffsetValue && headerOffset(e) <= toOffsetValue)
            .filter(e -> spec.eventTypes().isEmpty() || spec.eventTypes().contains(e.eventType()))
            .toList());
    }

    @Override
    public Promise<Void> unsubscribe(TenantContext tenant, SubscriptionId subscriptionId) {
        // P3-03: For in-memory implementation, cancellation is handled by the Subscription.cancel() method
        // which removes the listener from tailListeners. This is a no-op for tracking purposes.
        return Promise.of(null);
    }
}
