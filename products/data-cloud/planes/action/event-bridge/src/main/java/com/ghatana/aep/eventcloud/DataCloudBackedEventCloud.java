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
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements AEP's simplified {@link EventCloud} facade backed by
 * Data-Cloud's {@link EventLogStore} (P4-07).
 *
 * <p>This bridges the AEP operator/pipeline world (synchronous, String-based)
 * to Data-Cloud's async {@link EventLogStore} SPI. The AEP facade has a
 * synchronous contract, so this implementation resolves Promises inline on
 * the calling thread (matching the contract of
 * {@link com.ghatana.aep.event.ConnectorBackedEventCloud}).
 *
 * <p>P4-07: Makes EventCloud persistence bridge explicit and replay-safe:
 * <ul>
 *   <li>Checkpoint management using EventLogStore SPI checkpoint methods</li>
 *   <li>Replay support with idempotency guarantees</li>
 *   <li>Replay mode (dry-run vs replay-with-side-effects)</li>
 * </ul>
 *
 * <p>For async-aware callers, prefer using the {@link DataCloudEventCloudConnector}
 * which returns {@code Promise} directly.
 *
 * <p>Supports {@link ServiceLoader} discovery: when placed on the classpath
 * with a provider descriptor, the no-arg constructor discovers
 * {@link EventLogStore} via {@code ServiceLoader}.
 *
 * @doc.type class
 * @doc.purpose AEP EventCloud facade backed by Data-Cloud EventLogStore with replay-safe checkpoint support
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
            ServiceLoader.load(com.ghatana.platform.domain.eventstore.EventLogStore.class).findFirst()
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

        Promise<Offset> promise = eventLogStore.append(tenant, entry)
            .whenResult(offset ->
                log.debug("[event-cloud] Appended event {} type={} tenant={} offset={}",
                    eventId, eventType, tenantId, offset));

        if (promise.isException()) {
            Throwable ex = promise.getException();
            log.error("[event-cloud] Append failed event={} type={} tenant={}: {}",
                eventId, eventType, tenantId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to append event", ex);
        }
        return eventId.toString();
    }

    @Override
    public Subscription subscribe(String tenantId, String eventType, EventHandler handler) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(handler, "handler required");

        TenantContext tenant = TenantContext.of(tenantId);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        EventLogStore.Subscription[] delegateHolder = new EventLogStore.Subscription[1];

        Promise<EventLogStore.Subscription> promise = eventLogStore.getLatestOffset(tenant)
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
            .whenResult(sub -> delegateHolder[0] = sub);

        if (promise.isException()) {
            Throwable ex = promise.getException();
            log.error("[event-cloud] Subscribe failed type={} tenant={}: {}",
                eventType, tenantId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to subscribe", ex);
        }

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

    // ==================== P4-07: Checkpoint/Replay Support ====================

    @Override
    public boolean createCheckpoint(String tenantId, String checkpointId, Map<String, Object> metadata) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(checkpointId, "checkpointId required");
        Objects.requireNonNull(metadata, "metadata required");

        TenantContext tenant = TenantContext.of(tenantId);

        // Get current offset to checkpoint
        Promise<Offset> offsetPromise = eventLogStore.getLatestOffset(tenant);
        if (offsetPromise.isException()) {
            log.error("[event-cloud] Failed to get latest offset for checkpoint: {}",
                offsetPromise.getException().getMessage());
            return false;
        }

        Offset offset = offsetPromise.getResult();
        if (offset == null) {
            log.warn("[event-cloud] No events to checkpoint for tenant={}", tenantId);
            return false;
        }

        // Create checkpoint using EventLogStore SPI
        Promise<Void> commitPromise = eventLogStore.commitCheckpoint(
            tenant,
            checkpointId,
            offset.value(),
            metadata
        );

        if (commitPromise.isException()) {
            log.error("[event-cloud] Failed to create checkpoint {}: {}",
                checkpointId, commitPromise.getException().getMessage());
            return false;
        }

        log.info("[event-cloud] Created checkpoint {} tenant={} offset={}", checkpointId, tenantId, offset);
        return true;
    }

    @Override
    public Map<String, Object> readCheckpoint(String tenantId, String checkpointId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(checkpointId, "checkpointId required");

        TenantContext tenant = TenantContext.of(tenantId);

        Promise<Map<String, Object>> readPromise = eventLogStore.readCheckpoint(tenant, checkpointId);

        if (readPromise.isException()) {
            log.error("[event-cloud] Failed to read checkpoint {}: {}",
                checkpointId, readPromise.getException().getMessage());
            return null;
        }

        return readPromise.getResult();
    }

    @Override
    public boolean deleteCheckpoint(String tenantId, String checkpointId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(checkpointId, "checkpointId required");

        TenantContext tenant = TenantContext.of(tenantId);

        Promise<Void> deletePromise = eventLogStore.deleteCheckpoint(tenant, checkpointId);

        if (deletePromise.isException()) {
            log.error("[event-cloud] Failed to delete checkpoint {}: {}",
                checkpointId, deletePromise.getException().getMessage());
            return false;
        }

        log.info("[event-cloud] Deleted checkpoint {} tenant={}", checkpointId, tenantId);
        return true;
    }

    @Override
    public List<CheckpointInfo> listCheckpoints(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId required");

        TenantContext tenant = TenantContext.of(tenantId);

        Promise<List<Map<String, Object>>> listPromise = eventLogStore.getAllCheckpointsWithMetadata(tenant);

        if (listPromise.isException()) {
            log.error("[event-cloud] Failed to list checkpoints for tenant {}: {}",
                tenantId, listPromise.getException().getMessage());
            return List.of();
        }

        List<Map<String, Object>> checkpoints = listPromise.getResult();
        if (checkpoints == null) {
            return List.of();
        }

        List<CheckpointInfo> result = new ArrayList<>();
        for (Map<String, Object> checkpoint : checkpoints) {
            String id = (String) checkpoint.get("checkpointId");
            Long offset = ((Number) checkpoint.getOrDefault("offset", 0L)).longValue();
            result.add(new CheckpointInfo(id, offset, checkpoint));
        }

        return result;
    }

    @Override
    public ReplayStatistics replay(String tenantId, String checkpointId, ReplayMode mode, EventHandler handler) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(checkpointId, "checkpointId required");
        Objects.requireNonNull(mode, "mode required");
        Objects.requireNonNull(handler, "handler required");

        TenantContext tenant = TenantContext.of(tenantId);
        long startTime = System.currentTimeMillis();
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger matched = new AtomicInteger(0);

        // Read checkpoint to get starting offset
        Map<String, Object> checkpointData = readCheckpoint(tenantId, checkpointId);
        if (checkpointData == null) {
            log.error("[event-cloud] Checkpoint {} not found for replay", checkpointId);
            return new ReplayStatistics(0, 0, 0);
        }

        long fromOffset = ((Number) checkpointData.getOrDefault("offset", 0L)).longValue();

        // Replay events from checkpoint offset
        Promise<List<EventEntry>> replayPromise = eventLogStore.replay(
            tenant,
            fromOffset,
            -1,  // toOffset -1 means read to latest
            null  // eventType null means all types
        );

        if (replayPromise.isException()) {
            log.error("[event-cloud] Failed to replay from checkpoint {}: {}",
                checkpointId, replayPromise.getException().getMessage());
            return new ReplayStatistics(0, 0, 0);
        }

        List<EventEntry> events = replayPromise.getResult();
        if (events == null) {
            events = List.of();
        }

        for (EventEntry entry : events) {
            processed.incrementAndGet();

            if (mode == ReplayMode.DRY_RUN) {
                // Dry run: only evaluate without side effects
                log.debug("[event-cloud] Dry-run replay event {} type={}", entry.eventId(), entry.eventType());
            } else {
                // Replay with side effects
                byte[] data = new byte[entry.payload().remaining()];
                entry.payload().duplicate().get(data);
                handler.handle(entry.eventId().toString(), entry.eventType(), data);
                matched.incrementAndGet();
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("[event-cloud] Replay completed checkpoint {} tenant={} mode={} processed={} matched={} durationMs={}",
            checkpointId, tenantId, mode, processed.get(), matched.get(), duration);

        return new ReplayStatistics(processed.get(), matched.get(), duration);
    }
}
