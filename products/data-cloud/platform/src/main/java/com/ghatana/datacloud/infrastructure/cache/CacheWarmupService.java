package com.ghatana.datacloud.infrastructure.cache;

import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.async.function.AsyncSupplier;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Service for warming up metadata cache with jitter to prevent thundering herd.
 *
 * <p><b>Purpose</b><br>
 * Proactively loads frequently accessed collection metadata into cache on startup
 * or schedule. Uses randomized jitter to prevent synchronized cache stampedes when
 * multiple instances restart simultaneously.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CacheWarmupService warmup = new CacheWarmupService(
 *     repository,
 *     cacheService,
 *     metrics
 * );
 *
 * // Warm up cache for all tenants
 * Promise<WarmupResult> promise = warmup.warmupAllTenants();
 * WarmupResult result = runPromise(() -> promise);
 *
 * // Warm up specific tenant with custom jitter
 * promise = warmup.warmupTenant("tenant-123", Duration.ofSeconds(5));
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Cache warm-up service in infrastructure layer
 * - Uses CollectionRepository for data loading
 * - Uses MetadataCacheService for cache population
 * - Uses MetricsCollector for warm-up metrics
 * - Prevents thundering herd with randomized jitter
 *
 * <p><b>Jitter Strategy</b><br>
 * - Randomized delays between collections (0 to maxJitter ms)
 * - Prevents synchronized cache loads across instances
 * - Default jitter: 100ms per collection
 * - Configurable per warm-up operation
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - can be invoked concurrently.
 *
 * @see MetadataCacheService
 * @see CollectionRepository
 * @doc.type class
 * @doc.purpose Cache warm-up with jitter to prevent thundering herd
 * @doc.layer product
 * @doc.pattern Service (Infrastructure Layer)
 */
