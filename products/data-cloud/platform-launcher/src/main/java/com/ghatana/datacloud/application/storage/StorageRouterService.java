package com.ghatana.datacloud.application.storage;

import com.ghatana.platform.core.exception.BaseException;
import com.ghatana.platform.core.exception.ErrorCode;
import com.ghatana.platform.core.exception.ResourceNotFoundException;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import com.ghatana.datacloud.entity.storage.CollectionStorageProfileRepository;
import com.ghatana.datacloud.resilience.DataCloudCircuitBreakers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

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

    private static final Logger log = LoggerFactory.getLogger(StorageRouterService.class);

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);
    private static final String METRIC_ROUTING_DECISION = "storage_routing.decision";
    private static final String METRIC_ROUTING_CACHE_HIT = "storage_routing.cache_hit";
    private static final String METRIC_ROUTING_CACHE_MISS = "storage_routing.cache_miss";
    private static final String METRIC_ROUTING_ERROR = "storage_routing.error";
    private static final String METRIC_ROUTING_CB_OPEN = "storage_routing.circuit_open";
    private static final String METRIC_ROUTING_CB_FALLBACK = "storage_routing.circuit_fallback";

    private final CollectionStorageProfileRepository repository;
    private final MetricsCollector metrics;
    private final Duration cacheTtl;
    private final Eventloop eventloop;
    private final CircuitBreaker routerCircuitBreaker;

    /** DC3-H5: Bounded Caffeine LRU cache; prevents heap exhaustion at 10K tenants × 10 active collections. */
    private static final int MAX_ROUTING_CACHE_ENTRIES = 100_000;

    /**
     * Cache: "tenantId:collectionName" → RoutingTarget. Bounded LRU with automatic TTL expiry.
     */
    private final Cache<String, RoutingTarget> routingCache;

    /**
     * Creates StorageRouterService with default TTL and a standard circuit breaker.
     *
     * @param repository profile repository for lookups
     * @param metrics    metrics collector for instrumentation
     * @param eventloop  ActiveJ eventloop for circuit breaker async probes
     */
    public StorageRouterService(
            CollectionStorageProfileRepository repository,
            MetricsCollector metrics,
            Eventloop eventloop) {
        this(repository, metrics, DEFAULT_CACHE_TTL, eventloop, DataCloudCircuitBreakers.storageRouter());
    }

    /**
     * Creates StorageRouterService with custom TTL and a standard circuit breaker.
     *
     * @param repository profile repository for lookups
     * @param metrics    metrics collector for instrumentation
     * @param cacheTtl   routing cache TTL
     * @param eventloop  ActiveJ eventloop for circuit breaker async probes
     */
    public StorageRouterService(
            CollectionStorageProfileRepository repository,
            MetricsCollector metrics,
            Duration cacheTtl,
            Eventloop eventloop) {
        this(repository, metrics, cacheTtl, eventloop, DataCloudCircuitBreakers.storageRouter());
    }

    /**
     * Creates StorageRouterService with custom TTL and a provided circuit breaker.
     *
     * <p>Use this constructor in tests or to supply a pre-configured circuit breaker.
     *
     * @param repository           profile repository for lookups
     * @param metrics              metrics collector for instrumentation
     * @param cacheTtl             routing cache TTL
     * @param eventloop            ActiveJ eventloop for circuit breaker async probes
     * @param routerCircuitBreaker pre-built circuit breaker instance
     */
    public StorageRouterService(
            CollectionStorageProfileRepository repository,
            MetricsCollector metrics,
            Duration cacheTtl,
            Eventloop eventloop,
            CircuitBreaker routerCircuitBreaker) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop");
        this.routerCircuitBreaker = Objects.requireNonNull(routerCircuitBreaker, "routerCircuitBreaker");
        this.routingCache = Caffeine.newBuilder()
                .maximumSize(MAX_ROUTING_CACHE_ENTRIES)
                .expireAfterWrite(cacheTtl)
                .build();
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
        this.eventloop = null;
        this.routerCircuitBreaker = null;
        this.routingCache = Caffeine.newBuilder()
                .maximumSize(MAX_ROUTING_CACHE_ENTRIES)
                .expireAfterWrite(cacheTtl)
                .build();
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
            log.error("Cannot resolve backend: missing tenantId");
            metrics.incrementCounter(METRIC_ROUTING_ERROR, "reason", "missing_tenant");
            return Promise.ofException(
                    new IllegalArgumentException("tenantId cannot be null or blank"));
        }

        if (collectionName == null || collectionName.isBlank()) {
            log.error("Cannot resolve backend: missing collectionName");
            metrics.incrementCounter(METRIC_ROUTING_ERROR, "reason", "missing_collection");
            return Promise.ofException(
                    new IllegalArgumentException("collectionName cannot be null or blank"));
        }

        if (query == null || query.isBlank()) {
            log.error("Cannot resolve backend: missing query");
            metrics.incrementCounter(METRIC_ROUTING_ERROR, "reason", "missing_query");
            return Promise.ofException(
                    new IllegalArgumentException("query cannot be null or blank"));
        }

        // Check cache first
        String cacheKey = makeCacheKey(tenantId, collectionName);
        RoutingTarget cached = routingCache.getIfPresent(cacheKey);

        if (cached != null) {
            log.debug(
                    "Routing cache hit [tenant={}, collection={}]",
                    tenantId,
                    collectionName);
            metrics.incrementCounter(METRIC_ROUTING_CACHE_HIT, "tenant", tenantId);
            return Promise.of(cached);
        }

        // Cache miss: fetch from repository (protected by circuit breaker when configured)
        log.debug(
                "Routing cache miss [tenant={}, collection={}]",
                tenantId,
                collectionName);
        metrics.incrementCounter(METRIC_ROUTING_CACHE_MISS, "tenant", tenantId);

        Supplier<Promise<RoutingTarget>> repositoryOp = () ->
                repository.findByTenantAndName(tenantId, collectionName)
                        .then(profile -> buildRoutingTargetFromProfile(
                                tenantId, collectionName, cacheKey, query, profile));

        if (routerCircuitBreaker != null) {
            return routerCircuitBreaker.execute(eventloop, repositoryOp, () -> {
                RoutingTarget stale = routingCache.getIfPresent(cacheKey);
                if (stale != null) {
                    log.warn("Storage router circuit OPEN; serving stale routing "
                                    + "[tenant={}, collection={}]",
                            tenantId, collectionName);
                    metrics.incrementCounter(METRIC_ROUTING_CB_FALLBACK, "tenant", tenantId);
                    return stale;
                }
                metrics.incrementCounter(METRIC_ROUTING_CB_OPEN, "tenant", tenantId);
                log.error("Storage router circuit OPEN; no stale cache available "
                                + "[tenant={}, collection={}]",
                        tenantId, collectionName);
                throw new BaseException(
                        ErrorCode.SERVICE_UNAVAILABLE,
                        "Storage routing unavailable (circuit breaker open)");
            });
        }

        return repositoryOp.get();
    }

    /**
     * Validates the profile lookup result and builds a {@link RoutingTarget}, or returns
     * a failed promise for missing/mismatched profiles.
     */
    private Promise<RoutingTarget> buildRoutingTargetFromProfile(
            String tenantId,
            String collectionName,
            String cacheKey,
            String query,
            Optional<CollectionStorageProfile> profile) {

        if (profile.isEmpty()) {
            log.warn(
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
            log.error(
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

        // Build routing target and cache it
        RoutingTarget target = new RoutingTarget(
                storageProfile.getPrimaryBackendId(),
                storageProfile.getFallbackBackendIds(),
                query,
                storageProfile.hasFailoverSupport());

        routingCache.put(cacheKey, target);

        log.debug(
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
                        log.warn(
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

                    log.debug(
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
        routingCache.invalidate(cacheKey);

        log.debug(
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

        List<String> keysToRemove = routingCache.asMap().keySet()
                .stream()
                .filter(key -> key.startsWith(tenantId + ":"))
                .toList();

        keysToRemove.forEach(routingCache::invalidate);

        log.debug(
                "Invalidated all routing cache for tenant [tenant={}, count={}]",
                tenantId,
                keysToRemove.size());
    }

    /**
     * Gets cache statistics (test/debug only).
     */
    public Map<String, Long> getCacheStats() {
        return Map.of(
                "cache_size", routingCache.estimatedSize(),
                "cache_ttl_millis", cacheTtl.toMillis());
    }

    /**
     * Clears all routing cache (test/debug only).
     */
    public void clearCache() {
        routingCache.invalidateAll();
        log.info("Cleared all routing cache");
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
