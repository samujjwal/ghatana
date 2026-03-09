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
import com.ghatana.datacloud.attention.SalienceScore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Default implementation of the Memory Tier Router.
 *
 * <p>
 * Provides intelligent tier-based memory management with automatic
 * promotion, demotion, and eviction. Uses in-memory storage for demonstration
 * but is designed to delegate to tier-specific storage backends.
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li>Salience-based tier placement</li>
 * <li>Configurable policies per tier</li>
 * <li>Multiple eviction strategies</li>
 * <li>Comprehensive metrics tracking</li>
 * <li>Thread-safe concurrent access</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * MemoryTierRouter<EventRecord> router = new DefaultMemoryTierRouter<>();
 * 
 * // Route a record
 * SalienceScore score = SalienceScore.builder()
 *         .overallScore(0.85)
 *         .priorityLevel(SalienceScore.PriorityLevel.HIGH)
 *         .build();
 * 
 * router.route(eventRecord, score)
 *         .whenResult(result -> {
 *             System.out.println("Placed in: " + result.targetTier());
 *         });
 * }</pre>
 *
 * @param <R> the type of data record being routed
 *
 * @doc.type class
 * @doc.purpose Default tier routing implementation
 * @doc.layer core
 * @doc.pattern Strategy, Template Method
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public class DefaultMemoryTierRouter<R extends DataRecord> implements MemoryTierRouter<R> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMemoryTierRouter.class);

    // Storage: tier -> recordId -> record
    private final Map<MemoryTier, ConcurrentHashMap<String, R>> tierStorage;

    // Metadata: recordId -> TierEntry
    private final ConcurrentHashMap<String, TierEntry> entryMetadata;

    // Policies per tier
    private final EnumMap<MemoryTier, TierPolicy> policies;

    // Statistics per tier
    private final EnumMap<MemoryTier, TierStats> statistics;

    /**
     * Mutable statistics holder for thread-safe updates.
     */
    private static class TierStats {
        final AtomicLong recordCount = new AtomicLong();
        final AtomicLong totalBytes = new AtomicLong();
        final AtomicLong hitCount = new AtomicLong();
        final AtomicLong missCount = new AtomicLong();
        final AtomicLong promotionCount = new AtomicLong();
        final AtomicLong demotionCount = new AtomicLong();
        final AtomicLong evictionCount = new AtomicLong();
        volatile Instant lastEvictionTime;
        final AtomicLong totalResidencyMs = new AtomicLong();
        final AtomicLong residencySamples = new AtomicLong();
    }

    /**
     * Creates a new router with default policies.
     */
    public DefaultMemoryTierRouter() {
        this.tierStorage = new EnumMap<>(MemoryTier.class);
        this.entryMetadata = new ConcurrentHashMap<>();
        this.policies = new EnumMap<>(MemoryTier.class);
        this.statistics = new EnumMap<>(MemoryTier.class);

        // Initialize storage and policies for each tier
        for (MemoryTier tier : MemoryTier.values()) {
            tierStorage.put(tier, new ConcurrentHashMap<>());
            policies.put(tier, TierPolicy.defaultFor(tier));
            statistics.put(tier, new TierStats());
        }
    }

    /**
     * Creates a router with custom policies.
     *
     * @param customPolicies map of tier to policy
     */
    public DefaultMemoryTierRouter(Map<MemoryTier, TierPolicy> customPolicies) {
        this();
        customPolicies.forEach(this::updatePolicy);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Core Routing
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<RoutingResult<R>> route(R record, SalienceScore salienceScore) {
        Objects.requireNonNull(record, "Record must not be null");
        Objects.requireNonNull(salienceScore, "Salience score must not be null");

        return Promise.of(record)
                .map(r -> routeInternal(r, salienceScore));
    }

    private RoutingResult<R> routeInternal(R record, SalienceScore salienceScore) {
        String recordId = record.getId().toString();
        String tenantId = extractTenantId(record);
        double score = salienceScore.getScore();

        // Determine target tier
        MemoryTier targetTier = MemoryTier.fromSalienceScore(score);
        TierPolicy policy = policies.get(targetTier);

        LOG.debug("Routing record {} with salience {} to tier {}",
                recordId, score, targetTier);

        // Check if record already exists
        TierEntry existingEntry = entryMetadata.get(recordId);
        if (existingEntry != null) {
            return handleExistingRecord(record, salienceScore, existingEntry, targetTier);
        }

        // Check capacity and evict if needed
        boolean evictionTriggered = false;
        if (checkCapacityExceeded(targetTier)) {
            evictIfNeeded(targetTier, policy.getEvictionTarget());
            evictionTriggered = true;

            // Recheck capacity
            if (checkCapacityExceeded(targetTier)) {
                LOG.warn("Still at capacity after eviction, rejecting record {}", recordId);
                return new RoutingResult<>(
                        record, targetTier, null, true,
                        RoutingResult.RoutingOutcome.REJECTED_CAPACITY,
                        "Tier at capacity even after eviction");
            }
        }

        // Create entry and store
        TierEntry entry = TierEntry.create(
                recordId,
                record.getClass().getSimpleName(),
                tenantId,
                targetTier,
                score,
                estimateSize(record),
                -1 // Use tier default TTL
        );

        tierStorage.get(targetTier).put(recordId, record);
        entryMetadata.put(recordId, entry);

        // Update statistics
        TierStats stats = statistics.get(targetTier);
        stats.recordCount.incrementAndGet();
        stats.totalBytes.addAndGet(entry.getEstimatedSizeBytes());

        LOG.debug("Successfully routed record {} to {}", recordId, targetTier);

        return new RoutingResult<>(
                record, targetTier, entry, evictionTriggered,
                evictionTriggered
                        ? RoutingResult.RoutingOutcome.PLACED_AFTER_EVICTION
                        : RoutingResult.RoutingOutcome.PLACED,
                "Record placed in " + targetTier);
    }

    private RoutingResult<R> handleExistingRecord(
            R record,
            SalienceScore salienceScore,
            TierEntry existingEntry,
            MemoryTier newTargetTier) {

        String recordId = record.getId().toString();
        MemoryTier currentTier = existingEntry.getCurrentTier();

        // Update salience
        TierEntry updatedEntry = existingEntry
                .recordAccess()
                .updateSalience(salienceScore.getScore());

        // Check if tier transition needed
        if (newTargetTier != currentTier) {
            TierPolicy currentPolicy = policies.get(currentTier);

            // Check minimum residency
            Duration residency = existingEntry.getTimeInCurrentTier();
            if (residency.compareTo(currentPolicy.getMinimumTierResidency()) < 0) {
                // Too soon to transition, just update in place
                tierStorage.get(currentTier).put(recordId, record);
                entryMetadata.put(recordId, updatedEntry);

                return new RoutingResult<>(
                        record, currentTier, updatedEntry, false,
                        RoutingResult.RoutingOutcome.UPDATED,
                        "Updated in current tier (residency requirement)");
            }

            // Execute transition
            tierStorage.get(currentTier).remove(recordId);
            tierStorage.get(newTargetTier).put(recordId, record);

            TierEntry movedEntry = updatedEntry.moveTo(newTargetTier, -1);
            entryMetadata.put(recordId, movedEntry);

            // Update stats
            updateTransitionStats(currentTier, newTargetTier, existingEntry);

            return new RoutingResult<>(
                    record, newTargetTier, movedEntry, false,
                    RoutingResult.RoutingOutcome.UPDATED,
                    "Moved from " + currentTier + " to " + newTargetTier);
        }

        // Same tier, just update
        tierStorage.get(currentTier).put(recordId, record);
        entryMetadata.put(recordId, updatedEntry);

        return new RoutingResult<>(
                record, currentTier, updatedEntry, false,
                RoutingResult.RoutingOutcome.UPDATED,
                "Updated in " + currentTier);
    }

    @Override
    public Promise<BatchRoutingResult<R>> routeBatch(Map<R, SalienceScore> records) {
        Objects.requireNonNull(records, "Records map must not be null");

        return Promise.of(records).map(recs -> {
            long startTime = System.currentTimeMillis();
            List<RoutingResult<R>> results = new ArrayList<>();
            Map<MemoryTier, Integer> tierDist = new EnumMap<>(MemoryTier.class);
            int successCount = 0;
            int failureCount = 0;

            for (Map.Entry<R, SalienceScore> entry : recs.entrySet()) {
                RoutingResult<R> result = routeInternal(entry.getKey(), entry.getValue());
                results.add(result);

                if (result.isSuccess()) {
                    successCount++;
                    tierDist.merge(result.targetTier(), 1, Integer::sum);
                } else {
                    failureCount++;
                }
            }

            return new BatchRoutingResult<>(
                    results,
                    successCount,
                    failureCount,
                    tierDist,
                    System.currentTimeMillis() - startTime);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Retrieval
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<Optional<R>> get(String recordId, String tenantId) {
        return Promise.of(getInternal(recordId, tenantId));
    }

    private Optional<R> getInternal(String recordId, String tenantId) {
        TierEntry entry = entryMetadata.get(recordId);
        if (entry == null) {
            // Miss across all tiers
            for (TierStats stats : statistics.values()) {
                stats.missCount.incrementAndGet();
            }
            return Optional.empty();
        }

        // Verify tenant
        if (tenantId != null && !tenantId.equals(entry.getTenantId())) {
            return Optional.empty();
        }

        MemoryTier tier = entry.getCurrentTier();
        R record = tierStorage.get(tier).get(recordId);

        if (record != null) {
            // Record access
            entryMetadata.put(recordId, entry.recordAccess());
            statistics.get(tier).hitCount.incrementAndGet();
        } else {
            statistics.get(tier).missCount.incrementAndGet();
        }

        return Optional.ofNullable(record);
    }

    @Override
    public Promise<Optional<R>> getFromTier(String recordId, String tenantId, MemoryTier tier) {
        return Promise.of(Optional.ofNullable(tierStorage.get(tier).get(recordId)));
    }

    @Override
    public Promise<Optional<TierEntry>> recordAccess(String recordId, String tenantId) {
        return Promise.of(
                Optional.ofNullable(entryMetadata.computeIfPresent(recordId,
                        (id, entry) -> entry.recordAccess())));
    }

    @Override
    public Promise<Optional<TierEntry>> getTierEntry(String recordId, String tenantId) {
        return Promise.of(Optional.ofNullable(entryMetadata.get(recordId)));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tier Transitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<TransitionRecommendation> evaluateTransition(
            String recordId,
            String tenantId,
            double currentSalience) {

        return Promise.of(evaluateTransitionInternal(recordId, tenantId, currentSalience));
    }

    private TransitionRecommendation evaluateTransitionInternal(
            String recordId,
            String tenantId,
            double currentSalience) {

        TierEntry entry = entryMetadata.get(recordId);
        if (entry == null) {
            return new TransitionRecommendation(
                    recordId, null, null,
                    TransitionRecommendation.TransitionType.NONE,
                    0.0, "Record not found");
        }

        MemoryTier currentTier = entry.getCurrentTier();
        TierPolicy policy = policies.get(currentTier);

        // Check minimum residency
        if (entry.getTimeInCurrentTier().compareTo(policy.getMinimumTierResidency()) < 0) {
            return new TransitionRecommendation(
                    recordId, currentTier, currentTier,
                    TransitionRecommendation.TransitionType.NONE,
                    0.9, "Minimum residency not met");
        }

        // Check for promotion
        if (policy.shouldPromote(currentSalience)) {
            MemoryTier promoteTo = currentTier.promote();
            double confidence = Math.min(1.0, (currentSalience - policy.getEffectivePromotionThreshold()) * 5);

            return new TransitionRecommendation(
                    recordId, currentTier, promoteTo,
                    TransitionRecommendation.TransitionType.PROMOTE,
                    confidence,
                    String.format("Salience %.3f exceeds promotion threshold %.3f",
                            currentSalience, policy.getEffectivePromotionThreshold()));
        }

        // Check for demotion
        if (policy.shouldDemote(currentSalience)) {
            MemoryTier demoteTo = currentTier.demote();
            double confidence = Math.min(1.0, (policy.getEffectiveDemotionThreshold() - currentSalience) * 5);

            return new TransitionRecommendation(
                    recordId, currentTier, demoteTo,
                    TransitionRecommendation.TransitionType.DEMOTE,
                    confidence,
                    String.format("Salience %.3f below demotion threshold %.3f",
                            currentSalience, policy.getEffectiveDemotionThreshold()));
        }

        // Check for expiry-based eviction
        if (entry.isExpired() && currentTier == MemoryTier.ARCHIVE) {
            return new TransitionRecommendation(
                    recordId, currentTier, null,
                    TransitionRecommendation.TransitionType.EVICT,
                    1.0, "Record expired in archive tier");
        }

        return new TransitionRecommendation(
                recordId, currentTier, currentTier,
                TransitionRecommendation.TransitionType.NONE,
                0.8, "No transition warranted");
    }

    @Override
    public Promise<TransitionResult> executeTransition(
            String recordId,
            String tenantId,
            MemoryTier targetTier) {

        return Promise.of(recordId)
                .map(id -> executeTransitionInternal(id, tenantId, targetTier));
    }

    private TransitionResult executeTransitionInternal(
            String recordId,
            String tenantId,
            MemoryTier targetTier) {

        TierEntry entry = entryMetadata.get(recordId);
        if (entry == null) {
            return new TransitionResult(
                    recordId, null, targetTier, false, null,
                    "Record not found");
        }

        MemoryTier sourceTier = entry.getCurrentTier();
        if (sourceTier == targetTier) {
            return new TransitionResult(
                    recordId, sourceTier, targetTier, true, entry,
                    "Already in target tier");
        }

        // Move record
        R record = tierStorage.get(sourceTier).remove(recordId);
        if (record == null) {
            return new TransitionResult(
                    recordId, sourceTier, targetTier, false, entry,
                    "Record data not found in source tier");
        }

        tierStorage.get(targetTier).put(recordId, record);

        // Update entry
        TierEntry movedEntry = entry.moveTo(targetTier, -1);
        entryMetadata.put(recordId, movedEntry);

        // Update statistics
        updateTransitionStats(sourceTier, targetTier, entry);

        LOG.info("Transitioned record {} from {} to {}",
                recordId, sourceTier, targetTier);

        return new TransitionResult(
                recordId, sourceTier, targetTier, true, movedEntry,
                "Successfully transitioned");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Eviction
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<EvictionResult> evict(MemoryTier tier, double targetUtilization) {
        return Promise.of(tier)
                .map(t -> evictIfNeeded(t, targetUtilization));
    }

    private EvictionResult evictIfNeeded(MemoryTier tier, double targetUtilization) {
        TierPolicy policy = policies.get(tier);
        TierStats stats = statistics.get(tier);
        ConcurrentHashMap<String, R> storage = tierStorage.get(tier);

        double currentUtil = calculateUtilization(tier);
        if (currentUtil <= policy.getEvictionThreshold()) {
            return new EvictionResult(tier, 0, 0, 0, currentUtil, currentUtil, 0);
        }

        long startTime = System.currentTimeMillis();
        int evicted = 0;
        int demoted = 0;
        long bytesFreed = 0;

        // Get entries sorted by eviction priority
        List<TierEntry> candidates = entryMetadata.values().stream()
                .filter(e -> e.getCurrentTier() == tier)
                .filter(e -> !e.isPinned())
                .sorted(Comparator.comparingDouble(TierEntry::calculateEvictionPriority))
                .collect(Collectors.toList());

        for (TierEntry entry : candidates) {
            if (calculateUtilization(tier) <= targetUtilization) {
                break;
            }

            String recordId = entry.getRecordId();
            R record = storage.remove(recordId);
            if (record == null)
                continue;

            bytesFreed += entry.getEstimatedSizeBytes();

            // Try to demote instead of evict (except for ARCHIVE)
            if (tier != MemoryTier.ARCHIVE) {
                MemoryTier lowerTier = tier.demote();
                tierStorage.get(lowerTier).put(recordId, record);
                entryMetadata.put(recordId, entry.moveTo(lowerTier, -1));
                demoted++;
                statistics.get(lowerTier).recordCount.incrementAndGet();
                statistics.get(lowerTier).totalBytes.addAndGet(entry.getEstimatedSizeBytes());
            } else {
                entryMetadata.remove(recordId);
                evicted++;
            }

            stats.recordCount.decrementAndGet();
            stats.totalBytes.addAndGet(-entry.getEstimatedSizeBytes());
        }

        stats.evictionCount.addAndGet(evicted + demoted);
        stats.demotionCount.addAndGet(demoted);
        stats.lastEvictionTime = Instant.now();

        double newUtil = calculateUtilization(tier);

        LOG.info("Eviction in {}: evicted={}, demoted={}, bytesFreed={}, util: {:.2f} -> {:.2f}",
                tier, evicted, demoted, bytesFreed, currentUtil, newUtil);

        return new EvictionResult(
                tier, evicted, demoted, bytesFreed,
                currentUtil, newUtil,
                System.currentTimeMillis() - startTime);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Listing & Statistics
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<List<TierEntry>> listTierEntries(MemoryTier tier, String tenantId, int limit) {
        return Promise.of(
                entryMetadata.values().stream()
                        .filter(e -> e.getCurrentTier() == tier)
                        .filter(e -> tenantId == null || tenantId.equals(e.getTenantId()))
                        .limit(limit)
                        .collect(Collectors.toList()));
    }

    @Override
    public Promise<TierStatistics> getTierStatistics(MemoryTier tier) {
        return Promise.of(buildStatistics(tier));
    }

    @Override
    public Promise<Map<MemoryTier, TierStatistics>> getAllTierStatistics() {
        Map<MemoryTier, TierStatistics> result = new EnumMap<>(MemoryTier.class);
        for (MemoryTier tier : MemoryTier.values()) {
            result.put(tier, buildStatistics(tier));
        }
        return Promise.of(result);
    }

    private TierStatistics buildStatistics(MemoryTier tier) {
        TierStats stats = statistics.get(tier);
        TierPolicy policy = policies.get(tier);

        long hits = stats.hitCount.get();
        long misses = stats.missCount.get();
        double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) : 0.0;

        long residencySamples = stats.residencySamples.get();
        Duration avgResidency = residencySamples > 0
                ? Duration.ofMillis(stats.totalResidencyMs.get() / residencySamples)
                : Duration.ZERO;

        return new TierStatistics(
                tier,
                stats.recordCount.get(),
                stats.totalBytes.get(),
                calculateUtilization(tier),
                hits,
                misses,
                hitRate,
                stats.promotionCount.get(),
                stats.demotionCount.get(),
                stats.evictionCount.get(),
                stats.lastEvictionTime,
                avgResidency);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Policy Management
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public TierPolicy getPolicy(MemoryTier tier) {
        return policies.get(tier);
    }

    @Override
    public void updatePolicy(MemoryTier tier, TierPolicy policy) {
        Objects.requireNonNull(tier, "Tier must not be null");
        Objects.requireNonNull(policy, "Policy must not be null");

        policy.validate().ifPresent(error -> {
            throw new IllegalArgumentException("Invalid policy: " + error);
        });

        policies.put(tier, policy);
        LOG.info("Updated policy for tier {}", tier);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Pinning
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<Boolean> pin(String recordId, String tenantId, String reason) {
        return Promise.of(
                entryMetadata.computeIfPresent(recordId,
                        (id, entry) -> entry.pin(reason)) != null);
    }

    @Override
    public Promise<Boolean> unpin(String recordId, String tenantId) {
        return Promise.of(
                entryMetadata.computeIfPresent(recordId,
                        (id, entry) -> entry.unpin()) != null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Removal
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<Boolean> remove(String recordId, String tenantId) {
        return Promise.of(recordId)
                .map(id -> {
                    TierEntry entry = entryMetadata.remove(id);
                    if (entry == null)
                        return false;

                    MemoryTier tier = entry.getCurrentTier();
                    R removed = tierStorage.get(tier).remove(id);

                    if (removed != null) {
                        TierStats stats = statistics.get(tier);
                        stats.recordCount.decrementAndGet();
                        stats.totalBytes.addAndGet(-entry.getEstimatedSizeBytes());
                    }

                    return removed != null;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private String extractTenantId(R record) {
        // Try to extract tenant from record metadata
        if (record.getMetadata() != null && record.getMetadata().containsKey("tenantId")) {
            Object tenant = record.getMetadata().get("tenantId");
            return tenant != null ? tenant.toString() : "default";
        }
        return "default";
    }

    private long estimateSize(R record) {
        // Rough estimation - could be made more accurate with serialization
        return 512; // Base size
    }

    private boolean checkCapacityExceeded(MemoryTier tier) {
        TierPolicy policy = policies.get(tier);
        return calculateUtilization(tier) >= policy.getEvictionThreshold();
    }

    private double calculateUtilization(MemoryTier tier) {
        TierPolicy policy = policies.get(tier);
        TierStats stats = statistics.get(tier);

        // Check both record count and bytes
        double recordUtil = policy.getMaxRecords() > 0
                ? (double) stats.recordCount.get() / policy.getMaxRecords()
                : 0.0;

        double bytesUtil = policy.getMaxBytes() > 0
                ? (double) stats.totalBytes.get() / policy.getMaxBytes()
                : 0.0;

        return Math.max(recordUtil, bytesUtil);
    }

    private void updateTransitionStats(MemoryTier sourceTier, MemoryTier targetTier, TierEntry entry) {
        TierStats sourceStats = statistics.get(sourceTier);
        TierStats targetStats = statistics.get(targetTier);

        sourceStats.recordCount.decrementAndGet();
        sourceStats.totalBytes.addAndGet(-entry.getEstimatedSizeBytes());
        targetStats.recordCount.incrementAndGet();
        targetStats.totalBytes.addAndGet(entry.getEstimatedSizeBytes());

        // Track residency for source tier
        long residencyMs = entry.getTimeInCurrentTier().toMillis();
        sourceStats.totalResidencyMs.addAndGet(residencyMs);
        sourceStats.residencySamples.incrementAndGet();

        if (targetTier.isHigherPriorityThan(sourceTier)) {
            sourceStats.promotionCount.incrementAndGet();
        } else {
            sourceStats.demotionCount.incrementAndGet();
        }
    }
}
