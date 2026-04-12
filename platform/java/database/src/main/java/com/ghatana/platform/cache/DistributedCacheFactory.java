package com.ghatana.platform.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Factory for constructing {@link DistributedCachePort} instances.
 *
 * <p>Callers should prefer the factory methods over instantiating adapter classes
 * directly so that the correct two-level composition is applied automatically.</p>
 *
 * <h3>Usage — production (Redis L2 + Caffeine L1)</h3>
 * <pre>{@code
 * JedisPool pool = DistributedCacheFactory.createJedisPool("redis-host", 6379);
 * DistributedCachePort<String, RiskMetrics> cache = DistributedCacheFactory.create(
 *     pool, mapper, RiskMetrics.class, executor,
 *     "finance.risk",
 *     Duration.ofMinutes(5),   // L2 TTL
 *     10_000,                   // L1 max size
 *     Duration.ofMinutes(1)    // L1 TTL
 * );
 * }</pre>
 *
 * <h3>Usage — tests / single-node</h3>
 * <pre>{@code
 * DistributedCachePort<String, RiskMetrics> cache =
 *     DistributedCacheFactory.createInMemory(10_000, Duration.ofMinutes(5));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Factory for DistributedCachePort instances (KRQ-05)
 * @doc.layer platform
 * @doc.pattern Factory
 * @since 1.0.0
 */
public final class DistributedCacheFactory {

    private DistributedCacheFactory() {}

    /**
     * Creates a production-grade two-level cache (L1=Caffeine, L2=Redis).
     *
     * @param jedisPool  shared connection pool
     * @param mapper     Jackson mapper for value serialization
     * @param valueType  value class for deserialization
     * @param executor   blocking executor for Redis I/O
     * @param namespace  logical namespace prefix (e.g., {@code "finance.risk"})
     * @param l2Ttl      L2 (Redis) TTL per entry
     * @param l1MaxSize  L1 (Caffeine) maximum entry count
     * @param l1Ttl      L1 (Caffeine) TTL per entry (should be ≤ l2Ttl)
     */
    public static <K, V> DistributedCachePort<K, V> create(
            JedisPool jedisPool,
            ObjectMapper mapper,
            Class<V> valueType,
            Executor executor,
            String namespace,
            Duration l2Ttl,
            long l1MaxSize,
            Duration l1Ttl) {

        Objects.requireNonNull(jedisPool, "jedisPool must not be null");
        Objects.requireNonNull(mapper, "mapper must not be null");
        Objects.requireNonNull(valueType, "valueType must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(l2Ttl, "l2Ttl must not be null");
        Objects.requireNonNull(l1Ttl, "l1Ttl must not be null");

        InMemoryCacheAdapter<K, V> l1 = new InMemoryCacheAdapter<>(l1MaxSize, l1Ttl);
        RedisDistributedCacheAdapter<K, V> l2 =
                new RedisDistributedCacheAdapter<>(jedisPool, mapper, valueType, executor, namespace, l2Ttl);

        return new WriteThroughDistributedCache<>(l1, l2);
    }

    /**
     * Creates an in-memory only cache with Caffeine. Suitable for single-node or test use.
     */
    public static <K, V> DistributedCachePort<K, V> createInMemory(long maximumSize, Duration ttl) {
        return new InMemoryCacheAdapter<>(maximumSize, ttl);
    }

    /**
     * Creates a {@link JedisPool} from standard Redis connection parameters.
     *
     * @param host    Redis hostname
     * @param port    Redis port (default 6379)
     * @param timeout connection timeout in milliseconds
     * @return configured {@link JedisPool}
     */
    public static JedisPool createJedisPool(String host, int port, int timeout) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(20);
        config.setMaxIdle(10);
        config.setMinIdle(2);
        config.setTestOnBorrow(true);
        config.setTestWhileIdle(true);
        return new JedisPool(config, host, port, timeout);
    }

    /**
     * Creates a {@link JedisPool} with default timeout (2000ms).
     */
    public static JedisPool createJedisPool(String host, int port) {
        return createJedisPool(host, port, 2000);
    }
}
