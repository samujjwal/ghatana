package com.ghatana.datacloud.launcher.compaction;

import com.ghatana.datacloud.client.autonomy.AutonomyController;
import com.ghatana.datacloud.client.autonomy.AutonomyLevel;
import com.ghatana.datacloud.storage.H2SovereignEntityStore;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type class
 * @doc.purpose Runs scheduled tombstone compaction for sovereign H2 storage under autonomy control
 * @doc.layer product
 * @doc.pattern BackgroundTask
 */
public final class StorageCompactionTask implements AutoCloseable {

    public static final String ACTION_TYPE = "storage.compaction";

    private static final Logger LOG = LoggerFactory.getLogger(StorageCompactionTask.class);

    private final H2SovereignEntityStore entityStore;
    private final AutonomyController autonomyController;
    private final AuditService auditService;
    private final MetricsCollector metricsCollector;
    private final Eventloop eventloop;
    private final ScheduledExecutorService scheduler;
    private final long intervalSeconds;
    private final int tombstoneThreshold;
    private final AtomicBoolean running;
    private final AtomicLong lastRunEpochMillis;

    public StorageCompactionTask(
        H2SovereignEntityStore entityStore,
        AutonomyController autonomyController,
        AuditService auditService,
        MetricsCollector metricsCollector,
        Eventloop eventloop,
        long intervalSeconds,
        int tombstoneThreshold
    ) {
        this.entityStore = entityStore;
        this.autonomyController = autonomyController;
        this.auditService = auditService;
        this.metricsCollector = metricsCollector;
        this.eventloop = eventloop;
        this.intervalSeconds = intervalSeconds;
        this.tombstoneThreshold = tombstoneThreshold;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dc-storage-compaction");
            thread.setDaemon(true);
            return thread;
        });
        this.running = new AtomicBoolean(false);
        this.lastRunEpochMillis = new AtomicLong(0L);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(
            () -> eventloop.execute(this::runCompactionPass),
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS);
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", running.get() ? "DEGRADED" : "UP");
        status.put("enabled", true);
        status.put("intervalSeconds", intervalSeconds);
        status.put("tombstoneThreshold", tombstoneThreshold);
        status.put("lastRunAt", lastRunEpochMillis.get() == 0L ? null : Instant.ofEpochMilli(lastRunEpochMillis.get()).toString());
        return status;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private void runCompactionPass() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        entityStore.countTombstones()
            .then(tombstoneCounts -> {
                Promise<Void> chain = Promise.complete();
                for (Map.Entry<String, Long> entry : tombstoneCounts.entrySet()) {
                    if (entry.getValue() >= tombstoneThreshold) {
                        chain = chain.then(() -> compactTenant(entry.getKey(), entry.getValue()));
                    }
                }
                return chain;
            })
            .whenComplete((ignored, exception) -> {
                if (exception != null) {
                    LOG.warn("Storage compaction cycle failed", exception);
                    metricsCollector.recordError("datacloud.storage.compaction.failed", new Exception(exception), Map.of());
                }
                lastRunEpochMillis.set(System.currentTimeMillis());
                running.set(false);
            });
    }

    private Promise<Void> compactTenant(String tenantId, long tombstones) {
        long startedAt = System.currentTimeMillis();
        return resolveLevel(tenantId)
            .then(level -> {
                if (level.getOrdinalLevel() < AutonomyLevel.NOTIFY.getOrdinalLevel()) {
                    metricsCollector.incrementCounter("datacloud.storage.compaction.skipped", Map.of("tenant", tenantId, "reason", "autonomy"));
                    return Promise.complete();
                }
                return entityStore.compactTenant(tenantId)
                    .then(removed -> emitCompactionOutcome(tenantId, tombstones, removed, startedAt), exception -> {
                        metricsCollector.recordError("datacloud.storage.compaction.failed", new Exception(exception), Map.of("tenant", tenantId));
                        return Promise.ofException(exception);
                    });
            });
    }

    private Promise<Void> emitCompactionOutcome(String tenantId, long tombstones, int removed, long startedAt) {
        metricsCollector.incrementCounter("datacloud.storage.compaction.runs", Map.of("tenant", tenantId, "result", "success"));
        metricsCollector.recordTimer("datacloud.storage.compaction.duration", System.currentTimeMillis() - startedAt, "tenant", tenantId);

        if (auditService == null) {
            return Promise.complete();
        }

        AuditEvent event = AuditEvent.builder()
            .tenantId(tenantId)
            .eventType("storage.compaction.completed")
            .principal("system")
            .resourceType("storage")
            .resourceId("sovereign-h2")
            .success(true)
            .detail("tombstonesObserved", tombstones)
            .detail("rowsRemoved", removed)
            .build();
        return auditService.record(event);
    }

    private Promise<AutonomyLevel> resolveLevel(String tenantId) {
        if (autonomyController == null) {
            return Promise.of(AutonomyLevel.NOTIFY);
        }
        return autonomyController.listAllStates(tenantId)
            .then(states -> {
                if (!states.containsKey(ACTION_TYPE)) {
                    return autonomyController.setLevel(
                        ACTION_TYPE,
                        tenantId,
                        AutonomyLevel.NOTIFY,
                        "Default sovereign storage compaction baseline")
                        .map(AutonomyController.TransitionResult::getNewLevel);
                }
                return Promise.of(states.get(ACTION_TYPE).getCurrentLevel());
            });
    }
}
