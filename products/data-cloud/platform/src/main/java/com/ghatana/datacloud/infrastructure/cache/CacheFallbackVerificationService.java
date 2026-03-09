package com.ghatana.datacloud.infrastructure.cache;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.datacloud.entity.MetaCollection;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for verifying cache fallback behavior to database.
 *
 * <p><b>Purpose</b><br>
 * Validates that cache misses correctly fall back to the database repository,
 * ensuring data consistency between cache and persistent storage. Detects
 * cache-database discrepancies and emits metrics for monitoring.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CacheFallbackVerificationService verifier = new CacheFallbackVerificationService(
 *     repository,
 *     cacheService,
 *     metrics
 * );
 *
 * // Verify cache miss fallback
 * Promise<VerificationResult> promise = verifier.verifyFallback("tenant-123", "users");
 * VerificationResult result = runPromise(() -> promise);
 *
 * if (!result.isConsistent()) {
 *     logger.warn("Cache inconsistency detected: {}", result.issue());
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Infrastructure layer service for cache validation
 * - Uses CollectionRepository for database access
 * - Uses MetadataCacheService for cache access
 * - Uses MetricsCollector for consistency metrics
 * - Detects and reports cache-database discrepancies
 *
 * <p><b>Verification Strategy</b><br>
 * 1. Invalidate cache entry
 * 2. Fetch from cache (should trigger database fallback)
 * 3. Fetch directly from database
 * 4. Compare results for consistency
 * 5. Emit metrics and log discrepancies
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - can be invoked concurrently.
 *
 * @see MetadataCacheService
 * @see CollectionRepository
 * @doc.type class
 * @doc.purpose Cache fallback verification service
 * @doc.layer product
 * @doc.pattern Service (Infrastructure Layer)
 */
public class CacheFallbackVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheFallbackVerificationService.class);

    private final CollectionRepository repository;
    private final MetadataCacheService cacheService;
    private final MetricsCollector metrics;

    /**
     * Creates a new cache fallback verification service.
     *
     * @param repository the collection repository (required)
     * @param cacheService the cache service (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if any parameter is null
     */
    public CacheFallbackVerificationService(
            CollectionRepository repository,
            MetadataCacheService cacheService,
            MetricsCollector metrics) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null");
        this.cacheService = Objects.requireNonNull(cacheService, "CacheService must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
    }

    /**
     * Verifies cache fallback behavior for a collection.
     *
     * <p><b>Verification Process</b><br>
     * 1. Invalidate cache to ensure miss
     * 2. Fetch via cache (triggers fallback)
     * 3. Fetch directly from database
     * 4. Compare results
     * 5. Record metrics
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @return Promise of VerificationResult
     * @throws NullPointerException if any parameter is null
     */
    public Promise<VerificationResult> verifyFallback(String tenantId, String collectionName) {
        Objects.requireNonNull(tenantId, "TenantId must not be null");
        Objects.requireNonNull(collectionName, "CollectionName must not be null");

        long startTime = System.currentTimeMillis();

        LOGGER.debug("Verifying cache fallback: tenant={}, collection={}", tenantId, collectionName);

        // Step 1: Invalidate cache to force fallback
        return cacheService.invalidateCollection(tenantId, collectionName)
            .then(() -> {
                // Step 2: Fetch via cache (should trigger database fallback)
                return cacheService.getCollection(tenantId, collectionName);
            })
            .then(cacheResult -> {
                // Step 3: Fetch directly from database
                return repository.findByName(tenantId, collectionName)
                    .map(dbResult -> {
                        // Step 4: Compare results
                        VerificationResult result = compareResults(
                            tenantId,
                            collectionName,
                            cacheResult,
                            dbResult
                        );

                        // Step 5: Record metrics
                        long duration = System.currentTimeMillis() - startTime;
                        recordMetrics(tenantId, collectionName, result, duration);

                        if (!result.isConsistent()) {
                            LOGGER.warn("Cache fallback inconsistency detected: tenant={}, collection={}, issue={}",
                                tenantId, collectionName, result.issue());
                        } else {
                            LOGGER.debug("Cache fallback verified: tenant={}, collection={}, {}ms",
                                tenantId, collectionName, duration);
                        }

                        return result;
                    });
            })
            .whenException(ex -> {
                metrics.incrementCounter("cache.fallback.verification.error",
                    "tenant", tenantId,
                    "collection", collectionName,
                    "error", ex.getClass().getSimpleName());
                LOGGER.error("Cache fallback verification failed: tenant={}, collection={}",
                    tenantId, collectionName, ex);
            });
    }

    /**
     * Compares cache and database results for consistency.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @param cacheResult the result from cache
     * @param dbResult the result from database
     * @return VerificationResult indicating consistency
     */
    private VerificationResult compareResults(
            String tenantId,
            String collectionName,
            Optional<MetaCollection> cacheResult,
            Optional<MetaCollection> dbResult) {

        // Both empty - consistent (collection doesn't exist)
        if (cacheResult.isEmpty() && dbResult.isEmpty()) {
            return VerificationResult.consistent(
                tenantId,
                collectionName,
                false
            );
        }

        // Cache has value, DB doesn't - inconsistent
        if (cacheResult.isPresent() && dbResult.isEmpty()) {
            return VerificationResult.inconsistent(
                tenantId,
                collectionName,
                "Cache has value but database doesn't"
            );
        }

        // DB has value, cache doesn't - this is EXPECTED after invalidation
        // Cache should have been populated by getCollection() call
        if (cacheResult.isEmpty() && dbResult.isPresent()) {
            return VerificationResult.inconsistent(
                tenantId,
                collectionName,
                "Database has value but cache fallback didn't populate cache"
            );
        }

        // Both present - verify they match
        MetaCollection cached = cacheResult.get();
        MetaCollection fromDb = dbResult.get();

        if (!cached.equals(fromDb)) {
            return VerificationResult.inconsistent(
                tenantId,
                collectionName,
                String.format("Cache and database values differ: cached=%s, db=%s",
                    cached, fromDb)
            );
        }

        return VerificationResult.consistent(
            tenantId,
            collectionName,
            true
        );
    }

    /**
     * Records verification metrics.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @param result the verification result
     * @param durationMs the verification duration in milliseconds
     */
    private void recordMetrics(
        String tenantId,
        String collectionName,
        VerificationResult result,
        long durationMs) {

        metrics.getMeterRegistry()
            .timer("cache.fallback.verification.duration",
                "tenant", tenantId,
                "collection", collectionName,
                "consistent", String.valueOf(result.isConsistent()))
            .record(Duration.ofMillis(durationMs));

        metrics.incrementCounter("cache.fallback.verification.count",
            "tenant", tenantId,
            "collection", collectionName,
            "consistent", String.valueOf(result.isConsistent()),
            "exists", String.valueOf(result.exists()));

        if (!result.isConsistent()) {
            metrics.incrementCounter("cache.fallback.verification.inconsistent",
                "tenant", tenantId,
                "collection", collectionName);
        }
    }

    /**
     * Result of a cache fallback verification.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @param isConsistent true if cache and database are consistent
     * @param exists true if collection exists in database
     * @param issue optional description of inconsistency (null if consistent)
     *
     * @doc.type record
     * @doc.purpose Cache fallback verification result
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record VerificationResult(
            String tenantId,
            String collectionName,
            boolean isConsistent,
            boolean exists,
            String issue) {

        /**
         * Creates a consistent verification result.
         *
         * @param tenantId the tenant identifier
         * @param collectionName the collection name
         * @param exists true if collection exists
         * @return consistent VerificationResult
         */
        public static VerificationResult consistent(
                String tenantId,
                String collectionName,
                boolean exists) {
            return new VerificationResult(tenantId, collectionName, true, exists, null);
        }

        /**
         * Creates an inconsistent verification result.
         *
         * @param tenantId the tenant identifier
         * @param collectionName the collection name
         * @param issue the description of the inconsistency
         * @return inconsistent VerificationResult
         */
        public static VerificationResult inconsistent(
                String tenantId,
                String collectionName,
                String issue) {
            return new VerificationResult(tenantId, collectionName, false, true, issue);
        }

        @Override
        public String toString() {
            if (isConsistent) {
                return String.format("VerificationResult{tenant=%s, collection=%s, consistent=true, exists=%s}",
                    tenantId, collectionName, exists);
            } else {
                return String.format("VerificationResult{tenant=%s, collection=%s, consistent=false, issue=%s}",
                    tenantId, collectionName, issue);
            }
        }
    }
}
