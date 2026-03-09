package com.ghatana.datacloud.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.datacloud.entity.MetaField;
import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Caches collection metadata in Redis with TTL-based expiration.
 *
 * <p><b>Purpose</b><br>
 * Provides fast access to collection and field metadata using Redis caching.
 * Reduces database load for frequently accessed metadata while maintaining consistency
 * through cache invalidation on updates.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetadataCacheService cache = new MetadataCacheService(
 *     repository,
 *     cacheManager,
 *     metrics
 * );
 *
 * // Get collection (from cache or repository)
 * Promise<Optional<MetaCollection>> promise = cache.getCollection(
 *     "tenant-123",
 *     "products"
 * );
 *
 * // In test with EventloopTestBase:
 * Optional<MetaCollection> collection = runPromise(() -> promise);
 *
 * // Invalidate cache on update
 * cache.invalidateCollection("tenant-123", "products");
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Cache service in infrastructure layer
 * - Uses core/redis-cache for caching
 * - Uses CollectionRepository for fallback
 * - Uses MetricsCollector for cache metrics
 * - Enforces tenant isolation
 *
 * <p><b>Caching Strategy</b><br>
 * - TTL: 1 hour (configurable)
 * - Cache key format: `meta:collection:{tenantId}:{name}`
 * - Invalidation: On create/update/delete
 * - Metrics: Hit/miss rates, cache size
 *
 * <p><b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state in Redis.
 *
 * @see MetaCollection
 * @see CollectionRepository
 * @see MetricsCollector
 * @doc.type class
 * @doc.purpose Redis caching for collection metadata with TTL
 * @doc.layer product
 * @doc.pattern Cache Service (Infrastructure Layer)
 */
public class MetadataCacheService {

    private static final Logger logger = LoggerFactory.getLogger(MetadataCacheService.class);

    private static final long DEFAULT_TTL_SECONDS = 3600; // 1 hour
    private static final String COLLECTION_KEY_PREFIX = "meta:collection:";
    private static final String FIELD_KEY_PREFIX = "meta:field:";
    private static final String TENANT_KEY_PREFIX = "meta:tenant:";

    private final CollectionRepository repository;
    private final CacheManager cacheManager;
    private final MetricsCollector metrics;
    private final long ttlSeconds;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new metadata cache service.
     *
     * @param repository the collection repository (required)
     * @param cacheManager the cache manager (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if any parameter is null
     */
    public MetadataCacheService(
            CollectionRepository repository,
            CacheManager cacheManager,
            MetricsCollector metrics) {
        this(repository, cacheManager, metrics, DEFAULT_TTL_SECONDS);
    }

    /**
     * Creates a new metadata cache service with custom TTL.
     *
     * @param repository the collection repository (required)
     * @param cacheManager the cache manager (required)
     * @param metrics the metrics collector (required)
     * @param ttlSeconds the cache TTL in seconds
     * @throws NullPointerException if repository, cacheManager, or metrics is null
     * @throws IllegalArgumentException if ttlSeconds is <= 0
     */
    public MetadataCacheService(
            CollectionRepository repository,
            CacheManager cacheManager,
            MetricsCollector metrics,
            long ttlSeconds) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null");
        this.cacheManager = Objects.requireNonNull(cacheManager, "CacheManager must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL must be > 0");
        }
        this.ttlSeconds = ttlSeconds;

