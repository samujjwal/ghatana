package com.ghatana.aep.event;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory EventCloud implementation for testing and development.
 *
 * <p>This implementation stores events in memory and is suitable for:
 * <ul>
 *   <li>Unit testing</li>
 *   <li>Integration testing</li>
 *   <li>Development environments</li>
 * </ul>
 *
 * <p>Supports automatic TTL-based eviction: when a {@code defaultTtl} is configured
 * (via {@link #InMemoryEventCloud(Duration)}), events older than the TTL are purged on
 * every {@link #append} call. Use {@link #purgeExpired()} to trigger eviction manually.
 *
 * <p>For production use, configure a durable EventCloud implementation
 * such as PostgresEventCloudAdapter or KafkaEventCloudAdapter.
 *
 * @doc.type class
 * @doc.purpose In-memory EventCloud for testing and development with optional TTL eviction
 * @doc.layer product
 * @doc.pattern Adapter
 * @since 1.0.0
 */
public class InMemoryEventCloud implements EventCloud {

    /** Sentinel value meaning "no TTL enforcement". */
    private static final Duration NO_TTL = null;

    private final Duration defaultTtl;
    private final Map<String, List<StoredEvent>> eventsByTenant = new ConcurrentHashMap<>();
    private final Map<String, Map<String, CheckpointRecord>> checkpointsByTenant = new ConcurrentHashMap<>();
    private final List<SubscriptionEntry> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicLong eventCounter = new AtomicLong(0);
    private volatile long lastGlobalPurgeAtMs;

    /** Create an instance with no TTL enforcement. */
    public InMemoryEventCloud() {
        this.defaultTtl = NO_TTL;
    }

    /**
     * Create an instance with automatic TTL eviction.
     *
     * <p>Events older than {@code defaultTtl} are purged on every {@link #append} call.
     * Pass {@code null} or {@link Duration#ZERO} to disable TTL enforcement.
     *
     * @param defaultTtl TTL for all events (null / ZERO = no enforcement)
     */
    public InMemoryEventCloud(Duration defaultTtl) {
        this.defaultTtl = (defaultTtl != null && !defaultTtl.isZero() && !defaultTtl.isNegative())
            ? defaultTtl : NO_TTL;
    }

    @Override
    public String append(String tenantId, String eventType, byte[] payload) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(payload, "payload required");

        // Enforce TTL before adding new events to keep memory bounded without scanning all tenants.
        if (defaultTtl != null) {
            purgeExpired(tenantId);
        }

        String eventId = tenantId + "-" + eventCounter.incrementAndGet();
        StoredEvent event = new StoredEvent(eventId, eventType, payload, System.currentTimeMillis());

        eventsByTenant.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>()).add(event);

        // Notify matching subscribers
        for (SubscriptionEntry sub : subscriptions) {
            if (!sub.cancelled && sub.tenantId.equals(tenantId) && sub.eventType.equals(eventType)) {
                try {
                    sub.handler.handle(eventId, eventType, payload);
                } catch (Exception e) {
                    // Log and continue — don't let one subscriber failure block others
                }
            }
        }

        return eventId;
    }

    @Override
    public Subscription subscribe(String tenantId, String eventType, EventHandler handler) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(handler, "handler required");

        SubscriptionEntry entry = new SubscriptionEntry(tenantId, eventType, handler);
        subscriptions.add(entry);

        return new Subscription() {
            @Override
            public void cancel() {
                entry.cancelled = true;
                subscriptions.remove(entry);
            }

            @Override
            public boolean isCancelled() {
                return entry.cancelled;
            }
        };
    }

    @Override
    public boolean createCheckpoint(String tenantId, String checkpointId, Map<String, Object> metadata) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(checkpointId, "checkpointId required");
        long offset = eventsByTenant.getOrDefault(tenantId, List.of()).size();
        checkpointsByTenant.computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>())
            .put(checkpointId, new CheckpointRecord(offset, metadata == null ? Map.of() : Map.copyOf(metadata)));
        return true;
    }

    @Override
    public Map<String, Object> readCheckpoint(String tenantId, String checkpointId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(checkpointId, "checkpointId required");
        CheckpointRecord checkpoint = checkpointsByTenant.getOrDefault(tenantId, Map.of()).get(checkpointId);
        return checkpoint == null ? null : checkpoint.metadata();
    }

    @Override
    public boolean deleteCheckpoint(String tenantId, String checkpointId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(checkpointId, "checkpointId required");
        Map<String, CheckpointRecord> tenantCheckpoints = checkpointsByTenant.get(tenantId);
        return tenantCheckpoints != null && tenantCheckpoints.remove(checkpointId) != null;
    }

    @Override
    public List<CheckpointInfo> listCheckpoints(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        return checkpointsByTenant.getOrDefault(tenantId, Map.of()).entrySet().stream()
            .map(entry -> new CheckpointInfo(entry.getKey(), entry.getValue().offset(), entry.getValue().metadata()))
            .toList();
    }

    @Override
    public ReplayStatistics replay(String tenantId, String checkpointId, ReplayMode mode, EventHandler handler) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(checkpointId, "checkpointId required");
        Objects.requireNonNull(mode, "mode required");
        Objects.requireNonNull(handler, "handler required");

        long startedAt = System.currentTimeMillis();
        CheckpointRecord checkpoint = checkpointsByTenant.getOrDefault(tenantId, Map.of()).get(checkpointId);
        if (checkpoint == null) {
            throw new IllegalArgumentException("Unknown checkpoint: " + checkpointId);
        }

        List<StoredEvent> events = eventsByTenant.getOrDefault(tenantId, List.of());
        int processed = 0;
        for (int index = Math.toIntExact(Math.min(checkpoint.offset(), events.size())); index < events.size(); index++) {
            StoredEvent event = events.get(index);
            processed++;
            if (mode == ReplayMode.REPLAY_WITH_SIDE_EFFECTS) {
                handler.handle(event.eventId(), event.eventType(), event.payload());
            }
        }
        return new ReplayStatistics(processed, processed, System.currentTimeMillis() - startedAt);
    }

    /**
     * Get all events for a tenant (for testing).
     *
     * @param tenantId tenant identifier
     * @return list of stored events, or empty list if no events
     */
    public List<StoredEvent> getEvents(String tenantId) {
        return List.copyOf(eventsByTenant.getOrDefault(tenantId, List.of()));
    }

    /**
     * Clear all events (for testing).
     */
    public void clear() {
        eventsByTenant.clear();
        checkpointsByTenant.clear();
        subscriptions.clear();
        eventCounter.set(0);
    }

    /**
     * Purge all events that have exceeded the configured TTL.
     *
     * <p>This is called automatically on every {@link #append} when a TTL is configured,
     * but can also be called manually at any time.
     *
     * @return number of events removed
     */
    public int purgeExpired() {
        if (defaultTtl == null) {
            return 0;
        }
        long cutoffMs = System.currentTimeMillis() - defaultTtl.toMillis();
        int removed = 0;
        for (List<StoredEvent> events : eventsByTenant.values()) {
            removed += purgeExpired(events, cutoffMs);
        }
        lastGlobalPurgeAtMs = System.currentTimeMillis();
        return removed;
    }

    private void purgeExpired(String tenantId) {
        List<StoredEvent> events = eventsByTenant.get(tenantId);
        if (events == null || events.isEmpty()) {
            maybePurgeOtherTenants();
            return;
        }

        long cutoffMs = System.currentTimeMillis() - defaultTtl.toMillis();
        purgeExpired(events, cutoffMs);
        maybePurgeOtherTenants();
    }

    private void maybePurgeOtherTenants() {
        long nowMs = System.currentTimeMillis();
        long ttlMs = Math.max(defaultTtl.toMillis(), 1L);
        if (nowMs - lastGlobalPurgeAtMs < ttlMs) {
            return;
        }
        purgeExpired();
    }

    private static int purgeExpired(List<StoredEvent> events, long cutoffMs) {
        int before = events.size();
        events.removeIf(event -> event.timestamp() < cutoffMs);
        return before - events.size();
    }

    /**
     * Total number of events across all tenants.
     *
     * @return event count
     */
    public int size() {
        return eventsByTenant.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Stored event record.
     *
     * @param eventId   unique event identifier
     * @param eventType event type string
     * @param payload   raw event payload
     * @param timestamp epoch millis when appended
     */
    public record StoredEvent(String eventId, String eventType, byte[] payload, long timestamp) {}

    private record CheckpointRecord(long offset, Map<String, Object> metadata) {}

    private static class SubscriptionEntry {
        final String tenantId;
        final String eventType;
        final EventHandler handler;
        volatile boolean cancelled;

        SubscriptionEntry(String tenantId, String eventType, EventHandler handler) {
            this.tenantId = tenantId;
            this.eventType = eventType;
            this.handler = handler;
        }
    }
}
