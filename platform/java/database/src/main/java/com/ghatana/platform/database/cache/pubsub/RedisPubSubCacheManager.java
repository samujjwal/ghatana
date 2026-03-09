package com.ghatana.platform.database.cache.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.database.cache.CacheManager;
import com.ghatana.platform.database.cache.RedisCacheManager;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Redis cache manager with pub/sub invalidation support.
 *
 * <p><b>Purpose</b><br>
 * Extends {@link RedisCacheManager} with automatic cache invalidation
 * broadcasting via Redis pub/sub. When cache entries are removed or cleared,
 * invalidation messages are published to notify other instances.
 
 *
 * @doc.type class
 * @doc.purpose Redis pub sub cache manager
 * @doc.layer core
 * @doc.pattern Manager
*/
public class RedisPubSubCacheManager implements CacheManager {
    private static final Logger logger = LoggerFactory.getLogger(RedisPubSubCacheManager.class);
    
    private final CacheManager delegate;
    private final RedisPubSubManager pubSubManager;
    private final String namespace;
    private final String instanceId;

    /**
     * Create a new RedisPubSubCacheManager.
     *
     * @param delegate The underlying cache manager to delegate to
     * @param pubSubManager The pub/sub manager for broadcasting invalidations
     * @param namespace The cache namespace
     * @param instanceId The instance ID for tracking invalidation sources
     */
    public RedisPubSubCacheManager(CacheManager delegate, RedisPubSubManager pubSubManager, 
                                 String namespace, String instanceId) {
        this.delegate = delegate;
        this.pubSubManager = pubSubManager;
        this.namespace = namespace;
        this.instanceId = instanceId;
        
        // Subscribe to invalidation messages
        this.pubSubManager.subscribe(this::handleRemoteInvalidation);
    }

    @Override
    public <T> Promise<Optional<T>> get(String key, Class<T> type) {
        return delegate.get(key, type);
    }
    
    @Override
    public <T> Promise<Void> put(String key, T value) {
        return delegate.put(key, value);
    }
    
    @Override
    public <T> Promise<Void> put(String key, T value, Duration ttl) {
        return delegate.put(key, value, ttl);
    }

    @Override
    public Promise<Boolean> remove(String key) {
        return delegate.remove(key)
            .then(removed -> {
                if (removed) {
                    CacheInvalidationMessage message = CacheInvalidationMessage.invalidateKeys(
                        Set.of(key),
                        namespace,
                        instanceId
                    );
                    return pubSubManager.publish(message)
                        .map(ignored -> removed);
                } else {
                    return Promise.of(removed);
                }
            });
    }

    @Override
    public Promise<Void> clear() {
        return delegate.clear()
            .then(ignored -> {
                CacheInvalidationMessage message = CacheInvalidationMessage.clearNamespace(
                    namespace,
                    instanceId
                );
                return pubSubManager.publish(message);
            });
    }

    
    @Override
    public Promise<Boolean> exists(String key) {
        return delegate.exists(key);
    }
    
    @Override
    public <T> Promise<T> getOrCompute(String key, Class<T> type, Supplier<Promise<T>> supplier) {
        return delegate.getOrCompute(key, type, supplier);
    }

    @Override
    public <T> Promise<T> getOrCompute(String key, Class<T> type, Duration ttl, Supplier<Promise<T>> supplier) {
        return delegate.getOrCompute(key, type, ttl, supplier);
    }
    
    @Override
    public Promise<Long> increment(String key) {
        return delegate.increment(key);
    }
    
    @Override
    public Promise<Long> increment(String key, long delta) {
        return delegate.increment(key, delta);
    }
    
    @Override
    public Promise<Set<String>> keys(String pattern) {
        return delegate.keys(pattern);
    }

    @Override
    public Promise<Long> clearPattern(String pattern) {
        return delegate.clearPattern(pattern)
            .then(deleted -> {
                CacheInvalidationMessage message = CacheInvalidationMessage.invalidatePattern(
                    pattern,
                    namespace,
                    instanceId
                );
                return pubSubManager.publish(message)
                    .map(ignored -> deleted);
            });
    }

    @Override
    public CacheStats getStats() {
        return delegate.getStats();
    }

    @Override
    public Promise<Boolean> expire(String key, Duration ttl) {
        return delegate.expire(key, ttl);
    }

    @Override
    public Promise<Duration> ttl(String key) {
        return delegate.ttl(key);
    }
    
    // ========================================================================
    // Remote Invalidation Handling
    // ========================================================================
    
    /**
     * Handle invalidation message from remote instance
     *
     * <p>Called when another instance publishes invalidation.
     * Applies the invalidation to local cache.
     *
     * @param message Cache invalidation message
     */
    private void handleRemoteInvalidation(CacheInvalidationMessage message) {
        if (!namespace.equals(message.getNamespace())) {
            logger.trace("[RedisPubSubCache] Skipping invalidation for different namespace: {}",
                message.getNamespace());
            return;
        }
        
        logger.debug("[RedisPubSubCache] Handling remote invalidation: {}", message);
        
        try {
            switch (message.getOperation()) {
                case INVALIDATE_KEYS:
                    // Invalidate specific keys
                    message.getKeys().forEach(key ->
                        delegate.remove(key)
                            .whenResult(removed -> logger.trace("[RedisPubSubCache] Invalidated key: {} (removed={})", key, removed))
                            .whenException(e -> logger.error("[RedisPubSubCache] Failed to invalidate key: {}", key, e))
                    );
                    break;
                    
                case INVALIDATE_PATTERN:
                    // Invalidate keys matching pattern
                    delegate.clearPattern(message.getPattern())
                        .whenResult(deleted -> logger.trace("[RedisPubSubCache] Invalidated pattern: {} (deleted={})", message.getPattern(), deleted))
                        .whenException(e -> logger.error("[RedisPubSubCache] Failed to invalidate pattern: {}", message.getPattern(), e));
                    break;
                    
                case CLEAR_NAMESPACE:
                    // Clear entire namespace
                    delegate.clear()
                        .whenResult(v -> logger.trace("[RedisPubSubCache] Cleared namespace: {}", namespace))
                        .whenException(e -> logger.error("[RedisPubSubCache] Failed to clear namespace: {}", namespace, e));
                    break;
            }
        } catch (Exception e) {
            logger.error("[RedisPubSubCache] Failed to apply remote invalidation: {}", message, e);
        }
    }
    
    /**
     * Get pub/sub statistics
     *
     * @return Pub/sub statistics
     */
    public RedisPubSubManager.PubSubStats getPubSubStats() {
        return pubSubManager.getStats();
    }
}
