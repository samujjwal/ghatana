package com.ghatana.platform.database.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.database.cache.exceptions.CacheOperationException;
import io.activej.promise.Promise;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Redis-backed implementation of {@link CacheManager}.
 *
 * <p>This implementation uses Jedis for Redis connectivity and Jackson for JSON
 * serialization. All operations are Promise-based and thread-safe. Keys are automatically
 * namespaced to support multi-tenant isolation.
 *
 * <h2>Key Features:</h2>
 * <ul>
 * @doc.type class
 * @doc.purpose Redis-backed cache manager with Promise-based async operations
 * @doc.layer core
 * @doc.pattern Implementation, Cache Manager
 *   <li>Namespace isolation (all keys prefixed with "{namespace}:")</li>
 *   <li>JSON serialization via Jackson ObjectMapper</li>
 *   <li>Configurable default TTL (default: 10 minutes)</li>
 *   <li>Atomic statistics tracking (hit/miss/eviction counts)</li>
 *   <li>Connection pooling via JedisPool</li>
 *   <li>Automatic error wrapping in CacheOperationException</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * JedisPool pool = new JedisPool("localhost", 6379);
 * ObjectMapper mapper = new ObjectMapper();
 * 
 * RedisCacheManager cache = new RedisCacheManager(
 *     pool, mapper, "tenant:acme", Duration.ofMinutes(15)
 * );
 * 
 * // Store user with 5-minute TTL
 * cache.put("user:123", user, Duration.ofMinutes(5))
 *     .whenComplete(() -> logger.info("User cached"));
 * 
 * // Get-or-compute pattern
 * cache.getOrCompute("config", Config.class, 
 *     () -> loadFromDatabase())
 *     .whenResult(cfg -> logger.info("Config: {}", cfg));
 * 
 * // View statistics
 * CacheStats stats = cache.getStats();
 * logger.info("Hit rate: {}", stats.getHitRate());
 * }</pre>
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe. JedisPool manages connection pooling internally.
 * Statistics are tracked via atomic counters.
 *
 * <h2>Performance Characteristics:</h2>
 * - Get/Put/Remove: O(1) - single Redis operation
 * - Pattern operations (keys/clearPattern): O(N) - scans all keys
 * - Increment: O(1) - atomic Redis INCRBY
 *
 * <h2>Error Handling:</h2>
 * All Redis errors are wrapped in {@link CacheOperationException} with
 * operation name for debugging.
 *
 * @since 1.0.0
 */
public class RedisCacheManager implements CacheManager {
    private final RedisOperations redisOperations;
    private final Duration defaultTtl;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();
    private final AtomicLong totalLoadTime = new AtomicLong();

    /**
     * Creates a new RedisCacheManager with the specified parameters.
     *
     * @param jedisPool    The Jedis connection pool
     * @param objectMapper The ObjectMapper for JSON serialization
     * @param namespace    The namespace for cache keys
     * @param defaultTtl   The default time-to-live for cache entries
     */
    public RedisCacheManager(JedisPool jedisPool, ObjectMapper objectMapper, String namespace, Duration defaultTtl) {
        this.redisOperations = new RedisOperations(jedisPool, objectMapper, namespace, defaultTtl);
        this.defaultTtl = defaultTtl != null ? defaultTtl : Duration.ofMinutes(10);
    }

    /**
     * Creates a new RedisCacheManager with default TTL of 10 minutes.
     *
     * @param jedisPool    The Jedis connection pool
     * @param objectMapper The ObjectMapper for JSON serialization
     * @param namespace    The namespace for cache keys
     */
    public RedisCacheManager(JedisPool jedisPool, ObjectMapper objectMapper, String namespace) {
        this(jedisPool, objectMapper, namespace, Duration.ofMinutes(10));
    }

    @Override
    public <T> Promise<Optional<T>> get(String key, Class<T> type) {
        return runBlocking(() -> {
            String value = redisOperations.execute(
                jedis -> jedis.get(redisOperations.namespaceKey(key)),
                "get"
            );
            
            if (value == null) {
                missCount.incrementAndGet();
                return Optional.empty();
            }
            
            hitCount.incrementAndGet();
            return Optional.of(redisOperations.fromJson(value, type));
        });
    }

    @Override
    public <T> Promise<Void> put(String key, T value) {
        return put(key, value, defaultTtl);
    }

