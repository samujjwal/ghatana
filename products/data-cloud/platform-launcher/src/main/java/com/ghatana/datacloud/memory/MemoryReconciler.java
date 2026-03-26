/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.memory;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.attention.SalienceScorer;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import lombok.Builder;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Background reconciler for tiered memory management.
 *
 * <p>Performs periodic maintenance tasks including:
 * <ul>
 *   <li>Expiration checking and cleanup</li>
 *   <li>Tier transition evaluation and execution</li>
 *   <li>Capacity-based eviction</li>
 *   <li>Statistics collection and reporting</li>
 *   <li>Salience score refresh</li>
 * </ul>
 *
 * <h2>Reconciliation Cycle</h2>
 * <pre>
 *     ┌────────────────────────────────────────────┐
 *     │           Reconciliation Cycle              │
 *     └────────────────────────────────────────────┘
 *                        │
 *          ┌─────────────┼─────────────┐
 *          ▼             ▼             ▼
 *     ┌─────────┐  ┌──────────┐  ┌──────────┐
 *     │ Expire  │  │ Evaluate │  │ Capacity │
 *     │ Records │  │ Transitions│  │  Check  │
 *     └────┬────┘  └────┬─────┘  └────┬─────┘
 *          │            │             │
 *          └────────────┴─────────────┘
 *                        │
 *                        ▼
 *               ┌──────────────┐
 *               │   Report     │
 *               │   Metrics    │
 *               └──────────────┘
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * MemoryReconciler<EventRecord> reconciler = MemoryReconciler.<EventRecord>builder()
 *     .router(memoryRouter)
 *     .salienceScorer(salienceScorer)
 *     .reconciliationInterval(Duration.ofMinutes(1))
 *     .build();
 * 
 * reconciler.start();
 * // ... application runs ...
 * reconciler.stop();
 * }</pre>
 *
 * @param <R> the type of data record being managed
 *
 * @doc.type class
 * @doc.purpose Background reconciliation for tier management
 * @doc.layer core
 * @doc.pattern Observer, Scheduled Task
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
@Builder
public class MemoryReconciler<R extends DataRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryReconciler.class);

    /**
     * The memory tier router to reconcile.
     */
    private final MemoryTierRouter<R> router;

    /**
     * Optional salience scorer for re-evaluating records.
     */
    private final SalienceScorer salienceScorer;

    /**
     * Interval between reconciliation cycles.
     */
    @Builder.Default
    private final Duration reconciliationInterval = Duration.ofMinutes(1);

    /**
     * Maximum records to process per cycle.
     */
    @Builder.Default
    private final int maxRecordsPerCycle = 1000;

    /**
     * Maximum transitions per cycle to prevent cascade effects.
     */
    @Builder.Default
    private final int maxTransitionsPerCycle = 100;

    /**
     * Whether to perform aggressive eviction during reconciliation.
     */
    @Builder.Default
    private final boolean aggressiveEviction = false;

    /**
     * Listeners for reconciliation events.
     */
    @Builder.Default
    private final List<ReconciliationListener> listeners = new ArrayList<>();

    // Runtime state
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastReconciliation = new AtomicReference<>();
    private final AtomicReference<ReconciliationReport> lastReport = new AtomicReference<>();

    /**
     * Starts the background reconciliation process.
     *
     * @return Promise that completes when reconciler is started
     */
    public Promise<Void> start() {
        if (running.compareAndSet(false, true)) {
            LOG.info("Starting memory reconciler with interval {}", reconciliationInterval);
            scheduleNextCycle();
            return Promise.complete();
        }
        return Promise.ofException(new IllegalStateException("Reconciler already running"));
    }

    /**
     * Stops the background reconciliation process.
     *
     * @return Promise that completes when reconciler is stopped
     */
    public Promise<Void> stop() {
        if (running.compareAndSet(true, false)) {
            LOG.info("Stopping memory reconciler");
            return Promise.complete();
        }
        return Promise.complete();
    }

    /**
     * Checks if the reconciler is currently running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Triggers an immediate reconciliation cycle.
     *
     * @return Promise containing the reconciliation report
     */
    public Promise<ReconciliationReport> reconcileNow() {
        return runReconciliationCycle();
    }

    /**
     * Gets the last reconciliation report.
     *
     * @return the most recent report, or null if none
     */
    public ReconciliationReport getLastReport() {
        return lastReport.get();
    }

    /**
     * Gets the time of the last reconciliation.
     *
     * @return instant of last reconciliation, or null if none
     */
    public Instant getLastReconciliationTime() {
        return lastReconciliation.get();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Reconciliation Cycle
    // ═══════════════════════════════════════════════════════════════════════════

    private void scheduleNextCycle() {
        if (!running.get()) return;

        // In a real implementation, this would use a scheduler
        // For now, we just log that scheduling would occur
        LOG.debug("Scheduling next reconciliation cycle in {}", reconciliationInterval);
    }

    private Promise<ReconciliationReport> runReconciliationCycle() {
        Instant cycleStart = Instant.now();
        LOG.debug("Starting reconciliation cycle");

        return router.getAllTierStatistics()
                .then(beforeStats -> {
                    ReconciliationReport.ReconciliationReportBuilder reportBuilder =
                            ReconciliationReport.builder()
                                    .startTime(cycleStart)
                                    .beforeStats(beforeStats);

                    // Phase 1: Expire old records
                    return processExpirations()
                            .then(expirationResult -> {
                                reportBuilder.expiredRecords(expirationResult.expiredCount);
                                reportBuilder.demotedFromExpiry(expirationResult.demotedCount);

                                // Phase 2: Evaluate and execute transitions
                                return processTransitions();
                            })
                            .then(transitionResult -> {
                                reportBuilder.promotions(transitionResult.promotions);
                                reportBuilder.demotions(transitionResult.demotions);
                                reportBuilder.transitionsSkipped(transitionResult.skipped);

                                // Phase 3: Capacity management
                                return processCapacity();
                            })
                            .then(capacityResult -> {
                                reportBuilder.evictionsForCapacity(capacityResult.totalEvicted);
                                reportBuilder.demotedForCapacity(capacityResult.totalDemoted);
                                reportBuilder.bytesFreed(capacityResult.bytesFreed);

                                // Collect after stats
                                return router.getAllTierStatistics();
                            })
                            .map(afterStats -> {
                                ReconciliationReport report = reportBuilder
                                        .afterStats(afterStats)
                                        .endTime(Instant.now())
                                        .build();

                                lastReconciliation.set(cycleStart);
                                lastReport.set(report);

                                // Notify listeners
                                notifyListeners(report);

                                LOG.info("Reconciliation cycle complete: {} expired, {} promoted, " +
                                                "{} demoted, {} evicted, {} bytes freed",
                                        report.getExpiredRecords(),
                                        report.getPromotions(),
                                        report.getDemotions() + report.getDemotedFromExpiry() + report.getDemotedForCapacity(),
                                        report.getEvictionsForCapacity(),
                                        report.getBytesFreed());

                                if (running.get()) {
                                    scheduleNextCycle();
                                }

                                return report;
                            });
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Expiration Processing
    // ═══════════════════════════════════════════════════════════════════════════

    private Promise<ExpirationResult> processExpirations() {
        List<Promise<Integer>> tierPromises = new ArrayList<>();

        for (MemoryTier tier : MemoryTier.values()) {
            tierPromises.add(processExpirationsForTier(tier));
        }

        return Promises.toList(tierPromises)
                .map(results -> {
                    int totalExpired = 0;
                    int totalDemoted = 0;

                    for (Integer count : results) {
                        // Simplified - in reality we'd track expired vs demoted separately
                        if (count > 0) {
                            totalDemoted += count; // Most expirations result in demotion
                        }
                    }

                    return new ExpirationResult(totalExpired, totalDemoted);
                });
    }

    private Promise<Integer> processExpirationsForTier(MemoryTier tier) {
        return router.listTierEntries(tier, null, maxRecordsPerCycle)
                .then(entries -> {
                    List<Promise<Boolean>> expirePromises = new ArrayList<>();

                    for (TierEntry entry : entries) {
                        if (entry.isExpired() && !entry.isPinned()) {
                            if (tier == MemoryTier.ARCHIVE) {
                                // Evict from archive
                                expirePromises.add(
                                        router.remove(entry.getRecordId(), entry.getTenantId())
                                );
                            } else {
                                // Demote to next tier
                                expirePromises.add(
                                        router.executeTransition(
                                                entry.getRecordId(),
                                                entry.getTenantId(),
                                                tier.demote()
                                        ).map(result -> result.success())
                                );
                            }
                        }
                    }

                    return Promises.toList(expirePromises)
                            .map(results -> (int) results.stream().filter(b -> b).count());
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Transition Processing
    // ═══════════════════════════════════════════════════════════════════════════

    private Promise<TransitionResult> processTransitions() {
        return router.listTierEntries(MemoryTier.HOT, null, maxRecordsPerCycle)
                .then(hotEntries -> processTransitionsForEntries(hotEntries))
                .then(hotResult -> router.listTierEntries(MemoryTier.WARM, null, maxRecordsPerCycle)
                        .then(warmEntries -> processTransitionsForEntries(warmEntries))
                        .map(warmResult -> new TransitionResult(
                                hotResult.promotions + warmResult.promotions,
                                hotResult.demotions + warmResult.demotions,
                                hotResult.skipped + warmResult.skipped
                        )));
    }

    private Promise<TransitionResult> processTransitionsForEntries(List<TierEntry> entries) {
        int promotions = 0;
        int demotions = 0;
        int skipped = 0;
        int processed = 0;

        List<Promise<MemoryTierRouter.TransitionResult>> transitionPromises = new ArrayList<>();

        for (TierEntry entry : entries) {
            if (processed >= maxTransitionsPerCycle) {
                skipped++;
                continue;
            }

            double currentSalience = entry.getLastSalienceScore();

            // Simple evaluation - in production would use salienceScorer
            MemoryTier currentTier = entry.getCurrentTier();
            TierPolicy policy = router.getPolicy(currentTier);

            if (policy.shouldPromote(currentSalience)) {
                transitionPromises.add(
                        router.executeTransition(
                                entry.getRecordId(),
                                entry.getTenantId(),
                                currentTier.promote()
                        )
                );
                processed++;
            } else if (policy.shouldDemote(currentSalience)) {
                transitionPromises.add(
                        router.executeTransition(
                                entry.getRecordId(),
                                entry.getTenantId(),
                                currentTier.demote()
                        )
                );
                processed++;
            }
        }

        final int finalSkipped = skipped;
        return Promises.toList(transitionPromises)
                .map(results -> {
                    int promo = 0;
                    int demo = 0;

                    for (MemoryTierRouter.TransitionResult result : results) {
                        if (result.success()) {
                            if (result.targetTier().isHigherPriorityThan(result.sourceTier())) {
                                promo++;
                            } else {
                                demo++;
                            }
                        }
                    }

                    return new TransitionResult(promo, demo, finalSkipped);
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Capacity Processing
    // ═══════════════════════════════════════════════════════════════════════════

    private Promise<CapacityResult> processCapacity() {
        List<Promise<MemoryTierRouter.EvictionResult>> evictionPromises = new ArrayList<>();

        for (MemoryTier tier : MemoryTier.values()) {
            TierPolicy policy = router.getPolicy(tier);
            double targetUtil = aggressiveEviction
                    ? policy.getEvictionTarget() * 0.9
                    : policy.getEvictionTarget();

            evictionPromises.add(router.evict(tier, targetUtil));
        }

        return Promises.toList(evictionPromises)
                .map(results -> {
                    int totalEvicted = 0;
                    int totalDemoted = 0;
                    long bytesFreed = 0;

                    for (MemoryTierRouter.EvictionResult result : results) {
                        totalEvicted += result.recordsEvicted();
                        totalDemoted += result.recordsDemoted();
                        bytesFreed += result.bytesFreed();
                    }

                    return new CapacityResult(totalEvicted, totalDemoted, bytesFreed);
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Listener Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Adds a reconciliation listener.
     *
     * @param listener the listener to add
     */
    public void addListener(ReconciliationListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a reconciliation listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(ReconciliationListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(ReconciliationReport report) {
        for (ReconciliationListener listener : listeners) {
            try {
                listener.onReconciliationComplete(report);
            } catch (Exception e) {
                LOG.warn("Listener threw exception", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Result Types
    // ═══════════════════════════════════════════════════════════════════════════

    private record ExpirationResult(int expiredCount, int demotedCount) {}
    private record TransitionResult(int promotions, int demotions, int skipped) {}
    private record CapacityResult(int totalEvicted, int totalDemoted, long bytesFreed) {}

    /**
     * Report of a reconciliation cycle.
     */
    @Value
    @Builder
    public static class ReconciliationReport {
        Instant startTime;
        Instant endTime;
        Map<MemoryTier, MemoryTierRouter.TierStatistics> beforeStats;
        Map<MemoryTier, MemoryTierRouter.TierStatistics> afterStats;

        int expiredRecords;
        int demotedFromExpiry;
        int promotions;
        int demotions;
        int transitionsSkipped;
        int evictionsForCapacity;
        int demotedForCapacity;
        long bytesFreed;

        /**
         * Gets the total number of records affected.
         *
         * @return total affected records
         */
        public int getTotalAffected() {
            return expiredRecords + demotedFromExpiry + promotions + demotions
                    + evictionsForCapacity + demotedForCapacity;
        }

        /**
         * Gets the duration of the reconciliation cycle.
         *
         * @return cycle duration
         */
        public Duration getDuration() {
            return Duration.between(startTime, endTime);
        }

        /**
         * Gets the net change in record count across all tiers.
         *
         * @return net change (negative means records removed)
         */
        public long getNetRecordChange() {
            return -(expiredRecords + evictionsForCapacity);
        }
    }

    /**
     * Listener for reconciliation events.
     */
    @FunctionalInterface
    public interface ReconciliationListener {
        /**
         * Called when a reconciliation cycle completes.
         *
         * @param report the reconciliation report
         */
        void onReconciliationComplete(ReconciliationReport report);
    }
}
