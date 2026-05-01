package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import com.ghatana.yappc.domain.Identifiable;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import com.ghatana.yappc.infrastructure.cache.EntityCache;
import com.ghatana.yappc.infrastructure.datacloud.pagination.PaginatedResult;
import com.ghatana.yappc.infrastructure.datacloud.pagination.PaginationConfig;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generic repository adapter for YAPPC entities using data-cloud.
 *
 * <p>Provides CRUD operations for YAPPC domain objects backed by data-cloud entity storage.
 * All operations resolve the tenant ID from {@link TenantContext} at call-time to ensure proper
 * multi-tenant data isolation. No hardcoded tenant identifier is used.
 *
 * @param <T> Entity type — must implement {@link Identifiable} to ensure it carries a stable UUID
 * @doc.type class
 * @doc.purpose Generic data-cloud repository adapter with proper tenant isolation
 * @doc.layer infrastructure
 * @doc.pattern Repository/Adapter
 */
public class YappcDataCloudRepository<T extends Identifiable<UUID>> {

    private static final Logger LOG = LoggerFactory.getLogger(YappcDataCloudRepository.class);
    private static final int DEFAULT_CACHE_SIZE = 1000;
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);

    private final DataCloudClient client;
    private final YappcEntityMapper mapper;
    private final String collectionName;
    private final Class<T> entityClass;
    private final EntityCache<T> cache;
    private final com.ghatana.yappc.infrastructure.cache.RedisEntityCacheAdapter<T> redisCache;
    private final RetryPolicy retryPolicy;
    private final CircuitBreaker circuitBreaker;
    private final Eventloop eventloop;

    public YappcDataCloudRepository(
            @NotNull DataCloudClient client,
            @NotNull YappcEntityMapper mapper,
            @NotNull String collectionName,
            @NotNull Class<T> entityClass) {
        this(client, mapper, collectionName, entityClass, null, null, null, null);
    }

    public YappcDataCloudRepository(
            @NotNull DataCloudClient client,
            @NotNull YappcEntityMapper mapper,
            @NotNull String collectionName,
            @NotNull Class<T> entityClass,
            EntityCache<T> cache) {
        this(client, mapper, collectionName, entityClass, cache, null, null, null);
    }

    public YappcDataCloudRepository(
            @NotNull DataCloudClient client,
            @NotNull YappcEntityMapper mapper,
            @NotNull String collectionName,
            @NotNull Class<T> entityClass,
            com.ghatana.yappc.infrastructure.cache.RedisEntityCacheAdapter<T> redisCache) {
        this(client, mapper, collectionName, entityClass, null, redisCache, null, null);
    }

    public YappcDataCloudRepository(
            @NotNull DataCloudClient client,
            @NotNull YappcEntityMapper mapper,
            @NotNull String collectionName,
            @NotNull Class<T> entityClass,
            @Nullable EntityCache<T> cache,
            @Nullable com.ghatana.yappc.infrastructure.cache.RedisEntityCacheAdapter<T> redisCache,
            @Nullable RetryPolicy retryPolicy,
            @Nullable CircuitBreaker circuitBreaker) {
        this.client = client;
        this.mapper = mapper;
        this.collectionName = collectionName;
        this.entityClass = entityClass;
        this.cache = cache != null ? cache : (redisCache == null ? new EntityCache<>(DEFAULT_CACHE_TTL, DEFAULT_CACHE_SIZE) : null);
        this.redisCache = redisCache;
        this.retryPolicy = retryPolicy != null ? retryPolicy : createDefaultRetryPolicy();
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : createDefaultCircuitBreaker();
        this.eventloop = Eventloop.create();
    }

    /**
     * Creates default retry policy for Data Cloud operations.
     * Uses exponential backoff with jitter for transient failures.
     */
    private static RetryPolicy createDefaultRetryPolicy() {
        return RetryPolicy.builder()
                .maxRetries(3)
                .initialDelay(Duration.ofMillis(100))
                .maxDelay(Duration.ofSeconds(10))
                .multiplier(2.0)
                .jitter(0.1)
                .retryOn(RuntimeException.class, IllegalStateException.class)
                .maxDuration(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Creates default circuit breaker for Data Cloud operations.
     * Opens after 5 consecutive failures, resets after 30 seconds.
     */
    private static CircuitBreaker createDefaultCircuitBreaker() {
        return CircuitBreaker.builder("data-cloud-" + System.identityHashCode(Thread.currentThread()))
                .failureThreshold(5)
                .successThreshold(2)
                .resetTimeout(Duration.ofSeconds(30))
                .maxBackoff(Duration.ofMinutes(5))
                .backoffMultiplier(2.0)
                .build();
    }

    /**
     * Resolves the current tenant ID from {@link TenantContext}. Throws {@link
     * SecurityException} if no tenant context is active, preventing cross-tenant data access.
     *
     * @return current tenant ID, never null or blank
     * @throws SecurityException if no tenant context is set
     */
    private String resolveTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException(
                    "YappcDataCloudRepository requires an active tenant context. "
                            + "Ensure ApiKeyAuthFilter or TenantExtractionFilter is applied.");
        }
        return tenantId;
    }

    /**
     * Finds an entity by ID, scoped to the current tenant with caching.
     */
    @NotNull
    public Promise<Optional<T>> findById(@NotNull UUID id) {
        String tenantId = resolveTenantId();
        String cacheKey = buildCacheKey(tenantId, id);

        // Check Redis cache first if available
        if (redisCache != null) {
            Optional<T> cached = redisCache.get(id.toString(), entityClass);
            if (cached.isPresent()) {
                LOG.debug("Redis cache hit for {}:{}", collectionName, id);
                return Promise.of(cached);
            }
        }

        // Check in-memory cache if available
        if (cache != null) {
            Optional<T> cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                LOG.debug("In-memory cache hit for {}:{}", collectionName, id);
                return Promise.of(cached);
            }
        }

        // Load from Data-Cloud with retry and circuit breaker
        return circuitBreaker.execute(eventloop, () ->
            retryPolicy.execute(eventloop, () ->
                client.findById(tenantId, collectionName, id.toString())
                    .map(opt -> opt.map(entity -> mapper.fromEntity(entity, entityClass)))
                    .whenResult(opt -> opt.ifPresent(entity -> {
                        // Cache in Redis if available
                        if (redisCache != null) {
                            redisCache.put(id.toString(), entity);
                        }
                        // Cache in memory if available
                        if (cache != null) {
                            cache.put(cacheKey, entity);
                        }
                        LOG.debug("Cached {}:{}", collectionName, id);
                    }))
            )
        );
    }

    /**
     * Saves an entity to data-cloud under the current tenant with cache invalidation.
     */
    @NotNull
    public Promise<T> save(@NotNull T domainEntity) {
        String tenantId = resolveTenantId();
        Map<String, Object> data = mapper.toEntityData(domainEntity);

        return circuitBreaker.execute(eventloop, () ->
            retryPolicy.execute(eventloop, () ->
                client.save(tenantId, collectionName, data)
                    .map(saved -> {
                        T result = mapper.fromEntity(saved, entityClass);
                        // Update Redis cache with saved entity
                        if (redisCache != null) {
                            redisCache.put(result.getId().toString(), result);
                        }
                        // Update in-memory cache with saved entity
                        if (cache != null) {
                            String cacheKey = buildCacheKey(tenantId, result.getId());
                            cache.put(cacheKey, result);
                        }
                        return result;
                    })
            )
        );
    }

    /**
     * Deletes an entity by ID, scoped to the current tenant with cache invalidation.
     */
    @NotNull
    public Promise<Void> deleteById(@NotNull UUID id) {
        String tenantId = resolveTenantId();
        String cacheKey = buildCacheKey(tenantId, id);

        // Invalidate Redis cache if available
        if (redisCache != null) {
            redisCache.invalidate(id.toString());
        }

        // Invalidate in-memory cache if available
        if (cache != null) {
            cache.invalidate(cacheKey);
        }

        return circuitBreaker.execute(eventloop, () ->
            retryPolicy.execute(eventloop, () ->
                client.delete(tenantId, collectionName, id.toString())
            )
        );
    }

    private String buildCacheKey(String tenantId, UUID id) {
        return tenantId + ":" + collectionName + ":" + id.toString();
    }

    /**
     * Finds all entities in the collection, scoped to the current tenant.
     */
    @NotNull
    public Promise<List<T>> findAll() {
        String tenantId = resolveTenantId();
        return circuitBreaker.execute(eventloop, () ->
            retryPolicy.execute(eventloop, () ->
                client.query(tenantId, collectionName, DataCloudClient.Query.limit(1000))
                    .map(entities -> entities.stream()
                            .map(entity -> mapper.fromEntity(entity, entityClass))
                            .collect(Collectors.toList()))
            )
        );
    }

    /**
     * Finds entities by filter criteria, scoped to the current tenant.
     *
     * <p><b>Limitations:</b> This method only supports equality filters.
     * Comparison operators ($gte, $gt, $lte, $lt) are not supported.
     * Sorting is not implemented.
     *
     * <p><b>Note:</b> Full query operators are not supported by the current Data Cloud adapter.
     * Use explicit equality methods (findByFieldEquals) instead.
     *
     * @param filter the filter criteria (field name -&gt; value) - equality only
     * @param sort   ignored - sorting not implemented
     * @param limit  maximum results to return
     * @param offset offset for pagination
     * @return Promise of list of matching entities
     * @deprecated Use explicit equality methods (findByFieldEquals) instead
     */
    @Deprecated(since = "1.0")
    @NotNull
    public Promise<List<T>> findByFilter(
            @NotNull Map<String, Object> filter,
            String sort,
            int limit,
            int offset) {
        String tenantId = resolveTenantId();
        List<DataCloudClient.Filter> filters = filter.entrySet().stream()
                .map(e -> DataCloudClient.Filter.eq(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filters(filters)
                .offset(offset)
                .limit(limit > 0 ? limit : 1000)
                .build();
        return circuitBreaker.execute(eventloop, () ->
            retryPolicy.execute(eventloop, () ->
                client.query(tenantId, collectionName, query)
                    .map(entities -> entities.stream()
                            .map(entity -> mapper.fromEntity(entity, entityClass))
                            .collect(Collectors.toList()))
            )
        );
    }

    /**
     * Finds entities by a single field value, scoped to the current tenant.
     *
     * @param fieldName  the field name to filter by
     * @param fieldValue the field value to match
     * @return Promise of list of matching entities
     */
    @NotNull
    public Promise<List<T>> findByField(
            @NotNull String fieldName,
            @NotNull Object fieldValue) {
        Map<String, Object> filter = new HashMap<>();
        filter.put(fieldName, fieldValue);
        return findByFilter(filter, null, 1000, 0);
    }

    /**
     * Finds entities by a single field value with pagination support.
     *
     * @param fieldName  the field name to filter by
     * @param fieldValue the field value to match
     * @param cursor     optional cursor for pagination (null for first page)
     * @param pageSize   number of items per page (clamped to valid range)
     * @return Promise of paginated matching entities
     */
    @NotNull
    public Promise<PaginatedResult<T>> findByFieldPaginated(
            @NotNull String fieldName,
            @NotNull Object fieldValue,
            String cursor,
            int pageSize) {
        int validatedPageSize = PaginationConfig.clampPageSize(pageSize);
        Map<String, Object> filter = new HashMap<>();
        filter.put(fieldName, fieldValue);
        return findByFilterPaginated(filter, null, cursor, validatedPageSize);
    }

    /**
     * Finds entities by filter criteria with cursor-based pagination.
     *
     * <p>Uses offset-based pagination with cursor encoding for page tracking.
     * The returned cursor can be used to fetch the next page.
     *
     * @param filter   the filter criteria (field name -> value)
     * @param sort     sort specification (format: "field direction", e.g., "createdAt DESC")
     * @param cursor   optional cursor from previous page (null for first page)
     * @param pageSize number of items per page (clamped to 1-500)
     * @return Promise of paginated results with next cursor if more data exists
     */
    @NotNull
    public Promise<PaginatedResult<T>> findByFilterPaginated(
            @NotNull Map<String, Object> filter,
            String sort,
            String cursor,
            int pageSize) {
        if (sort != null && !sort.isBlank()) {
            return Promise.ofException(
                new UnsupportedOperationException("Sorting is not yet implemented by DataCloud adapter"));
        }
        String tenantId = resolveTenantId();
        int validatedPageSize = PaginationConfig.clampPageSize(pageSize);

        // Decode cursor to get offset - use mutable variable for conditional assignment
        int offset;
        if (cursor != null && !cursor.isBlank()) {
            try {
                PaginationConfig.CursorData cursorData = PaginationConfig.decodeCursor(cursor);
                // Extract offset from the last ID (simplified approach)
                offset = Integer.parseInt(cursorData.lastId());
            } catch (Exception e) {
                LOG.warn("Invalid cursor format: {}, starting from beginning", cursor);
                offset = 0;
            }
        } else {
            offset = 0;
        }

        List<DataCloudClient.Filter> filters = filter.entrySet().stream()
                .map(e -> DataCloudClient.Filter.eq(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filters(filters)
                .offset(offset)
                .limit(validatedPageSize + 1) // Fetch one extra to detect hasMore
                .build();

        // Capture offset as final for lambda
        final int currentOffset = offset;

        return circuitBreaker.execute(eventloop, () ->
            retryPolicy.execute(eventloop, () ->
                client.query(tenantId, collectionName, query)
                .map(entities -> {
                    boolean hasMore = entities.size() > validatedPageSize;
                    List<T> items = entities.stream()
                            .limit(validatedPageSize)
                            .map(entity -> mapper.fromEntity(entity, entityClass))
                            .collect(Collectors.toList());

                    String nextCursor = null;
                    if (hasMore) {
                        // Encode next offset as cursor
                        nextCursor = PaginationConfig.encodeCursor(
                            String.valueOf(currentOffset + validatedPageSize), null);
                    }

                    return PaginatedResult.<T>builder()
                            .items(items)
                            .nextCursor(nextCursor)
                            .pageSize(validatedPageSize)
                            .hasMore(hasMore)
                            .totalCount(-1)
                            .build();
                })
        )
    );
    }

    /**
     * Extracts sort value from entity for cursor encoding.
     */
    private String extractSortValue(T item, String sortSpec) {
        if (sortSpec == null || sortSpec.isBlank()) {
            return null;
        }
        // Extract field name from sort spec (e.g., "createdAt DESC" -> "createdAt")
        String fieldName = sortSpec.split("\\s+")[0];
        // Try to extract value via reflection or map access
        // This is a simplified implementation
        return null;
    }
}
