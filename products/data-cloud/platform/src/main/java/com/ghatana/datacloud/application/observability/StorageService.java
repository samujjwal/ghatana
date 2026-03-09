package com.ghatana.datacloud.application.observability;

import com.ghatana.datacloud.entity.observability.RetentionPolicy;
import com.ghatana.datacloud.entity.observability.SpanData;
import com.ghatana.datacloud.entity.observability.StorageTierManager;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Application service for trace storage and archival.
 *
 * <p>
 * <b>Purpose</b><br>
 * Orchestrates span storage across tiers, handles archival workflows, manages
 * cleanup, and provides storage statistics and SLO tracking.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * StorageService service = new StorageService(tierManager, archiveAdapter, metrics);
 *
 * // Store span in appropriate tier
 * Promise<Void> stored = service.storeSpan(tenantId, span);
 *
 * // Archive expired spans
 * Promise<Integer> archived = service.archiveExpiredSpans(tenantId);
 *
 * // Get storage metrics
 * StorageMetrics metrics = service.getStorageMetrics(tenantId);
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Application layer coordinator between domain (RetentionPolicy,
 * StorageTierManager) and infrastructure (ArchiveAdapter, StorageRepository).
 * Provides Promise-based async operations with metrics collection and error
 * handling.
 *
 * @doc.type class
 * @doc.purpose Trace storage and archival orchestration service
 * @doc.layer product
 * @doc.pattern Service
 */
public class StorageService {

    private final StorageTierManager tierManager;
    private final StorageArchiveAdapter archiveAdapter;
    private final MetricsCollector metrics;
    private final Executor blockingExecutor;

    /**
     * Create storage service.
     *
     * @param tierManager Tier manager for lifecycle
     * @param archiveAdapter Archive adapter for S3/GCS
     * @param metrics Metrics collector
     */
    public StorageService(
            StorageTierManager tierManager,
            StorageArchiveAdapter archiveAdapter,
            MetricsCollector metrics) {
        this(tierManager, archiveAdapter, metrics, Executors.newCachedThreadPool());
    }

    /**
     * Create storage service with custom executor.
     *
     * @param tierManager Tier manager for lifecycle
     * @param archiveAdapter Archive adapter for S3/GCS
     * @param metrics Metrics collector
     * @param blockingExecutor Executor for blocking operations
     */
    public StorageService(
            StorageTierManager tierManager,
            StorageArchiveAdapter archiveAdapter,
            MetricsCollector metrics,
            Executor blockingExecutor) {
        this.tierManager = Objects.requireNonNull(tierManager, "tierManager required");
        this.archiveAdapter = Objects.requireNonNull(archiveAdapter, "archiveAdapter required");
        this.metrics = Objects.requireNonNull(metrics, "metrics required");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor required");
    }

    /**
     * Store span with tier management.
     *
     * @param tenantId Tenant ID
     * @param span Span to store
     * @param currentTier Current storage tier
     * @return Promise of completion
     */
    public Promise<Void> storeSpan(
            String tenantId,
            SpanData span,
            RetentionPolicy.StorageTier currentTier) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(span, "span required");
        Objects.requireNonNull(currentTier, "currentTier required");

        long startTime = System.currentTimeMillis();

