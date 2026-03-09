package com.ghatana.platform.database.cache;

import com.ghatana.platform.core.util.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Thread-safe in-memory cache implementation with TTL support.
 * 
 * Uses ConcurrentHashMap for thread-safety and a background thread for expiration cleanup.
 *
 * @param <K> the key type
 * @param <V> the value type
 *
 * @doc.type class
 * @doc.purpose Thread-safe in-memory cache with TTL and background expiration cleanup
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class InMemoryCache<K, V> implements Cache<K, V>, AutoCloseable {
    
    private static final Logger log = LoggerFactory.getLogger(InMemoryCache.class);
    
    private final Map<K, CacheEntry<V>> cache;
    private final Duration defaultTtl;
    private final ScheduledExecutorService cleanupExecutor;
    private final String name;
    
    private InMemoryCache(@NotNull String name, @NotNull Duration defaultTtl, @NotNull Duration cleanupInterval) {
        this.name = Preconditions.requireNonBlank(name, "name");
        this.defaultTtl = Preconditions.requireNonNull(defaultTtl, "defaultTtl");
        this.cache = new ConcurrentHashMap<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-cleanup-" + name);
            t.setDaemon(true);
            return t;
        });
        
        // Schedule periodic cleanup
        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpired,
                cleanupInterval.toMillis(),
                cleanupInterval.toMillis(),
                TimeUnit.MILLISECONDS
        );
        
        log.debug("InMemoryCache '{}' initialized with default TTL: {}", name, defaultTtl);
    }
    
    /**
     * Create a new in-memory cache with default settings.
     */
    public static <K, V> InMemoryCache<K, V> create(@NotNull String name) {
        return new InMemoryCache<>(name, Duration.ofMinutes(10), Duration.ofMinutes(1));
    }
    
    /**
     * Create a new in-memory cache with custom TTL.
     */
    public static <K, V> InMemoryCache<K, V> create(@NotNull String name, @NotNull Duration defaultTtl) {
        return new InMemoryCache<>(name, defaultTtl, Duration.ofMinutes(1));
    }
    
    /**
     * Create a new in-memory cache with custom TTL and cleanup interval.
     */
    public static <K, V> InMemoryCache<K, V> create(
            @NotNull String name, 
            @NotNull Duration defaultTtl, 
            @NotNull Duration cleanupInterval) {
        return new InMemoryCache<>(name, defaultTtl, cleanupInterval);
    }
    
    @Override
    public Optional<V> get(@NotNull K key) {
        Preconditions.requireNonNull(key, "key");
        CacheEntry<V> entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null) {
                cache.remove(key);
            }
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }
    
    @Override
    public V getOrCompute(@NotNull K key, @NotNull Supplier<V> loader) {
        return getOrCompute(key, loader, defaultTtl);
    }
    
    @Override
    public V getOrCompute(@NotNull K key, @NotNull Supplier<V> loader, @NotNull Duration ttl) {
        Preconditions.requireNonNull(key, "key");
        Preconditions.requireNonNull(loader, "loader");
        Preconditions.requireNonNull(ttl, "ttl");
        
        CacheEntry<V> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.value();
        }
        
        V value = loader.get();
        put(key, value, ttl);
        return value;
    }
    
    @Override
    public void put(@NotNull K key, @NotNull V value) {
        put(key, value, defaultTtl);
    }
    
    @Override
    public void put(@NotNull K key, @NotNull V value, @NotNull Duration ttl) {
        Preconditions.requireNonNull(key, "key");
        Preconditions.requireNonNull(value, "value");
        Preconditions.requireNonNull(ttl, "ttl");
        
        Instant expiresAt = Instant.now().plus(ttl);
        cache.put(key, new CacheEntry<>(value, expiresAt));
    }
    
    @Override
    public boolean putIfAbsent(@NotNull K key, @NotNull V value) {
        return putIfAbsent(key, value, defaultTtl);
    }
    
    @Override
    public boolean putIfAbsent(@NotNull K key, @NotNull V value, @NotNull Duration ttl) {
        Preconditions.requireNonNull(key, "key");
        Preconditions.requireNonNull(value, "value");
        Preconditions.requireNonNull(ttl, "ttl");
        
        Instant expiresAt = Instant.now().plus(ttl);
        CacheEntry<V> existing = cache.putIfAbsent(key, new CacheEntry<>(value, expiresAt));
        
        if (existing == null) {
            return true;
        }
        
        // If existing entry is expired, replace it
        if (existing.isExpired()) {
            cache.put(key, new CacheEntry<>(value, expiresAt));
            return true;
        }
        
        return false;
    }
    
    @Override
    public Optional<V> remove(@NotNull K key) {
        Preconditions.requireNonNull(key, "key");
        CacheEntry<V> entry = cache.remove(key);
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }
    
    @Override
    public boolean contains(@NotNull K key) {
        Preconditions.requireNonNull(key, "key");
        CacheEntry<V> entry = cache.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            return false;
        }
        return true;
    }
    
    @Override
    public void clear() {
        cache.clear();
        log.debug("Cache '{}' cleared", name);
    }
    
    @Override
    public long size() {
        return cache.size();
    }
    
    /**
     * Get the cache name.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the default TTL.
     */
    public Duration getDefaultTtl() {
        return defaultTtl;
    }
    
    @Override
    public void close() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        cache.clear();
        log.debug("Cache '{}' closed", name);
    }
    
    private void cleanupExpired() {
        int removed = 0;
        for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
            if (entry.getValue().isExpired()) {
                cache.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.trace("Cache '{}' cleanup removed {} expired entries", name, removed);
        }
    }
    
    private record CacheEntry<V>(@NotNull V value, @NotNull Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