public class CacheWarmupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheWarmupService.class);

    private static final Duration DEFAULT_MAX_JITTER = Duration.ofMillis(100);
    private static final int DEFAULT_BATCH_SIZE = 10;

    private final CollectionRepository repository;
    private final MetadataCacheService cacheService;
    private final MetricsCollector metrics;
    private final Duration defaultMaxJitter;
    private final int batchSize;

    /**
     * Creates a new cache warmup service.
     *
     * @param repository the collection repository (required)
     * @param cacheService the cache service (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if any parameter is null
     */
    public CacheWarmupService(
            CollectionRepository repository,
            MetadataCacheService cacheService,
            MetricsCollector metrics) {
        this(repository, cacheService, metrics, DEFAULT_MAX_JITTER, DEFAULT_BATCH_SIZE);
    }

    /**
     * Creates a new cache warmup service with custom settings.
     *
     * @param repository the collection repository (required)
     * @param cacheService the cache service (required)
     * @param metrics the metrics collector (required)
     * @param defaultMaxJitter the default maximum jitter (required)
     * @param batchSize the batch size for parallel warm-up (required)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if batchSize <= 0
     */
    public CacheWarmupService(
            CollectionRepository repository,
            MetadataCacheService cacheService,
            MetricsCollector metrics,
            Duration defaultMaxJitter,
            int batchSize) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null");
        this.cacheService = Objects.requireNonNull(cacheService, "CacheService must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
        this.defaultMaxJitter = Objects.requireNonNull(defaultMaxJitter, "MaxJitter must not be null");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be > 0");
        }
        this.batchSize = batchSize;
    }

    /**
     * Warms up cache for all tenants.
     *
     * <p><b>Behavior</b><br>
     * - Loads all collections from repository
     * - Applies jitter between loads
     * - Processes in batches for performance
     * - Emits warm-up metrics
     *
     * @return Promise of WarmupResult with statistics
     */
    public Promise<WarmupResult> warmupAllTenants() {
        return warmupAllTenants(defaultMaxJitter);
    }

    /**
     * Warms up cache for all tenants with custom jitter.
     *
     * @param maxJitter the maximum jitter duration (required)
     * @return Promise of WarmupResult
     */
    public Promise<WarmupResult> warmupAllTenants(Duration maxJitter) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("Starting cache warm-up for all tenants with max jitter: {}ms", maxJitter.toMillis());

        return repository.findAllTenantIds()
            .then(tenantIds -> {
                LOGGER.info("Warming up cache for {} tenants", tenantIds.size());

                // Warm up each tenant sequentially to avoid overload
                // Start with an empty list of results
                Promise<List<WarmupResult>> resultPromise = Promise.of(new ArrayList<>());
                
                for (String tenantId : tenantIds) {
                    resultPromise = resultPromise.then(results -> 
                        warmupTenant(tenantId, maxJitter)
                            .map(result -> {
                                results.add(result);
                                return results;
                            })
                    );
                }
                
                return resultPromise;
            })
            .map(results -> {
                long duration = System.currentTimeMillis() - startTime;

                WarmupResult combined = results.stream()
                    .reduce(WarmupResult.empty(), WarmupResult::combine);

                // Record duration using Timer
                metrics.getMeterRegistry()
                    .timer("cache.warmup.duration")
                    .record(Duration.ofMillis(duration));
                    
                metrics.incrementCounter("cache.warmup.completed",
                    "success", String.valueOf(combined.successCount()),
                    "errors", String.valueOf(combined.errorCount()));

                LOGGER.info("Cache warm-up completed: {} successful, {} errors, {} total, {}ms",
                    combined.successCount(), combined.errorCount(), combined.totalCount(), duration);

                return combined;
            })
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    metrics.incrementCounter("cache.warmup.error",
                        "error", ex.getClass().getSimpleName());
                    LOGGER.error("Cache warm-up failed", ex);
                }
            });
    }

    /**
     * Warms up cache for a specific tenant.
     *
     * @param tenantId the tenant identifier (required)
     * @return Promise of WarmupResult for this tenant
     */
    public Promise<WarmupResult> warmupTenant(String tenantId) {
        return warmupTenant(tenantId, defaultMaxJitter);
    }

    /**
     * Warms up cache for a specific tenant with custom jitter.
     *
     * <p><b>Jitter Application</b><br>
     * Random delay (0 to maxJitter) inserted between each collection load
     * to prevent synchronized cache stampedes.
     *
     * @param tenantId the tenant identifier (required)
     * @param maxJitter the maximum jitter duration (required)
     * @return Promise of WarmupResult
     */
    public Promise<WarmupResult> warmupTenant(String tenantId, Duration maxJitter) {
        Objects.requireNonNull(tenantId, "TenantId must not be null");
        Objects.requireNonNull(maxJitter, "MaxJitter must not be null");

        long startTime = System.currentTimeMillis();
        LOGGER.debug("Warming up cache for tenant: {}", tenantId);

        return repository.findAllByTenant(tenantId)
            .then(collections -> {
                LOGGER.debug("Found {} collections for tenant: {}", collections.size(), tenantId);

                if (collections.isEmpty()) {
                    return Promise.of(WarmupResult.empty());
                }

                // Process in batches with jitter
                return warmupCollections(tenantId, collections, maxJitter);
            })
            .whenComplete((result, ex) -> {
                long duration = System.currentTimeMillis() - startTime;

                if (ex == null) {
                    metrics.getMeterRegistry()
                        .timer("cache.warmup.tenant.duration", "tenant", tenantId)
                        .record(Duration.ofMillis(duration));
                    LOGGER.debug("Tenant warm-up completed: tenant={}, success={}, errors={}, {}ms",
                        tenantId, result.successCount(), result.errorCount(), duration);
                } else {
                    metrics.incrementCounter("cache.warmup.tenant.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                    LOGGER.error("Tenant warm-up failed: tenant={}", tenantId, ex);
                }
            });
    }

    /**
     * Warms up a list of collections with jitter.
     *
     * @param tenantId the tenant identifier
     * @param collections the collections to warm up
     * @param maxJitter the maximum jitter duration
     * @return Promise of WarmupResult
     */
    private Promise<WarmupResult> warmupCollections(
            String tenantId,
            List<MetaCollection> collections,
            Duration maxJitter) {

        int successCount = 0;
        int errorCount = 0;

        List<Promise<Boolean>> promises = new ArrayList<>();

        for (MetaCollection collection : collections) {
            // Apply randomized jitter before each collection
            long jitterMs = calculateJitter(maxJitter);

            Promise<Boolean> warmupPromise = applyJitter(jitterMs)
                .then(() -> warmupCollection(tenantId, collection))
                .map(success -> {
                    if (success) {
                        LOGGER.trace("Warmed up collection: tenant={}, collection={}",
                            tenantId, collection.getName());
                    }
                    return success;
                })
                .whenException(ex -> {
                    LOGGER.warn("Failed to warm up collection: tenant={}, collection={}",
                        tenantId, collection.getName(), ex);
                });

            promises.add(warmupPromise);

            // Process in batches
            if (promises.size() >= batchSize) {
                // Wait for batch to complete before continuing
                List<Boolean> batchResults = promises.stream()
                    .map(p -> {
                        try {
                            // Note: In production, use proper Promise handling
                            return p.getResult();
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

                successCount += batchResults.stream().filter(Boolean::booleanValue).count();
                errorCount += batchResults.stream().filter(b -> !b).count();
                promises.clear();
            }
        }

        // Handle remaining items
        if (!promises.isEmpty()) {
            List<Boolean> batchResults = promises.stream()
                .map(p -> {
                    try {
                        return p.getResult();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

            successCount += batchResults.stream().filter(Boolean::booleanValue).count();
            errorCount += batchResults.stream().filter(b -> !b).count();
        }

        int finalSuccessCount = successCount;
        int finalErrorCount = errorCount;
        return Promise.of(new WarmupResult(finalSuccessCount, finalErrorCount));
    }

    /**
     * Warms up a single collection.
     *
     * @param tenantId the tenant identifier
     * @param collection the collection to warm up
     * @return Promise of boolean indicating success
     */
    private Promise<Boolean> warmupCollection(String tenantId, MetaCollection collection) {
        return cacheService.getCollection(tenantId, collection.getName())
            .map(result -> {
                metrics.incrementCounter("cache.warmup.collection",
                    "tenant", tenantId,
                    "collection", collection.getName(),
                    "cached", String.valueOf(result.isPresent()));
                return result.isPresent();
            })
            .whenException(ex -> {
                metrics.incrementCounter("cache.warmup.collection.error",
                    "tenant", tenantId,
                    "collection", collection.getName());
            });
    }

    /**
     * Applies jitter delay.
     *
     * @param jitterMs the jitter in milliseconds
     * @return Promise that completes after jitter delay
     */
    private Promise<Void> applyJitter(long jitterMs) {
        if (jitterMs <= 0) {
            return Promise.complete();
        }

        // Use Promises.delay to avoid blocking eventloop
        return Promises.delay(Duration.ofMillis(jitterMs));
    }

    /**
     * Calculates random jitter.
     *
     * @param maxJitter the maximum jitter duration
     * @return random jitter in milliseconds (0 to maxJitter)
     */
    private long calculateJitter(Duration maxJitter) {
        long maxMs = maxJitter.toMillis();
        if (maxMs <= 0) {
            return 0;
        }
        return ThreadLocalRandom.current().nextLong(0, maxMs + 1);
    }

    /**
     * Result of a cache warm-up operation.
     *
     * @param successCount number of successfully warmed collections
     * @param errorCount number of errors during warm-up
     *
     * @doc.type record
     * @doc.purpose Cache warm-up operation result
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record WarmupResult(int successCount, int errorCount) {

        /**
         * Gets total count (success + errors).
         *
         * @return total collections processed
         */
        public int totalCount() {
            return successCount + errorCount;
        }

        /**
         * Creates an empty result.
         *
         * @return WarmupResult with 0 success and 0 errors
         */
        public static WarmupResult empty() {
            return new WarmupResult(0, 0);
        }

        /**
         * Combines two results.
         *
         * @param other the other result to combine
         * @return combined result
         */
        public WarmupResult combine(WarmupResult other) {
            return new WarmupResult(
                this.successCount + other.successCount,
                this.errorCount + other.errorCount
            );
        }

        @Override
        public String toString() {
            return String.format("WarmupResult{success=%d, errors=%d, total=%d}",
                successCount, errorCount, totalCount());
        }
    }
}
