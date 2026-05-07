package com.ghatana.datacloud.plugins.cache;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plugin Cache Manager - Provides caching for plugin metadata, instances, and operation results.
 *
 * <p>This manager improves plugin performance by caching:
 * <ul>
 *   <li>Plugin metadata (static information that doesn't change)</li>
 *   <li>Plugin instances after initialization (to avoid re-initialization)</li>
 *   <li>Operation results (configurable TTL-based caching)</li>
 * </ul>
 *
 * <p><b>Cache Invalidation</b><br>
 * Caches are automatically invalidated on plugin lifecycle changes:
 * <ul>
 *   <li>Metadata cache: Never expires (metadata is static)</li>
 *   <li>Instance cache: Invalidated on stop/shutdown</li>
 *   <li>Operation cache: TTL-based, also invalidated on plugin state changes</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * All operations are thread-safe using ConcurrentHashMap and atomic references.
 *
 * @doc.type class
 * @doc.purpose Plugin caching infrastructure
 * @doc.layer plugin
 * @doc.pattern Cache Manager, Singleton
 */
public class PluginCacheManager {

    private static final Logger log = LoggerFactory.getLogger(PluginCacheManager.class);

    private static final Duration DEFAULT_OPERATION_CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_CACHE_SIZE = 1000;

    private static final AtomicReference<PluginCacheManager> INSTANCE = new AtomicReference<>();

    // Metadata cache: pluginId -> PluginMetadata (never expires)
    private final Map<String, PluginMetadata> metadataCache = new ConcurrentHashMap<>();

    // Instance cache: pluginId -> PluginInstance (invalidated on stop/shutdown)
    private final Map<String, CachedPluginInstance> instanceCache = new ConcurrentHashMap<>();

    // Operation cache: composite key -> CachedResult (TTL-based)
    private final Map<String, CachedResult> operationCache = new ConcurrentHashMap<>();

    private final Duration operationCacheTtl;

    private PluginCacheManager(Duration operationCacheTtl) {
        this.operationCacheTtl = operationCacheTtl != null ? operationCacheTtl : DEFAULT_OPERATION_CACHE_TTL;
        log.info("PluginCacheManager initialized with operation cache TTL: {}", operationCacheTtl);
    }

    /**
     * Gets the singleton instance.
     */
    public static PluginCacheManager getInstance() {
        PluginCacheManager instance = INSTANCE.get();
        if (instance == null) {
            instance = new PluginCacheManager(DEFAULT_OPERATION_CACHE_TTL);
            INSTANCE.compareAndSet(null, instance);
        }
        return instance;
    }

    /**
     * Gets the singleton instance, creating it with a custom TTL if not initialized yet.
     */
    public static PluginCacheManager getInstanceWithTtl(Duration operationCacheTtl) {
        PluginCacheManager instance = INSTANCE.get();
        if (instance == null) {
            instance = new PluginCacheManager(operationCacheTtl);
            INSTANCE.compareAndSet(null, instance);
        }
        return instance;
    }

    // ========================================================================
    // Metadata Caching
    // ========================================================================

    /**
     * Caches plugin metadata.
     *
     * @param pluginId  plugin identifier
     * @param metadata plugin metadata
     */
    public void cacheMetadata(String pluginId, PluginMetadata metadata) {
        metadataCache.put(pluginId, metadata);
        log.debug("Cached metadata for plugin: {}", pluginId);
    }

    /**
     * Gets cached plugin metadata.
     *
     * @param pluginId plugin identifier
     * @return cached metadata or null if not cached
     */
    public PluginMetadata getCachedMetadata(String pluginId) {
        return metadataCache.get(pluginId);
    }

    /**
     * Checks if metadata is cached.
     *
     * @param pluginId plugin identifier
     * @return true if cached
     */
    public boolean hasCachedMetadata(String pluginId) {
        return metadataCache.containsKey(pluginId);
    }

    /**
     * Invalidates metadata cache.
     *
     * @param pluginId plugin identifier
     */
    public void invalidateMetadata(String pluginId) {
        metadataCache.remove(pluginId);
        log.debug("Invalidated metadata cache for plugin: {}", pluginId);
    }

    /**
     * Clears all metadata cache.
     */
    public void clearMetadataCache() {
        metadataCache.clear();
        log.info("Cleared all metadata cache");
    }

    // ========================================================================
    // Instance Caching
    // ========================================================================

    /**
     * Caches a plugin instance.
     *
     * @param pluginId  plugin identifier
     * @param instance  plugin instance
     * @param context   plugin context
     * @param state     plugin state
     */
    public void cacheInstance(String pluginId, Object instance, PluginContext context, PluginState state) {
        CachedPluginInstance cached = new CachedPluginInstance(instance, context, state, Instant.now());
        instanceCache.put(pluginId, cached);
        log.debug("Cached instance for plugin: {} with state: {}", pluginId, state);
    }

    /**
     * Gets cached plugin instance.
     *
     * @param pluginId plugin identifier
     * @return cached instance or null if not cached
     */
    public CachedPluginInstance getCachedInstance(String pluginId) {
        CachedPluginInstance cached = instanceCache.get(pluginId);
        if (cached != null) {
            // Check if instance is still valid (not stopped/shutdown)
            if (cached.state() == PluginState.RUNNING || cached.state() == PluginState.INITIALIZED) {
                return cached;
            } else {
                // Remove invalid instance
                instanceCache.remove(pluginId);
                log.debug("Removed invalid cached instance for plugin: {} (state: {})", pluginId, cached.state());
            }
        }
        return null;
    }

    /**
     * Checks if instance is cached and valid.
     *
     * @param pluginId plugin identifier
     * @return true if cached and valid
     */
    public boolean hasCachedInstance(String pluginId) {
        return getCachedInstance(pluginId) != null;
    }

    /**
     * Updates plugin state in cache.
     *
     * @param pluginId plugin identifier
     * @param state    new plugin state
     */
    public void updateInstanceState(String pluginId, PluginState state) {
        CachedPluginInstance cached = instanceCache.get(pluginId);
        if (cached != null) {
            CachedPluginInstance updated = new CachedPluginInstance(
                cached.instance(),
                cached.context(),
                state,
                cached.cachedAt()
            );
            instanceCache.put(pluginId, updated);
            log.debug("Updated state for cached plugin instance: {} -> {}", pluginId, state);
        }
    }

    /**
     * Invalidates instance cache.
     *
     * @param pluginId plugin identifier
     */
    public void invalidateInstance(String pluginId) {
        instanceCache.remove(pluginId);
        log.debug("Invalidated instance cache for plugin: {}", pluginId);
    }

    /**
     * Clears all instance cache.
     */
    public void clearInstanceCache() {
        instanceCache.clear();
        log.info("Cleared all instance cache");
    }

    // ========================================================================
    // Operation Caching
    // ========================================================================

    /**
     * Caches an operation result.
     *
     * @param pluginId   plugin identifier
     * @param operation  operation name
     * @param cacheKey   cache key (e.g., serialized parameters)
     * @param result     operation result
     */
    public void cacheOperationResult(String pluginId, String operation, String cacheKey, Object result) {
        String compositeKey = buildOperationKey(pluginId, operation, cacheKey);
        
        // Enforce max cache size
        if (operationCache.size() >= MAX_CACHE_SIZE) {
            evictOldestEntries();
        }
        
        CachedResult cached = new CachedResult(result, Instant.now(), operationCacheTtl);
        operationCache.put(compositeKey, cached);
        log.debug("Cached operation result for plugin: {} operation: {} key: {}", pluginId, operation, cacheKey);
    }

    /**
     * Gets cached operation result.
     *
     * @param pluginId  plugin identifier
     * @param operation operation name
     * @param cacheKey  cache key
     * @return cached result or null if not cached or expired
     */
    public Object getCachedOperationResult(String pluginId, String operation, String cacheKey) {
        String compositeKey = buildOperationKey(pluginId, operation, cacheKey);
        CachedResult cached = operationCache.get(compositeKey);
        
        if (cached != null) {
            // Check if cache entry is still valid
            if (Instant.now().isBefore(cached.expiresAt())) {
                log.debug("Cache hit for plugin: {} operation: {} key: {}", pluginId, operation, cacheKey);
                return cached.result();
            } else {
                // Remove expired entry
                operationCache.remove(compositeKey);
                log.debug("Cache expired for plugin: {} operation: {} key: {}", pluginId, operation, cacheKey);
            }
        }
        
        log.debug("Cache miss for plugin: {} operation: {} key: {}", pluginId, operation, cacheKey);
        return null;
    }

    /**
     * Checks if operation result is cached and valid.
     *
     * @param pluginId  plugin identifier
     * @param operation operation name
     * @param cacheKey  cache key
     * @return true if cached and valid
     */
    public boolean hasCachedOperationResult(String pluginId, String operation, String cacheKey) {
        return getCachedOperationResult(pluginId, operation, cacheKey) != null;
    }

    /**
     * Invalidates all operation cache for a plugin.
     *
     * @param pluginId plugin identifier
     */
    public void invalidatePluginOperations(String pluginId) {
        operationCache.keySet().removeIf(key -> key.startsWith(pluginId + ":"));
        log.debug("Invalidated all operation cache for plugin: {}", pluginId);
    }

    /**
     * Invalidates specific operation cache for a plugin.
     *
     * @param pluginId  plugin identifier
     * @param operation operation name
     */
    public void invalidatePluginOperation(String pluginId, String operation) {
        String prefix = pluginId + ":" + operation + ":";
        operationCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.debug("Invalidated operation cache for plugin: {} operation: {}", pluginId, operation);
    }

    /**
     * Clears all operation cache.
     */
    public void clearOperationCache() {
        operationCache.clear();
        log.info("Cleared all operation cache");
    }

    // ========================================================================
    // Cache Statistics
    // ========================================================================

    /**
     * Gets cache statistics.
     *
     * @return cache statistics
     */
    public CacheStatistics getStatistics() {
        int metadataCount = metadataCache.size();
        int instanceCount = instanceCache.size();
        int operationCount = operationCache.size();
        
        return new CacheStatistics(metadataCount, instanceCount, operationCount);
    }

    // ========================================================================
    // Private Helper Methods
    // ========================================================================

    private String buildOperationKey(String pluginId, String operation, String cacheKey) {
        return pluginId + ":" + operation + ":" + cacheKey;
    }

    private void evictOldestEntries() {
        // Find and remove oldest entry
        String oldestKey = null;
        Instant oldestTimestamp = null;
        
        for (Map.Entry<String, CachedResult> entry : operationCache.entrySet()) {
            if (oldestTimestamp == null || entry.getValue().cachedAt().isBefore(oldestTimestamp)) {
                oldestTimestamp = entry.getValue().cachedAt();
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            operationCache.remove(oldestKey);
            log.debug("Evicted oldest cache entry: {}", oldestKey);
        }
    }

    // ========================================================================
    // Inner Classes
    // ========================================================================

    /**
     * Cached plugin instance with metadata.
     */
    public record CachedPluginInstance(
        Object instance,
        PluginContext context,
        PluginState state,
        Instant cachedAt
    ) {}

    /**
     * Cached operation result with expiration.
     */
    private record CachedResult(
        Object result,
        Instant cachedAt,
        Duration ttl
    ) {
        Instant expiresAt() {
            return cachedAt.plus(ttl);
        }
    }

    /**
     * Cache statistics.
     */
    public record CacheStatistics(
        int metadataCacheSize,
        int instanceCacheSize,
        int operationCacheSize
    ) {}
}
