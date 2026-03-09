package com.ghatana.datacloud.application.storage;

import com.ghatana.platform.core.exception.BaseException;
import com.ghatana.platform.core.exception.ErrorCode;
import com.ghatana.platform.core.exception.ResourceNotFoundException;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import com.ghatana.datacloud.entity.storage.CollectionStorageProfileRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for routing queries to appropriate storage backends based on
 * collection profiles.
 *
 * <p>
 * <b>Purpose</b><br>
 * Makes intelligent routing decisions for queries based on: -
 * Collection-specific storage profile configuration - Backend availability and
 * failover settings - Tenant-scoped isolation - Performance optimization
 * (caching, affinity)
 *
 * <p>
 * <b>Routing Decision Flow</b><br>
 * 1. Get collection storage profile (database or cache) 2. Extract primary
 * backend and fallback backends 3. Check primary backend health 4. If primary
 * unavailable, try fallbacks in order 5. Return routing target or error
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * // Get router instance (DI-injected)
 * StorageRouterService router = // injected
 *
 * // Route query to appropriate backend(s)
 * RoutingTarget target = runPromise(() ->
 *     router.resolveBackendFor("tenant-1", "products", "SELECT * FROM events")
 * );
 *
 * // Execute query against target
 * StorageConnector connector = getConnector(target.getPrimaryBackendId());
 * List<Event> events = connector.executeQuery(target.getQuery());
 *
 * // If primary fails, retry with fallbacks
 * if (!connector.isAvailable()) {
 *     for (String fallbackId : target.getFallbackBackendIds()) {
 *         StorageConnector fallback = getConnector(fallbackId);
 *         if (fallback.isAvailable()) {
 *             events = fallback.executeQuery(target.getQuery());
 *             break;
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Caching Strategy</b><br>
 * Caches routing decisions with TTL (default 5 minutes) to reduce database
 * lookups. Cache is invalidated when collection profile is updated. Cache key:
 * "tenantId:collectionName"
 *
 * <p>
 * <b>Multi-Tenancy</b><br>
 * All operations enforce tenant isolation: - Tenant parameter required in all
 * methods - Tenant ID from profile must match request tenant - Fallback to
 * default profile only within same tenant
 *
 * @see CollectionStorageProfile
 * @see CollectionStorageProfileRepository
 * @see RoutingTarget
 * @doc.type class
 * @doc.purpose Query routing service using collection storage profiles
 * @doc.layer application
 * @doc.pattern Service
 */
public class StorageRouterService {

    private static final Logger logger = LoggerFactory.getLogger(StorageRouterService.class);

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);
    private static final String METRIC_ROUTING_DECISION = "storage_routing.decision";
    private static final String METRIC_ROUTING_CACHE_HIT = "storage_routing.cache_hit";
    private static final String METRIC_ROUTING_CACHE_MISS = "storage_routing.cache_miss";
    private static final String METRIC_ROUTING_ERROR = "storage_routing.error";

    private final CollectionStorageProfileRepository repository;
    private final MetricsCollector metrics;
    private final Duration cacheTtl;

    /**
     * Cache entry with timestamp for TTL tracking.
     */
    private static class CachedRoutingTarget {

        final RoutingTarget target;
        final long createdAtMillis;

        CachedRoutingTarget(RoutingTarget target) {
            this.target = target;
            this.createdAtMillis = System.currentTimeMillis();
        }

        boolean isExpired(Duration ttl) {
            long ageMillis = System.currentTimeMillis() - createdAtMillis;
            return ageMillis > ttl.toMillis();
        }
    }

    /**
     * Cache: "tenantId:collectionName" → CachedRoutingTarget Thread-safe for
     * concurrent access.
     */
    private final ConcurrentHashMap<String, CachedRoutingTarget> routingCache;

    /**
     * Creates StorageRouterService with default TTL.
     *
     * @param repository profile repository for lookups
     * @param metrics    metrics collector for instrumentation
     */
    public StorageRouterService(
            CollectionStorageProfileRepository repository,
            MetricsCollector metrics) {
        this(repository, metrics, DEFAULT_CACHE_TTL);
    }

    /**
     * Creates StorageRouterService with custom TTL.
     *
     * @param repository profile repository for lookups
     * @param metrics    metrics collector for instrumentation
     * @param cacheTtl   cache TTL duration
     */
    public StorageRouterService(
            CollectionStorageProfileRepository repository,
            MetricsCollector metrics,
            Duration cacheTtl) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl");
        this.routingCache = new ConcurrentHashMap<>();
    }

    /**
     * Resolves storage backend(s) for query on collection.
     *
     * <p>
     * Returns routing target with: - Primary backend for main query execution -
     * Fallback backends for failover - Query parameters (may be modified for
     * multi-backend execution)
     *
     * <p>
     * Tenant isolation enforced: profile must belong to requested tenant.
     *
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param query          query to execute (for planning)
     * @return Promise resolving to RoutingTarget with backend information
     * @throws IllegalArgumentException  if tenantId, collectionName, or query is
     *                                   null/blank
     * @throws ResourceNotFoundException if collection has no storage profile
     */
    public Promise<RoutingTarget> resolveBackendFor(
            String tenantId,
            String collectionName,
            String query) {
        if (tenantId == null || tenantId.isBlank()) {
            logger.error("Cannot resolve backend: missing tenantId");
            metrics.incrementCounter(METRIC_ROUTING_ERROR, "reason", "missing_tenant");
            return Promise.ofException(
                    new IllegalArgumentException("tenantId cannot be null or blank"));
        }

        if (collectionName == null || collectionName.isBlank()) {
            logger.error("Cannot resolve backend: missing collectionName");
            metrics.incrementCounter(METRIC_ROUTING_ERROR, "reason", "missing_collection");
            return Promise.ofException(
                    new IllegalArgumentException("collectionName cannot be null or blank"));
        }

        if (query == null || query.isBlank()) {
            logger.error("Cannot resolve backend: missing query");
            metrics.incrementCounter(METRIC_ROUTING_ERROR, "reason", "missing_query");
            return Promise.ofException(
                    new IllegalArgumentException("query cannot be null or blank"));
        }

        // Check cache first
        String cacheKey = makeCacheKey(tenantId, collectionName);
        CachedRoutingTarget cached = routingCache.get(cacheKey);

        if (cached != null && !cached.isExpired(cacheTtl)) {
            logger.debug(
                    "Routing cache hit [tenant={}, collection={}]",
                    tenantId,
                    collectionName);
            metrics.incrementCounter(METRIC_ROUTING_CACHE_HIT, "tenant", tenantId);
            return Promise.of(cached.target);
        }

        // Cache miss: fetch from repository
        logger.debug(
                "Routing cache miss [tenant={}, collection={}]",
                tenantId,
                collectionName);
        metrics.incrementCounter(METRIC_ROUTING_CACHE_MISS, "tenant", tenantId);

        return repository.findByTenantAndName(tenantId, collectionName)
                .then(profile -> {
                    if (profile.isEmpty()) {
                        logger.warn(
                                "No storage profile found for collection [tenant={}, collection={}]",
                                tenantId,
                                collectionName);
                        metrics.incrementCounter(
                                METRIC_ROUTING_ERROR,
                                "reason", "profile_not_found");
                        return Promise.ofException(
                                new ResourceNotFoundException(
                                        "No storage profile configured for collection: " + collectionName));
                    }

                    CollectionStorageProfile storageProfile = profile.get();

                    // Verify tenant isolation
                    if (!storageProfile.getTenantId().equals(tenantId)) {
                        logger.error(
                                "Tenant mismatch in routing [requested={}, actual={}]",
                                tenantId,
                                storageProfile.getTenantId());
                        metrics.incrementCounter(
                                METRIC_ROUTING_ERROR,
                                "reason", "tenant_mismatch");
                        return Promise.ofException(
                                new BaseException(
                                        ErrorCode.FORBIDDEN,
                                        "Tenant isolation violation in storage routing"));
                    }

                    // Build routing target
                    RoutingTarget target = new RoutingTarget(
                            storageProfile.getPrimaryBackendId(),
                            storageProfile.getFallbackBackendIds(),
                            query,
                            storageProfile.hasFailoverSupport());

                    // Cache the result
                    routingCache.put(cacheKey, new CachedRoutingTarget(target));

                    logger.debug(
                            "Resolved backend routing [tenant={}, collection={}, primary={}, fallbacks={}]",
                            tenantId,
                            collectionName,
                            target.getPrimaryBackendId(),
                            target.getFallbackBackendIds().size());
                    metrics.incrementCounter(
                            METRIC_ROUTING_DECISION,
                            "primary", target.getPrimaryBackendId(),
                            "has_fallback", String.valueOf(target.hasFallback()));

                    return Promise.of(target);
                });
    }

    /**
     * Resolves all available backends for collection.
     *
     * <p>
     * Includes primary + all fallbacks in order of preference.
     *
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @return Promise resolving to list of backend IDs (primary first)
     */
    public Promise<List<String>> getAllBackendsFor(String tenantId, String collectionName) {
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId cannot be null or blank"));
        }

        if (collectionName == null || collectionName.isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("collectionName cannot be null or blank"));
        }

        return repository.findByTenantAndName(tenantId, collectionName)
                .then(profile -> {
                    if (profile.isEmpty()) {
                        logger.warn(
                                "No profile found for backends lookup [tenant={}, collection={}]",
                                tenantId,
                                collectionName);
                        return Promise.ofException(
                                new ResourceNotFoundException(
                                        "No storage profile found for collection: " + collectionName));
                    }

                    CollectionStorageProfile storageProfile = profile.get();

                    // Build backend list: primary + fallbacks
                    List<String> backends = new ArrayList<>();
                    backends.add(storageProfile.getPrimaryBackendId());
                    backends.addAll(storageProfile.getFallbackBackendIds());

                    logger.debug(
                            "Listed all available backends [tenant={}, collection={}, count={}]",
                            tenantId,
                            collectionName,
                            backends.size());

                    return Promise.of(Collections.unmodifiableList(backends));
                });
    }

    /**
     * Invalidates routing cache for collection.
     *
     * <p>
     * Called when storage profile is updated to ensure cache freshness.
     *
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     */
    public void invalidateRoutingCache(String tenantId, String collectionName) {
        if (tenantId == null || collectionName == null) {
            return;
        }

        String cacheKey = makeCacheKey(tenantId, collectionName);
        routingCache.remove(cacheKey);

        logger.debug(
                "Invalidated routing cache [tenant={}, collection={}]",
                tenantId,
                collectionName);
    }

    /**
     * Invalidates all routing cache entries for tenant.
     *
     * <p>
     * Called during tenant cleanup or profile migration.
     *
     * @param tenantId tenant identifier
     */
    public void invalidateTenantCache(String tenantId) {
        if (tenantId == null) {
            return;
        }

        List<String> keysToRemove = routingCache.keySet()
                .stream()
                .filter(key -> key.startsWith(tenantId + ":"))
                .toList();

        keysToRemove.forEach(routingCache::remove);

        logger.debug(
                "Invalidated all routing cache for tenant [tenant={}, count={}]",
                tenantId,
                keysToRemove.size());
    }

    /**
     * Gets cache statistics (test/debug only).
     */
    public Map<String, Long> getCacheStats() {
        return Map.of(
                "cache_size", (long) routingCache.size(),
                "cache_ttl_millis", cacheTtl.toMillis());
    }

    /**
     * Clears all routing cache (test/debug only).
     */
    public void clearCache() {
        routingCache.clear();
        logger.info("Cleared all routing cache");
    }

    /**
     * Creates composite cache key from tenant and collection.
     */
    private String makeCacheKey(String tenantId, String collectionName) {
        return tenantId + ":" + collectionName;
    }

    /**
     * Represents routing target with backend selection.
     *
     * <p>
     * <b>Purpose</b><br>
     * Encapsulates routing decision: which backends to use for query execution.
     *
     * <p>
     * <b>Structure</b><br>
     * - primaryBackendId: Main backend for query execution -
     * fallbackBackendIds: List of fallback backends in preference order -
     * query: Query to execute (may be modified for federation) -
     * supportsFailover: Whether failover to fallbacks is configured
     */
    public static class RoutingTarget {

        private final String primaryBackendId;
        private final List<String> fallbackBackendIds;
        private final String query;
        private final boolean supportsFailover;

        /**
         * Creates new routing target.
         *
         * @param primaryBackendId   primary backend ID
         * @param fallbackBackendIds fallback backend IDs (may be empty)
         * @param query              query to execute
         * @param supportsFailover   whether failover is configured
         */
        public RoutingTarget(
                String primaryBackendId,
                List<String> fallbackBackendIds,
                String query,
                boolean supportsFailover) {
            this.primaryBackendId = Objects.requireNonNull(primaryBackendId);
            // Allow profiles without explicit fallbacks by treating null as an empty list
            this.fallbackBackendIds = fallbackBackendIds == null
                    ? Collections.emptyList()
                    : Objects.requireNonNull(fallbackBackendIds);
            this.query = Objects.requireNonNull(query);
            this.supportsFailover = supportsFailover;
        }

        public String getPrimaryBackendId() {
            return primaryBackendId;
        }

        public List<String> getFallbackBackendIds() {
            return Collections.unmodifiableList(fallbackBackendIds);
        }

        public String getQuery() {
            return query;
        }

        public boolean supportsFailover() {
            return supportsFailover;
        }

        public boolean hasFallback() {
            return !fallbackBackendIds.isEmpty();
        }

        @Override
        public String toString() {
            return "RoutingTarget{"
                    + "primary='" + primaryBackendId + '\''
                    + ", fallbacks=" + fallbackBackendIds.size()
                    + ", failover=" + supportsFailover
                    + '}';
        }
    }
}
