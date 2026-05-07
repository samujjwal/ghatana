/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.storage;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automated data lifecycle manager that runs periodic tier-demotion sweeps
 * across all registered AEP entity collections.
 *
 * <h3>Lifecycle Cascade</h3>
 * <pre>
 * HOT  (0–1h idle)   → WARM  (demote after {@value DEFAULT_HOT_IDLE_HOURS}h)
 * WARM (0–7d idle)   → COOL  (demote after {@value DEFAULT_WARM_IDLE_DAYS}d)
 * COOL (0–90d idle)  → COLD  (demote after {@value DEFAULT_COOL_IDLE_DAYS}d)
 * COLD               → retained (purged by AepDataRetentionService)
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * DataLifecycleManager mgr = new DataLifecycleManager(tierManager, meterRegistry);
 * mgr.registerCollection("tenant-abc", "aep_patterns");
 * mgr.registerCollection("tenant-abc", "aep_pipelines");
 *
 * // Run a full lifecycle sweep (call from an Eventloop scheduler)
 * mgr.runLifecycleSweep()
 *    .whenResult(report -> log.info("Sweep complete: {}", report));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Automated data lifecycle management — tier demotion and aging across HOT/WARM/COOL/COLD
 * @doc.layer product
 * @doc.pattern Service, Scheduler
 * @since 1.0.0
 */