    @Override
    public <T> Promise<Void> put(String key, T value, Duration ttl) {
        return runBlocking(() -> {
            String json = redisOperations.toJson(value);
            String namespacedKey = redisOperations.namespaceKey(key);
            
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                redisOperations.execute(
                    jedis -> jedis.set(namespacedKey, json),
                    "set"
                );
            } else {
                redisOperations.execute(
                    jedis -> jedis.setex(namespacedKey, (int) ttl.getSeconds(), json),
                    "setex"
                );
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> remove(String key) {
        return runBlocking(() -> 
            redisOperations.execute(
                jedis -> jedis.del(redisOperations.namespaceKey(key)) > 0,
                "del"
            )
        );
    }

    @Override
    public Promise<Boolean> exists(String key) {
        return runBlocking(() -> 
            redisOperations.execute(
                jedis -> jedis.exists(redisOperations.namespaceKey(key)),
                "exists"
            )
        );
    }

    @Override
    public Promise<Set<String>> keys(String pattern) {
        return runBlocking(() -> {
            Set<String> keys = redisOperations.execute(
                jedis -> jedis.keys(redisOperations.namespaceKey(pattern.replace("*", "")) + "*"),
                "keys"
            );
            
            Set<String> result = new HashSet<>();
            for (String k : keys) {
                result.add(redisOperations.removeNamespace(k));
            }
            return result;
        });
    }

    @Override
    public Promise<Void> clear() {
        return clearPattern("*").map(deleted -> null);
    }

    @Override
    public Promise<Long> clearPattern(String pattern) {
        return runBlocking(() -> {
            Set<String> keys = redisOperations.execute(
                jedis -> jedis.keys(redisOperations.namespaceKey(pattern.replace("*", "")) + "*"),
                "keys"
            );
            
            long deleted = redisOperations.execute(
                jedis -> {
                    long count = 0;
                    for (String k : keys) {
                        count += jedis.del(k);
                    }
                    return count;
                },
                "del"
            );
            
            evictionCount.addAndGet(deleted);
            return deleted;
        });
    }

    @Override
    public <T> Promise<T> getOrCompute(String key, Class<T> type, Supplier<Promise<T>> supplier) {
        return getOrCompute(key, type, defaultTtl, supplier);
    }

    @Override
    public <T> Promise<T> getOrCompute(String key, Class<T> type, Duration ttl, Supplier<Promise<T>> supplier) {
        return get(key, type).then(optional -> {
            if (optional.isPresent()) {
                return Promise.of(optional.get());
            }
            
            Instant start = Instant.now();
            return supplier.get().then(value ->
                put(key, value, ttl).map(ignored -> {
                    Duration loadTime = Duration.between(start, Instant.now());
                    totalLoadTime.addAndGet(loadTime.toMillis());
                    return value;
                })
            );
        });
    }

    @Override
    public Promise<Long> increment(String key) {
        return increment(key, 1);
    }

    @Override
    public Promise<Long> increment(String key, long delta) {
        return runBlocking(() -> 
            redisOperations.execute(
                jedis -> jedis.incrBy(redisOperations.namespaceKey(key), delta),
                "incrBy"
            )
        );
    }

    @Override
    public Promise<Boolean> expire(String key, Duration ttl) {
        return runBlocking(() -> {
            Long result = redisOperations.execute(
                jedis -> jedis.expire(redisOperations.namespaceKey(key), (int) ttl.getSeconds()),
                "expire"
            );
            return result != null && result == 1L;
        });
    }

    @Override
    public Promise<Duration> ttl(String key) {
        return runBlocking(() -> {
            Long ttlSeconds = redisOperations.execute(
                jedis -> jedis.ttl(redisOperations.namespaceKey(key)),
                "ttl"
            );
            
            if (ttlSeconds == null) {
                return Duration.ZERO;
            }
            if (ttlSeconds == -1L) {
                return Duration.ofSeconds(Long.MAX_VALUE);
            }
            if (ttlSeconds == -2L) {
                return Duration.ZERO;
            }
            return Duration.ofSeconds(ttlSeconds);
        });
    }

    @Override
    public CacheStats getStats() {
        return new CacheStatsImpl();
    }

    private static <T> Promise<T> runBlocking(Callable<T> supplier) {
        try {
            return Promise.of(supplier.call());
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Statistics implementation for the cache.
     */
    private class CacheStatsImpl implements CacheStats {
        @Override
        public long getHitCount() { 
            return hitCount.get(); 
        }

        @Override
        public long getMissCount() { 
            return missCount.get(); 
        }

        @Override
        public double getHitRate() {
            long hits = getHitCount();
            long total = hits + getMissCount();
            return total == 0 ? 0.0 : (double) hits / total;
        }

        @Override
        public long getEvictionCount() { 
            return evictionCount.get(); 
        }

        @Override
        public long getSize() { 
            return -1; 
        }

        @Override
        public Duration getAverageLoadTime() {
            long totalTime = totalLoadTime.get();
            long count = getHitCount() + getMissCount();
            return count == 0 ? Duration.ZERO : Duration.ofMillis(totalTime / count);
        }
    }
}
