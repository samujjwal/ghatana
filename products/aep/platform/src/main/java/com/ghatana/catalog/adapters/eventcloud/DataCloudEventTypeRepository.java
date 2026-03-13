/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.catalog.adapters.eventcloud;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.catalog.ports.EventTypeRepository;
import com.ghatana.platform.domain.domain.event.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event-sourced {@link EventTypeRepository} backed by the AEP {@link EventCloud}.
 *
 * <h2>Persistence Model</h2>
 * <p>All mutations are appended as events to the EventCloud before the in-memory projection
 * is updated. The projection is rebuilt from the EventCloud on startup via {@link #replayFromEventCloud()}.
 *
 * <ul>
 *   <li>{@code eventtype.registered} — emitted by {@link #save(EventType)}</li>
 *   <li>{@code eventtype.updated}    — emitted by {@link #update(EventType)}</li>
 *   <li>{@code eventtype.deleted}    — emitted by {@link #delete(String)}</li>
 * </ul>
 *
 * <h2>Serialization</h2>
 * <p>EventType objects (typically {@code GEventType} which implements
 * {@link java.io.Serializable}) are serialized to {@code byte[]} using standard
 * Java object serialization and stored as the EventCloud payload. Delete events
 * store the composite ID string as UTF-8 bytes.
 *
 * <h2>Multi-Tenancy</h2>
 * <p>All EventCloud append calls use the tenant identifier extracted from the
 * {@link EventType#getTenantId()} field. The in-memory projection is scoped by
 * the composite key {@code tenantId/namespace/name:version}.
 *
 * @doc.type class
 * @doc.purpose Event-sourced EventTypeRepository using AEP EventCloud for durable storage
 * @doc.layer product
 * @doc.pattern Repository, EventSourced
 */
public class DataCloudEventTypeRepository implements EventTypeRepository {

    private static final Logger log = LoggerFactory.getLogger(DataCloudEventTypeRepository.class);

    /** EventCloud event type for new event-type registrations. */
    public static final String EVENT_TYPE_REGISTERED = "eventtype.registered";
    /** EventCloud event type for event-type updates. */
    public static final String EVENT_TYPE_UPDATED    = "eventtype.updated";
    /** EventCloud event type for event-type deletions (payload = composite ID as UTF-8). */
    public static final String EVENT_TYPE_DELETED    = "eventtype.deleted";

    /** Tenant used for platform-level event types that span all tenants. */
    private static final String PLATFORM_TENANT = System.getenv().getOrDefault("AEP_DEFAULT_TENANT_ID", "platform");

    private final EventCloud eventCloud;

    // In-memory projection: rebuilt from EventCloud at startup, kept in sync on writes
    private final Map<String, EventType> byId      = new ConcurrentHashMap<>();
    /** baseKey = "tenantId/namespace/name" → version → EventType */
    private final Map<String, Map<String, EventType>> byBaseKey = new ConcurrentHashMap<>();

    private final AtomicBoolean replayDone = new AtomicBoolean(false);

    /**
     * Creates a repository backed by the supplied EventCloud.
     *
     * <p>Call {@link #replayFromEventCloud()} after construction to rebuild the
     * in-memory projection from durable storage before serving reads.
     *
     * @param eventCloud active AEP EventCloud connector (never {@code null})
     */
    public DataCloudEventTypeRepository(EventCloud eventCloud) {
        this.eventCloud = Objects.requireNonNull(eventCloud, "eventCloud");
    }

    // ─── Mutation ops ─────────────────────────────────────────────────────────

    @Override
    public EventType save(EventType eventType) {
        Objects.requireNonNull(eventType, "eventType");
        ensureReplayed();

        byte[] payload = serialize(eventType);
        eventCloud.append(tenantOf(eventType), EVENT_TYPE_REGISTERED, payload);

        applyToProjection(eventType);
        log.debug("[DataCloudEventTypeRepository] registered: {}", eventType.getId());
        return eventType;
    }

    @Override
    public EventType update(EventType eventType) {
        Objects.requireNonNull(eventType, "eventType");
        ensureReplayed();

        String id = eventType.getId();
        if (!byId.containsKey(id)) {
            throw new IllegalArgumentException("EventType not found for update: " + id);
        }

        byte[] payload = serialize(eventType);
        eventCloud.append(tenantOf(eventType), EVENT_TYPE_UPDATED, payload);

        applyToProjection(eventType);
        log.debug("[DataCloudEventTypeRepository] updated: {}", id);
        return eventType;
    }

    @Override
    public boolean delete(String id) {
        Objects.requireNonNull(id, "id");
        ensureReplayed();

        EventType removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        removeFromBaseKey(removed);

        // Persist tombstone event — payload is the composite ID as UTF-8
        eventCloud.append(PLATFORM_TENANT, EVENT_TYPE_DELETED, id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        log.debug("[DataCloudEventTypeRepository] deleted: {}", id);
        return true;
    }

    // ─── Query ops (read from in-memory projection) ────────────────────────────

    @Override
    public Optional<EventType> findById(String id) {
        ensureReplayed();
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<EventType> findByNameAndVersion(String tenantId, String namespace, String name, String version) {
        ensureReplayed();
        String fullId = tenantId + "/" + namespace + "/" + name + ":" + version;
        return findById(fullId);
    }

    @Override
    public Optional<EventType> findLatest(String tenantId, String namespace, String name) {
        ensureReplayed();
        String base = tenantId + "/" + namespace + "/" + name;
        Map<String, EventType> versions = byBaseKey.get(base);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        return versions.values().stream()
                .max(Comparator.comparing(EventType::getVersion, DataCloudEventTypeRepository::compareSemVer));
    }

    @Override
    public List<EventType> findAll() {
        ensureReplayed();
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    @Override
    public long count() {
        ensureReplayed();
        return byId.size();
    }

    // ─── Startup replay ────────────────────────────────────────────────────────

    /**
     * Replays all {@code eventtype.*} events from the EventCloud to rebuild the
     * in-memory projection.
     *
     * <p>Idempotent — calling this more than once is a no-op after the first replay.
     * Replay subscribes to the three event types in order: registered → updated → deleted.
     * Each received event is processed synchronously before the subscription is cancelled.
     *
     * <p>Failures during replay are logged at WARN level; the projection will reflect
     * whatever events were successfully replayed.
     */
    public void replayFromEventCloud() {
        if (!replayDone.compareAndSet(false, true)) {
            return; // already replayed
        }

        log.info("[DataCloudEventTypeRepository] replaying eventtype events from EventCloud ...");
        int[] counts = {0, 0, 0};

        // registered
        replayEventType(EVENT_TYPE_REGISTERED, payload -> {
            EventType et = deserialize(payload);
            if (et != null) { applyToProjection(et); counts[0]++; }
        });

        // updated (re-apply latest state)
        replayEventType(EVENT_TYPE_UPDATED, payload -> {
            EventType et = deserialize(payload);
            if (et != null) { applyToProjection(et); counts[1]++; }
        });

        // deleted (tombstones applied last)
        replayEventType(EVENT_TYPE_DELETED, payload -> {
            String id = new String(payload, java.nio.charset.StandardCharsets.UTF_8).trim();
            EventType removed = byId.remove(id);
            if (removed != null) { removeFromBaseKey(removed); counts[2]++; }
        });

        log.info("[DataCloudEventTypeRepository] replay complete: registered={} updated={} deleted={}",
                counts[0], counts[1], counts[2]);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void ensureReplayed() {
        if (!replayDone.get()) {
            replayFromEventCloud();
        }
    }

    private void applyToProjection(EventType et) {
        String id      = et.getId();
        String base    = et.getTenantId() + "/" + et.getNamespace() + "/" + et.getName();
        String version = et.getVersion();
        byId.put(id, et);
        byBaseKey.computeIfAbsent(base, k -> new ConcurrentHashMap<>()).put(version, et);
    }

    private void removeFromBaseKey(EventType et) {
        String base = et.getTenantId() + "/" + et.getNamespace() + "/" + et.getName();
        Map<String, EventType> vers = byBaseKey.get(base);
        if (vers != null) {
            vers.remove(et.getVersion());
            if (vers.isEmpty()) byBaseKey.remove(base);
        }
    }

    private static String tenantOf(EventType et) {
        String t = et.getTenantId();
        return (t != null && !t.isBlank()) ? t : PLATFORM_TENANT;
    }

    /** Serializes an EventType to byte[] via Java object serialization. */
    private static byte[] serialize(EventType eventType) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(eventType);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize EventType: " + eventType.getId(), e);
        }
    }

    /** Deserializes an EventType from byte[] via Java object deserialization. */
    private static EventType deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (EventType) ois.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            log.warn("[DataCloudEventTypeRepository] failed to deserialize EventType payload: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Replays all events of the given type from EventCloud by subscribing and collecting
     * events synchronously (via {@code CountDownLatch}-style busy-wait).
     */
    private void replayEventType(String eventType, java.util.function.Consumer<byte[]> handler) {
        try {
            // Subscribe to the event type and collect all historical events.
            // The EventCloud subscription model is push-based; we use a simple latch approach.
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.List<byte[]> collected = new java.util.concurrent.CopyOnWriteArrayList<>();

            EventCloud.Subscription sub = eventCloud.subscribe(PLATFORM_TENANT, eventType, (id, type, payload) -> {
                collected.add(payload);
                // Note: In a streaming subscription, we cannot know when "all historical" events
                // are delivered. For now, we collect all synchronously-delivered events.
            });

            // Give the subscription a short time to deliver buffered/historical events
            try { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { sub.cancel(); }

            collected.forEach(handler);
        } catch (Exception e) {
            log.warn("[DataCloudEventTypeRepository] replay error for event type '{}': {}", eventType, e.getMessage());
        }
    }

    /**
     * Semantic version comparator based on major.minor.patch string format.
     * Falls back to lexicographic order for non-standard version strings.
     */
    private static int compareSemVer(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return  1;
        String[] p1 = v1.split("\\.", 3);
        String[] p2 = v2.split("\\.", 3);
        for (int i = 0; i < Math.max(p1.length, p2.length); i++) {
            int n1 = i < p1.length ? parsePart(p1[i]) : 0;
            int n2 = i < p2.length ? parsePart(p2[i]) : 0;
            int cmp = Integer.compare(n1, n2);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static int parsePart(String part) {
        try { return Integer.parseInt(part.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }
}