        // Configure ObjectMapper for JSON serialization
        this.objectMapper = JsonUtils.getDefaultMapper();
    }

    /**
     * Gets a collection from cache or repository.
     *
     * <p><b>Behavior</b><br>
     * 1. Try to get from cache
     * 2. If not found, get from repository
     * 3. Cache the result
     * 4. Emit metrics (hit/miss)
     *
     * @param tenantId the tenant identifier (required)
     * @param name the collection name (required)
     * @return Promise of Optional containing the collection if found
     */
    public Promise<Optional<MetaCollection>> getCollection(String tenantId, String name) {
        validateTenantId(tenantId);
        Objects.requireNonNull(name, "Collection name must not be null");

        String key = buildCollectionKey(tenantId, name);

        return cacheManager.get(key)
            .then(cached -> {
                if (cached != null) {
                    metrics.incrementCounter("cache.hit",
                        "tenant", tenantId,
                        "type", "collection");
                    
                    // Record cache entry size for monitoring
                    metrics.getMeterRegistry()
                        .gauge("cache.entry.size", 
                            Tags.of("tenant", tenantId, "type", "collection", "collection", name),
                            cached.length());
                    
                    logger.debug("Cache hit: collection={}", name);
                    return Promise.of(Optional.of(deserializeCollection(cached)));
                }

                metrics.incrementCounter("cache.miss",
                    "tenant", tenantId,
                    "type", "collection");
                logger.debug("Cache miss: collection={}", name);

                return repository.findByName(tenantId, name)
                    .then(collection -> {
                        if (collection.isPresent()) {
                            return cacheManager.set(key, serializeCollection(collection.get()), ttlSeconds)
                                .map(v -> collection);
                        }
                        return Promise.of(collection);
                    });
            })
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    metrics.incrementCounter("cache.error",
                        "tenant", tenantId,
                        "type", "collection");
                    logger.warn("Cache error for collection: {}", name, ex);
                }
            });
    }

    /**
     * Invalidates collection cache.
     *
     * @param tenantId the tenant identifier (required)
     * @param name the collection name (required)
     * @return Promise of void
     */
    public Promise<Void> invalidateCollection(String tenantId, String name) {
        validateTenantId(tenantId);
        Objects.requireNonNull(name, "Collection name must not be null");

        String key = buildCollectionKey(tenantId, name);
        return cacheManager.delete(key)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("cache.invalidate",
                        "tenant", tenantId,
                        "type", "collection");
                    
                    // Record eviction metric
                    metrics.incrementCounter("cache.eviction",
                        "tenant", tenantId,
                        "type", "collection",
                        "reason", "manual");
                    
                    logger.debug("Invalidated collection cache: {}", name);
                } else {
                    logger.warn("Failed to invalidate collection cache: {}", name, ex);
                }
            });
    }

    /**
     * Invalidates all collections for a tenant.
     *
     * @param tenantId the tenant identifier (required)
     * @return Promise of void
     */
    public Promise<Void> invalidateTenant(String tenantId) {
        validateTenantId(tenantId);

        String pattern = COLLECTION_KEY_PREFIX + tenantId + ":*";
        return cacheManager.deletePattern(pattern)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("cache.invalidate_tenant",
                        "tenant", tenantId);
                    logger.debug("Invalidated all collections for tenant: {}", tenantId);
                } else {
                    logger.warn("Failed to invalidate tenant cache: {}", tenantId, ex);
                }
            });
    }

    /**
     * Gets a field from cache or repository.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param fieldName the field name (required)
     * @return Promise of Optional containing the field if found
     */
    public Promise<Optional<MetaField>> getField(String tenantId, String collectionName, String fieldName) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(fieldName, "Field name must not be null");

        String key = buildFieldKey(tenantId, collectionName, fieldName);

        return cacheManager.get(key)
            .then(cached -> {
                if (cached != null) {
                    metrics.incrementCounter("cache.hit",
                        "tenant", tenantId,
                        "type", "field");
                    return Promise.of(Optional.of(deserializeField(cached)));
                }

                metrics.incrementCounter("cache.miss",
                    "tenant", tenantId,
                    "type", "field");

                // For now, return empty (would need FieldRepository for full implementation)
                return Promise.of(Optional.empty());
            });
    }

    /**
     * Invalidates field cache.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param fieldName the field name (required)
     * @return Promise of void
     */
    public Promise<Void> invalidateField(String tenantId, String collectionName, String fieldName) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(fieldName, "Field name must not be null");

        String key = buildFieldKey(tenantId, collectionName, fieldName);
        return cacheManager.delete(key)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("cache.invalidate",
                        "tenant", tenantId,
                        "type", "field");
                    logger.debug("Invalidated field cache: {}.{}", collectionName, fieldName);
                } else {
                    logger.warn("Failed to invalidate field cache: {}.{}", collectionName, fieldName, ex);
                }
            });
    }

    /**
     * Invalidates all fields for a collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @return Promise of void
     */
    public Promise<Void> invalidateCollectionFields(String tenantId, String collectionName) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name must not be null");

        String pattern = FIELD_KEY_PREFIX + tenantId + ":" + collectionName + ":*";
        return cacheManager.deletePattern(pattern)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("cache.invalidate_fields",
                        "tenant", tenantId,
                        "collection", collectionName);
                    logger.debug("Invalidated all fields for collection: {}", collectionName);
                } else {
                    logger.warn("Failed to invalidate collection fields: {}", collectionName, ex);
                }
            });
    }

    /**
     * Builds cache key for collection.
     *
     * @param tenantId the tenant identifier
     * @param name the collection name
     * @return cache key
     */
    private String buildCollectionKey(String tenantId, String name) {
        return COLLECTION_KEY_PREFIX + tenantId + ":" + name;
    }

    /**
     * Builds cache key for field.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @param fieldName the field name
     * @return cache key
     */
    private String buildFieldKey(String tenantId, String collectionName, String fieldName) {
        return FIELD_KEY_PREFIX + tenantId + ":" + collectionName + ":" + fieldName;
    }

    /**
     * Serializes collection for caching.
     *
     * <p><b>Format</b><br>
     * Uses Jackson to serialize to JSON string.
     * Includes all fields including JSONB fields (permission, applications, validationSchema).
     *
     * @param collection the collection to serialize (required)
     * @return JSON string representation of collection
     * @throws CacheSerializationException if serialization fails
     */
    private String serializeCollection(MetaCollection collection) {
        Objects.requireNonNull(collection, "Collection must not be null");

        try {
            return objectMapper.writeValueAsString(collection);
        } catch (Exception e) {
            metrics.incrementCounter("cache.serialization_error", "type", "collection");
            logger.error("Failed to serialize collection: id={}, name={}",
                collection.getId(), collection.getName(), e);
            throw new CacheSerializationException("Failed to serialize collection", e);
        }
    }

    /**
     * Deserializes collection from cache.
     *
     * <p><b>Format</b><br>
     * Uses Jackson to deserialize from JSON string.
     * Handles all fields including JSONB fields.
     *
     * @param cached the cached JSON data (required)
     * @return deserialized collection
     * @throws CacheSerializationException if deserialization fails
     */
    private MetaCollection deserializeCollection(String cached) {
        Objects.requireNonNull(cached, "Cached data must not be null");

        try {
            return objectMapper.readValue(cached, MetaCollection.class);
        } catch (Exception e) {
            metrics.incrementCounter("cache.deserialization_error", "type", "collection");
            logger.error("Failed to deserialize collection from cache", e);
            throw new CacheSerializationException("Failed to deserialize collection", e);
        }
    }

    /**
     * Serializes field for caching.
     *
     * <p><b>Format</b><br>
     * Uses Jackson to serialize to JSON string.
     * Includes all fields including JSONB fields (validation, uiConfig).
     *
     * @param field the field to serialize (required)
     * @return JSON string representation of field
     * @throws CacheSerializationException if serialization fails
     */
    private String serializeField(MetaField field) {
        Objects.requireNonNull(field, "Field must not be null");

        try {
            return objectMapper.writeValueAsString(field);
        } catch (Exception e) {
            metrics.incrementCounter("cache.serialization_error", "type", "field");
            logger.error("Failed to serialize field: id={}, name={}",
                field.getId(), field.getName(), e);
            throw new CacheSerializationException("Failed to serialize field", e);
        }
    }

    /**
     * Deserializes field from cache.
     *
     * <p><b>Format</b><br>
     * Uses Jackson to deserialize from JSON string.
     * Handles all fields including JSONB fields.
     *
     * @param cached the cached JSON data (required)
     * @return deserialized field
     * @throws CacheSerializationException if deserialization fails
     */
    private MetaField deserializeField(String cached) {
        Objects.requireNonNull(cached, "Cached data must not be null");

        try {
            return objectMapper.readValue(cached, MetaField.class);
        } catch (Exception e) {
            metrics.incrementCounter("cache.deserialization_error", "type", "field");
            logger.error("Failed to deserialize field from cache", e);
            throw new CacheSerializationException("Failed to deserialize field", e);
        }
    }

    /**
     * Validates tenant ID is not null or empty.
     *
     * @param tenantId the tenant ID to validate
     * @throws IllegalArgumentException if tenantId is null or empty
     */
    private void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID must not be null or empty");
        }
    }

    /**
     * Exception thrown when serialization/deserialization fails.
     *
     * @doc.type exception
     * @doc.purpose Cache serialization error
     * @doc.layer product
     * @doc.pattern Exception
     */
    public static class CacheSerializationException extends RuntimeException {
        public CacheSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Cache manager interface for Redis operations.
     *
     * <p><b>Note</b><br>
     * This is a placeholder interface. In production, use core/redis-cache CacheManager.
     */
    public interface CacheManager {
        /**
         * Gets a value from cache.
         *
         * @param key the cache key
         * @return Promise of cached value or null if not found
         */
        Promise<String> get(String key);

        /**
         * Sets a value in cache with TTL.
         *
         * @param key the cache key
         * @param value the value to cache
         * @param ttlSeconds the TTL in seconds
         * @return Promise of void
         */
        Promise<Void> set(String key, String value, long ttlSeconds);

        /**
         * Deletes a value from cache.
         *
         * @param key the cache key
         * @return Promise of void
         */
        Promise<Void> delete(String key);

        /**
         * Deletes values matching a pattern.
         *
         * @param pattern the key pattern (e.g., "meta:collection:tenant-123:*")
         * @return Promise of void
         */
        Promise<Void> deletePattern(String pattern);
    }
}
