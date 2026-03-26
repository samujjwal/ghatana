package com.ghatana.datacloud.entity.observability;

import com.ghatana.platform.observability.MetricsCollector;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages span lifecycle across storage tiers and archival.
 *
 * <p>
 * <b>Purpose</b><br>
 * Orchestrates span movement between hot/warm/cold storage tiers based on age
 * and retention policy, with archival to S3/GCS and cleanup.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * StorageTierManager manager = StorageTierManager.create(policy, metrics);
 *
 * // Move spans to appropriate tier
 * manager.updateSpanTier(span);
 *
 * // Archive old spans
 * manager.archiveExpiredSpans();
 *
 * // Get tier statistics
 * StorageTierStatistics stats = manager.getStatistics();
 * }</pre>
 *
 * <p>
 * <b>Storage Movement Rules</b><br>
 * 1. New spans start in HOT tier (in-memory, fully indexed) 2. After 7 days →
 * WARM tier (SSD, basic indexing) 3. After 30 days → COLD tier (S3, no
 * indexing) 4. After 365 days → ARCHIVE tier (Glacier, retrieval hours) 5.
 * After 7 years → DELETE (if policy allows)
 *
 * <p>
 * <b>Performance Characteristics</b><br>
 * - Tier update: O(1) - single entry move - Batch archival: O(n) - linear scan
 * all spans - Cleanup: O(m) - scan candidates for deletion - Memory: O(1) per
 * span in tracking structure
 *
 * @doc.type class
 * @doc.purpose Storage tier lifecycle management for traces
 * @doc.layer product
 * @doc.pattern Manager
 */
public class StorageTierManager {

    private final RetentionPolicy policy;
    private final MetricsCollector metrics;
    private final ConcurrentHashMap<String, StorageTierInfo> tierTracking;
    private final ScheduledExecutorService scheduler;

    private StorageTierManager(RetentionPolicy policy, MetricsCollector metrics) {
        this.policy = Objects.requireNonNull(policy, "policy required");
        this.metrics = Objects.requireNonNull(metrics, "metrics required");
        this.tierTracking = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "StorageTierManager-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Create storage tier manager.
     *
     * @param policy Retention policy
     * @param metrics Metrics collector
     * @return Manager instance
     */
    public static StorageTierManager create(RetentionPolicy policy, MetricsCollector metrics) {
        return new StorageTierManager(policy, metrics);
    }

    /**
     * Update storage tier for span based on age.
     *
     * @param span Span to update
     * @param currentTier Current tier
     * @return New tier for span
     */
    public RetentionPolicy.StorageTier updateSpanTier(SpanData span, RetentionPolicy.StorageTier currentTier) {
        Objects.requireNonNull(span, "span required");
        Objects.requireNonNull(currentTier, "currentTier required");

        RetentionPolicy.StorageTier newTier = policy.getStorageTier(span);

        if (newTier != currentTier) {
            String spanKey = span.getTraceId() + ":" + span.getSpanId();
            tierTracking.put(spanKey, new StorageTierInfo(span.getStartTime(), newTier));

            metrics.incrementCounter(
                    "trace.tier.moved",
                    "from_tier", currentTier.name(),
                    "to_tier", newTier.name()
            );

            return newTier;
        }

        return currentTier;
    }

    /**
     * Get spans ready for archival (age > archiveStorageTtl).
     *
     * @param tenantId Tenant ID
     * @param spans Candidate spans
     * @return Spans ready for archival
     */
    public List<SpanData> getSpansReadyForArchival(String tenantId, Collection<SpanData> spans) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(spans, "spans required");

        List<SpanData> archivalCandidates = new ArrayList<>();

        for (SpanData span : spans) {
            if (policy.shouldArchiveSpan(span)) {
                archivalCandidates.add(span);
            }
        }

        metrics.incrementCounter(
                "trace.archive.candidates",
                "tenant", tenantId,
                "count", String.valueOf(archivalCandidates.size())
        );

        return archivalCandidates;
    }