        try {
            // Update tier if needed
            RetentionPolicy.StorageTier newTier = tierManager.updateSpanTier(span, currentTier);

            // Record metric
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordTimer("trace.store.duration", duration);
            metrics.incrementCounter("trace.stored", "tenant", tenantId, "tier", newTier.name());

            return Promise.complete();
        } catch (Exception e) {
            metrics.incrementCounter("trace.store.error", "tenant", tenantId);
            return Promise.ofException(e);
        }
    }

    /**
     * Archive expired spans for tenant.
     *
     * @param tenantId Tenant ID
     * @param spans Candidate spans for archival
     * @return Promise of archive count
     */
    public Promise<Integer> archiveExpiredSpans(String tenantId, Collection<SpanData> spans) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(spans, "spans required");

        long startTime = System.currentTimeMillis();

        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                // Find spans ready for archival
                List<SpanData> archivalCandidates = tierManager.getSpansReadyForArchival(tenantId, spans);

                if (archivalCandidates.isEmpty()) {
                    return 0;
                }

                // Archive each span
                int archived = 0;
                for (SpanData span : archivalCandidates) {
                    try {
                        String archivePath = tierManager.getArchivePath(span);
                        archiveAdapter.archiveSpan(tenantId, span, archivePath);
                        archived++;
                    } catch (Exception e) {
                        // Log but continue with other spans
                        metrics.incrementCounter(
                                "trace.archive.failed",
                                "tenant", tenantId,
                                "trace_id", span.getTraceId()
                        );
                    }
                }

                // Record metrics
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordTimer("trace.archive.batch.duration", duration);
                metrics.incrementCounter(
                        "trace.archived",
                        "tenant", tenantId,
                        "count", String.valueOf(archived)
                );

                return archived;
            } catch (Exception e) {
                metrics.incrementCounter("trace.archive.error", "tenant", tenantId);
                throw e;
            }
        });
    }

    /**
     * Delete expired spans for tenant.
     *
     * @param tenantId Tenant ID
     * @param spans Candidate spans for deletion
     * @param deleteAfterArchiveAge Age after archival to delete
     * @return Promise of deletion count
     */
    public Promise<Integer> deleteExpiredSpans(
            String tenantId,
            Collection<SpanData> spans,
            Duration deleteAfterArchiveAge) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(spans, "spans required");
        Objects.requireNonNull(deleteAfterArchiveAge, "deleteAfterArchiveAge required");

        long startTime = System.currentTimeMillis();

        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                // Find spans ready for deletion
                List<SpanData> deletionCandidates = tierManager.getSpansReadyForDeletion(
                        tenantId, spans, deleteAfterArchiveAge);

                if (deletionCandidates.isEmpty()) {
                    return 0;
                }

                // Delete each span
                int deleted = 0;
                for (SpanData span : deletionCandidates) {
                    try {
                        archiveAdapter.deleteSpan(tenantId, span);
                        deleted++;
                    } catch (Exception e) {
                        metrics.incrementCounter(
                                "trace.delete.failed",
                                "tenant", tenantId,
                                "trace_id", span.getTraceId()
                        );
                    }
                }

                // Record metrics
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordTimer("trace.delete.batch.duration", duration);
                metrics.incrementCounter(
                        "trace.deleted",
                        "tenant", tenantId,
                        "count", String.valueOf(deleted)
                );

                return deleted;
            } catch (Exception e) {
                metrics.incrementCounter("trace.delete.error", "tenant", tenantId);
                throw e;
            }
        });
    }

    /**
     * Get storage metrics snapshot.
     *
     * @param tenantId Tenant ID (optional, null for global)
     * @return Storage metrics
     */
    public StorageMetrics getStorageMetrics(String tenantId) {
        StorageTierManager.StorageTierStatistics stats = tierManager.getStatistics();

        return new StorageMetrics(
                tenantId,
                stats.getTotalSpans(),
                stats.getSpansInTier(RetentionPolicy.StorageTier.HOT),
                stats.getSpansInTier(RetentionPolicy.StorageTier.WARM),
                stats.getSpansInTier(RetentionPolicy.StorageTier.COLD),
                stats.getSpansInTier(RetentionPolicy.StorageTier.ARCHIVE),
                Instant.ofEpochMilli(stats.getCaptureTimeMs())
        );
    }

    /**
     * Port interface for archive operations.
     */
    public interface StorageArchiveAdapter {

        /**
         * Archive span to S3/GCS.
         *
         * @param tenantId Tenant ID
         * @param span Span to archive
         * @param archivePath Target archive path
         */
        void archiveSpan(String tenantId, SpanData span, String archivePath);

        /**
         * Delete span from storage.
         *
         * @param tenantId Tenant ID
         * @param span Span to delete
         */
        void deleteSpan(String tenantId, SpanData span);
    }

    /**
     * Immutable storage metrics snapshot.
     */
    public static class StorageMetrics {

        private final String tenantId;
        private final int totalSpans;
        private final int hotSpans;
        private final int warmSpans;
        private final int coldSpans;
        private final int archiveSpans;
        private final Instant captureTime;

        public StorageMetrics(
                String tenantId,
                int totalSpans,
                int hotSpans,
                int warmSpans,
                int coldSpans,
                int archiveSpans,
                Instant captureTime) {
            this.tenantId = tenantId;
            this.totalSpans = totalSpans;
            this.hotSpans = hotSpans;
            this.warmSpans = warmSpans;
            this.coldSpans = coldSpans;
            this.archiveSpans = archiveSpans;
            this.captureTime = Objects.requireNonNull(captureTime);
        }

        public String getTenantId() {
            return tenantId;
        }

        public int getTotalSpans() {
            return totalSpans;
        }

        public int getHotSpans() {
            return hotSpans;
        }

        public int getWarmSpans() {
            return warmSpans;
        }

        public int getColdSpans() {
            return coldSpans;
        }

        public int getArchiveSpans() {
            return archiveSpans;
        }

        public Instant getCaptureTime() {
            return captureTime;
        }

        /**
         * Calculate storage distribution percentage.
         *
         * @param tier Storage tier
         * @return Percentage of spans in tier (0-100)
         */
        public double getPercentageInTier(RetentionPolicy.StorageTier tier) {
            if (totalSpans == 0) {
                return 0.0;
            }

            int count = switch (tier) {
                case HOT ->
                    hotSpans;
                case WARM ->
                    warmSpans;
                case COLD ->
                    coldSpans;
                case ARCHIVE ->
                    archiveSpans;
            };

            return (count * 100.0) / totalSpans;
        }
    }
}