public final class DataLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(DataLifecycleManager.class);

    /** Default HOT idle threshold before demotion to WARM (1 hour). */
    public static final int DEFAULT_HOT_IDLE_HOURS = 1;
    /** Default WARM idle threshold before demotion to COOL (7 days). */
    public static final int DEFAULT_WARM_IDLE_DAYS = 7;
    /** Default COOL idle threshold before demotion to COLD (90 days). */
    public static final int DEFAULT_COOL_IDLE_DAYS = 90;

    private final StorageTierManager tierManager;
    private final Duration hotIdleThreshold;
    private final Duration warmIdleThreshold;
    private final Duration coolIdleThreshold;

    /** Registered (tenantId, collection) pairs for lifecycle management. */
    private final List<CollectionRef> registeredCollections = new ArrayList<>();

    private final AtomicBoolean sweepRunning = new AtomicBoolean(false);

    // Metrics
    private final Counter totalDemotions;
    private final Counter sweepErrors;
    private final Timer sweepTimer;

    /**
     * Constructs a lifecycle manager with default tier thresholds.
     *
     * @param tierManager   the tier manager for data movement
     * @param meterRegistry Micrometer registry for observability
     */
    public DataLifecycleManager(StorageTierManager tierManager, MeterRegistry meterRegistry) {
        this(
                tierManager,
                Duration.ofHours(DEFAULT_HOT_IDLE_HOURS),
                Duration.ofDays(DEFAULT_WARM_IDLE_DAYS),
                Duration.ofDays(DEFAULT_COOL_IDLE_DAYS),
                meterRegistry
        );
    }

    /**
     * Constructs a lifecycle manager with custom tier thresholds.
     *
     * @param tierManager        the tier manager for data movement
     * @param hotIdleThreshold   time before HOT → WARM demotion
     * @param warmIdleThreshold  time before WARM → COOL demotion
     * @param coolIdleThreshold  time before COOL → COLD demotion
     * @param meterRegistry      Micrometer registry
     */
    public DataLifecycleManager(
            StorageTierManager tierManager,
            Duration hotIdleThreshold,
            Duration warmIdleThreshold,
            Duration coolIdleThreshold,
            MeterRegistry meterRegistry) {
        this.tierManager = Objects.requireNonNull(tierManager, "tierManager");
        this.hotIdleThreshold  = Objects.requireNonNull(hotIdleThreshold,  "hotIdleThreshold");
        this.warmIdleThreshold = Objects.requireNonNull(warmIdleThreshold, "warmIdleThreshold");
        this.coolIdleThreshold = Objects.requireNonNull(coolIdleThreshold, "coolIdleThreshold");
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.totalDemotions = Counter.builder("aep.lifecycle.demotions.total")
                .description("Total entity demotions across all tiers and collections")
                .register(meterRegistry);
        this.sweepErrors = Counter.builder("aep.lifecycle.sweep.errors")
                .description("Lifecycle sweep failures")
                .register(meterRegistry);
        this.sweepTimer = Timer.builder("aep.lifecycle.sweep.duration")
                .description("Duration of a full lifecycle sweep")
                .register(meterRegistry);
    }

    // =========================================================================
    // Collection registration
    // =========================================================================

    /**
     * Registers a (tenantId, collection) pair for automatic lifecycle management.
     *
     * <p>Duplicate registrations are idempotent.
     *
     * @param tenantId   tenant identifier
     * @param collection base collection name (without tier suffix)
     */
    public void registerCollection(String tenantId, String collection) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collection, "collection");
        CollectionRef ref = new CollectionRef(tenantId, collection);
        if (!registeredCollections.contains(ref)) {
            registeredCollections.add(ref);
            log.info("[lifecycle] Registered collection tenant={} coll={}", tenantId, collection);
        }
    }

    /**
     * Removes a collection from lifecycle management.
     *
     * @param tenantId   tenant identifier
     * @param collection base collection name
     */
    public void deregisterCollection(String tenantId, String collection) {
        registeredCollections.remove(new CollectionRef(tenantId, collection));
        log.info("[lifecycle] Deregistered collection tenant={} coll={}", tenantId, collection);
    }

    /**
     * Returns the number of collections under management.
     *
     * @return registered collection count
     */
    public int registeredCollectionCount() {
        return registeredCollections.size();
    }

    // =========================================================================
    // Lifecycle sweep
    // =========================================================================

    /**
     * Runs a full lifecycle sweep across all registered collections.
     *
     * <p>The sweep is idempotent — if a sweep is already running, this call
     * returns immediately with an empty report. The cascade order is:
     * <ol>
     *   <li>COOL → COLD (oldest data first, prevents COOL filling up)</li>
     *   <li>WARM → COOL</li>
     *   <li>HOT  → WARM  (last, so new data is not immediately demoted)</li>
     * </ol>
     *
     * @return promise of a {@link SweepReport} summarising the sweep
     */
    public Promise<SweepReport> runLifecycleSweep() {
        if (!sweepRunning.compareAndSet(false, true)) {
            log.warn("[lifecycle] Sweep already running — skipping overlap");
            return Promise.of(SweepReport.empty());
        }
        Instant sweepStart = Instant.now();
        log.info("[lifecycle] Starting lifecycle sweep over {} collections", registeredCollections.size());

        return sweepTimer.record(() -> sweepAllCollections(sweepStart))
                .whenComplete((report, e) -> {
                    sweepRunning.set(false);
                    if (e != null) {
                        sweepErrors.increment();
                        log.error("[lifecycle] Sweep failed: {}", e.getMessage(), e);
                    } else {
                        log.info("[lifecycle] Sweep complete in {}ms — demoted {} entities (HOT→WARM={} WARM→COOL={} COOL→COLD={})",
                                Duration.between(sweepStart, Instant.now()).toMillis(),
                                report.totalDemotions(),
                                report.hotToWarm(), report.warmToCool(), report.coolToCold());
                    }
                });
    }

    private Promise<SweepReport> sweepAllCollections(Instant sweepStart) {
        if (registeredCollections.isEmpty()) {
            return Promise.of(new SweepReport(0, 0, 0, Duration.ZERO, true));
        }
        Instant hotCutoff  = sweepStart.minus(hotIdleThreshold);
        Instant warmCutoff = sweepStart.minus(warmIdleThreshold);
        Instant coolCutoff = sweepStart.minus(coolIdleThreshold);

        List<Promise<CollectionSweepResult>> sweeps = new ArrayList<>();
        for (CollectionRef ref : registeredCollections) {
            sweeps.add(sweepCollection(ref.tenantId(), ref.collection(),
                    hotCutoff, warmCutoff, coolCutoff));
        }
        return Promises.toList(sweeps).map(results -> {
            int hotToWarm = 0, warmToCool = 0, coolToCold = 0;
            for (CollectionSweepResult r : results) {
                hotToWarm  += r.hotToWarm();
                warmToCool += r.warmToCool();
                coolToCold += r.coolToCold();
            }
            int total = hotToWarm + warmToCool + coolToCold;
            totalDemotions.increment(total);
            return new SweepReport(hotToWarm, warmToCool, coolToCold,
                    Duration.between(sweepStart, Instant.now()), false);
        });
    }

    private Promise<CollectionSweepResult> sweepCollection(
            String tenantId, String collection,
            Instant hotCutoff, Instant warmCutoff, Instant coolCutoff) {
        // Run COOL→COLD first to clear space
        return tierManager.demoteCoolToCold(tenantId, collection, coolCutoff)
                .then(coolToCold ->
                        tierManager.demoteWarmToCool(tenantId, collection, warmCutoff)
                                .then(warmToCool ->
                                        tierManager.demoteIdleEntities(tenantId, collection, hotCutoff)
                                                .map(hotToWarm -> new CollectionSweepResult(hotToWarm, warmToCool, coolToCold))
                                )
                )
                .then(
                    result -> Promise.of(result),
                    e -> {
                        log.warn("[lifecycle] Collection sweep failed tenant={} coll={}: {}",
                                tenantId, collection, e.getMessage());
                        return Promise.of(new CollectionSweepResult(0, 0, 0));
                    }
                );
    }

    /**
     * Immediately demotes all entities in a collection to COLD (used for
     * tenant offboarding / compliance hold).
     *
     * @param tenantId   tenant identifier
     * @param collection base collection name
     * @return promise completing when all entities are in COLD
     */
    public Promise<Integer> archiveCollection(String tenantId, String collection) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collection, "collection");
        Instant now = Instant.now();
        return tierManager.demoteIdleEntities(tenantId, collection, now)
                .then(h -> tierManager.demoteWarmToCool(tenantId, collection, now)
                        .then(w -> tierManager.demoteCoolToCold(tenantId, collection, now)
                                .map(c -> h + w + c)));
    }

    // =========================================================================
    // Value types
    // =========================================================================

    /**
     * Summary of a completed lifecycle sweep.
     *
     * @param hotToWarm      entities demoted from HOT to WARM
     * @param warmToCool     entities demoted from WARM to COOL
     * @param coolToCold     entities demoted from COOL to COLD
     * @param elapsed        total sweep duration
     * @param skipped        true if a sweep was already running
     */
    public record SweepReport(
            int hotToWarm,
            int warmToCool,
            int coolToCold,
            Duration elapsed,
            boolean skipped
    ) {
        /** Total entities moved in this sweep. */
        public int totalDemotions() { return hotToWarm + warmToCool + coolToCold; }

        static SweepReport empty() {
            return new SweepReport(0, 0, 0, Duration.ZERO, true);
        }
    }

    private record CollectionRef(String tenantId, String collection) {}

    private record CollectionSweepResult(int hotToWarm, int warmToCool, int coolToCold) {}
}