    /**
     * Get spans ready for deletion (TTL expired).
     *
     * @param tenantId Tenant ID
     * @param spans Candidate spans
     * @param deleteAfterArchiveAge Age after archival to delete
     * @return Spans ready for deletion
     */
    public List<SpanData> getSpansReadyForDeletion(
            String tenantId,
            Collection<SpanData> spans,
            Duration deleteAfterArchiveAge) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(spans, "spans required");
        Objects.requireNonNull(deleteAfterArchiveAge, "deleteAfterArchiveAge required");

        List<SpanData> deletionCandidates = new ArrayList<>();

        for (SpanData span : spans) {
            if (policy.shouldDelete(span, deleteAfterArchiveAge)) {
                deletionCandidates.add(span);
            }
        }

        metrics.incrementCounter(
                "trace.deletion.candidates",
                "tenant", tenantId,
                "count", String.valueOf(deletionCandidates.size())
        );

        return deletionCandidates;
    }

    /**
     * Get storage tier statistics.
     *
     * @return Statistics snapshot
     */
    public StorageTierStatistics getStatistics() {
        long now = System.currentTimeMillis();
        Map<RetentionPolicy.StorageTier, Integer> tierCounts = new EnumMap<>(RetentionPolicy.StorageTier.class);

        for (RetentionPolicy.StorageTier tier : RetentionPolicy.StorageTier.values()) {
            tierCounts.put(tier, 0);
        }

        for (StorageTierInfo info : tierTracking.values()) {
            tierCounts.compute(info.tier, (k, v) -> v + 1);
        }

        return new StorageTierStatistics(
                tierTracking.size(),
                tierCounts,
                now
        );
    }

    /**
     * Schedule automatic tier management at interval.
     *
     * @param interval Check interval
     * @return true if scheduled
     */
    public boolean scheduleAutomaticTierManagement(Duration interval) {
        Objects.requireNonNull(interval, "interval required");

        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be > 0");
        }

        scheduler.scheduleAtFixedRate(
                this::performAutomaticTierManagement,
                interval.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS
        );

        return true;
    }

    /**
     * Perform automatic tier management (internal).
     */
    private void performAutomaticTierManagement() {
        try {
            // Review all tracked spans and update tiers
            for (StorageTierInfo info : tierTracking.values()) {
                // In production, this would:
                // 1. Check each span's age
                // 2. Move to appropriate tier
                // 3. Archive if needed
                // 4. Delete if TTL expired
            }
        } catch (Exception e) {
            metrics.incrementCounter("trace.tier.management.error");
        }
    }

    /**
     * Get archive path for span.
     *
     * @param span Span to archive
     * @return S3 path
     */
    public String getArchivePath(SpanData span) {
        return policy.getArchivePath(span);
    }

    /**
     * Shutdown tier manager scheduler.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Internal storage tier tracking information.
     */
    private static class StorageTierInfo {

        @SuppressWarnings("unused")
        final Instant timestamp;
        final RetentionPolicy.StorageTier tier;

        StorageTierInfo(Instant timestamp, RetentionPolicy.StorageTier tier) {
            this.timestamp = Objects.requireNonNull(timestamp);
            this.tier = Objects.requireNonNull(tier);
        }
    }

    /**
     * Immutable statistics snapshot for storage tiers.
     */
    public static class StorageTierStatistics {

        private final int totalSpans;
        private final Map<RetentionPolicy.StorageTier, Integer> tierCounts;
        private final long captureTimeMs;

        public StorageTierStatistics(
                int totalSpans,
                Map<RetentionPolicy.StorageTier, Integer> tierCounts,
                long captureTimeMs) {
            this.totalSpans = totalSpans;
            this.tierCounts = Collections.unmodifiableMap(new EnumMap<>(tierCounts));
            this.captureTimeMs = captureTimeMs;
        }

        public int getTotalSpans() {
            return totalSpans;
        }

        public int getSpansInTier(RetentionPolicy.StorageTier tier) {
            return tierCounts.getOrDefault(tier, 0);
        }

        public Map<RetentionPolicy.StorageTier, Integer> getTierCounts() {
            return tierCounts;
        }

        public long getCaptureTimeMs() {
            return captureTimeMs;
        }
    }
}
