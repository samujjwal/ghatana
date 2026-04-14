package com.ghatana.audio.video.infrastructure.cache;

import com.ghatana.platform.cache.DistributedCachePort;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * @doc.type class
 * @doc.purpose Audio-video specific cache wrapper around platform DistributedCachePort
 * @doc.layer infrastructure
 * @doc.pattern Cache
 */
public class AudioVideoCache<K, V> {
    
    private static final Logger LOG = LoggerFactory.getLogger(AudioVideoCache.class);
    
    private final DistributedCachePort<K, V> cachePort;
    private final String namespace;
    
    public AudioVideoCache(DistributedCachePort<K, V> cachePort, String namespace) {
        this.cachePort = Objects.requireNonNull(cachePort, "cachePort cannot be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
    }
    
    /**
     * Get value from cache
     */
    public Promise<Optional<V>> get(K key) {
        Objects.requireNonNull(key, "key cannot be null");
        
        return cachePort.get(key)
            .whenResult(opt -> {
                if (opt.isPresent()) {
                    LOG.trace("Cache hit: namespace={}, key={}", namespace, key);
                } else {
                    LOG.trace("Cache miss: namespace={}, key={}", namespace, key);
                }
            })
            .whenException(e -> LOG.error("Cache get error: namespace={}, key={}", namespace, key, e));
    }
    
    /**
     * Put value in cache with default TTL
     */
    public Promise<Void> put(K key, V value) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        
        return cachePort.put(key, value)
            .whenResult(v -> LOG.trace("Cache put: namespace={}, key={}", namespace, key))
            .whenException(e -> LOG.error("Cache put error: namespace={}, key={}", namespace, key, e));
    }
    
    /**
     * Put value with custom TTL
     */
    public Promise<Void> put(K key, V value, Duration ttl) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");
        
        return cachePort.put(key, value, ttl)
            .whenResult(v -> LOG.trace("Cache put: namespace={}, key={}, ttl={}s", 
                namespace, key, ttl.getSeconds()))
            .whenException(e -> LOG.error("Cache put error: namespace={}, key={}", namespace, key, e));
    }
    
    /**
     * Get or load value using provided loader function
     */
    public Promise<V> getOrLoad(K key, Function<K, Promise<V>> loader) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(loader, "loader cannot be null");
        
        return cachePort.getOrLoad(key, loader)
            .whenException(e -> LOG.error("Cache load error: namespace={}, key={}", namespace, key, e));
    }
    
    /**
     * Invalidate a single key
     */
    public Promise<Void> invalidate(K key) {
        Objects.requireNonNull(key, "key cannot be null");
        
        return cachePort.invalidate(key)
            .whenResult(v -> LOG.debug("Cache invalidate: namespace={}, key={}", namespace, key))
            .whenException(e -> LOG.error("Cache invalidate error: namespace={}, key={}", namespace, key, e));
    }
    
    /**
     * Invalidate all entries in namespace
     */
    public Promise<Void> invalidateAll() {
        return cachePort.invalidateAll()
            .whenResult(v -> LOG.info("Cache invalidate all: namespace={}", namespace))
            .whenException(e -> LOG.error("Cache invalidate all error: namespace={}", namespace, e));
    }
    
    /**
     * Check if cache is healthy
     */
    public boolean isHealthy() {
        return cachePort.isHealthy();
    }
    
    /**
     * Build tenant-scoped key
     */
    public String buildKey(String tenantId, String id) {
        return namespace + ":" + tenantId + ":" + id;
    }
}
