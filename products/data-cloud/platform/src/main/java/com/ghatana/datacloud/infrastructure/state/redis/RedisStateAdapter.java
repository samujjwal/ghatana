package com.ghatana.datacloud.infrastructure.state.redis;

import com.ghatana.datacloud.entity.state.StateAdapter;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis implementation of StateAdapter for centralized distributed state
 * storage.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides high-performance distributed state storage using Redis. Ideal for
 * shared state across multiple operator instances, hot-tier caching, and
 * session state in stream processing pipelines.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * RedisStateAdapter adapter = new RedisStateAdapter("localhost", 6379, "state:");
 *
 * // Store state with TTL
 * adapter.put("tenant:op1:partition0:counter", "42", 60000).get();
 *
 * // Retrieve state
 * Optional<String> value = adapter.get("tenant:op1:partition0:counter").get();
 *
 * // Cleanup
 * adapter.close().get();
 * }</pre>
 *
 * <p>
 * <b>Performance</b><br>
 * - Put: ~1-5ms (network round-trip) - Get: ~1-5ms (network round-trip) - Batch
 * put: ~0.5ms per entry (pipelining) - Batch get: ~0.5ms per entry (MGET)
 *
 * <p>
 * <b>Features</b><br>
 * - TTL support: Native Redis EXPIRE - Persistence: RDB/AOF depending on Redis
 * config - Distribution: Works with Redis Cluster - Pipelining: Batch
 * operations use pipeline for efficiency - Connection pooling: JedisPool for
 * concurrent access
 *
 * <p>
 * <b>Configuration</b><br>
 * Default configuration optimized for: - Low latency operations - Connection
 * pooling (max 128 connections) - Retry on connection failures
 *
 * @see StateAdapter
 * @see <a href="https://redis.io/">Redis</a>
 * @doc.type class
 * @doc.purpose Distributed state adapter using Redis
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class RedisStateAdapter implements StateAdapter<String, String> {

    private static final Logger logger = LoggerFactory.getLogger(RedisStateAdapter.class);

    // Constants
    private static final String ADAPTER_TYPE = "Redis";
    private static final int DEFAULT_POOL_SIZE = 128;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_DATABASE = 0;

    // Redis connection
    private final JedisPool jedisPool;
    private final String keyPrefix;
    private final AtomicBoolean closed;
    private final ExecutorService executor;

    // Configuration
    private final String host;
    private final int port;
    private final int database;

    /**
     * Construct Redis adapter with default configuration.
     *
     * @param host Redis host
     * @param port Redis port
     * @param keyPrefix Prefix for all keys (namespace isolation)
     */
    public RedisStateAdapter(String host, int port, String keyPrefix) {
        this(host, port, DEFAULT_DATABASE, keyPrefix, null);
    }

    /**
     * Construct Redis adapter with password.
     *
     * @param host Redis host
     * @param port Redis port
     * @param database Redis database number
     * @param keyPrefix Prefix for all keys (namespace isolation)
     * @param password Redis password (null if no auth)
     */
    public RedisStateAdapter(String host, int port, int database, String keyPrefix, String password) {
        Objects.requireNonNull(host, "host cannot be null");
        Objects.requireNonNull(keyPrefix, "keyPrefix cannot be null");

        this.host = host;
        this.port = port;
        this.database = database;
        this.keyPrefix = keyPrefix;
        this.closed = new AtomicBoolean(false);

        // Configure connection pool
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(DEFAULT_POOL_SIZE);
        poolConfig.setMaxIdle(DEFAULT_POOL_SIZE / 4);
        poolConfig.setMinIdle(DEFAULT_POOL_SIZE / 8);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWait(Duration.ofMillis(DEFAULT_TIMEOUT_MS));

        // Create pool
        this.jedisPool = new JedisPool(
                poolConfig,
                host,
                port,
                DEFAULT_TIMEOUT_MS,
                password,
                database
        );

        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        logger.info("Redis adapter connected to {}:{} (db={}, prefix={})", host, port, database, keyPrefix);
    }

    /**
     * Construct Redis adapter with existing pool.
     *
     * @param jedisPool Existing Jedis pool
     * @param keyPrefix Prefix for all keys
     */
    public RedisStateAdapter(JedisPool jedisPool, String keyPrefix) {
        Objects.requireNonNull(jedisPool, "jedisPool cannot be null");
        Objects.requireNonNull(keyPrefix, "keyPrefix cannot be null");

        this.jedisPool = jedisPool;
        this.keyPrefix = keyPrefix;
        this.closed = new AtomicBoolean(false);
        this.host = "external";
        this.port = 0;
        this.database = 0;

        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        logger.info("Redis adapter initialized with external pool (prefix={})", keyPrefix);
    }

    /**
     * Build full key with prefix.
     */
    private String fullKey(String key) {
        return keyPrefix + key;
    }

    /**
     * Put value in Redis with TTL.
     *
     * @param key State key
     * @param value State value
     * @param ttlMillis Time-to-live in milliseconds (0 = no expiry)
     * @return Promise completing when stored
     */
    @Override
    public Promise<Void> put(String key, String value, long ttlMillis) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String fullKey = fullKey(key);

                if (ttlMillis > 0) {
                    jedis.set(fullKey, value, SetParams.setParams().px(ttlMillis));
                } else {
                    jedis.set(fullKey, value);
                }

                logger.debug("Put key {} in Redis (ttl={}ms)", key, ttlMillis);
            }
            return (Void) null;
        });
    }

    /**
     * Batch put multiple key-value pairs using pipeline.
     *
     * @param entries Key-value pairs to store
     * @param ttlMillis Time-to-live for all entries
     * @return Promise completing when batch stored
     */
    @Override
    public Promise<Void> putAll(Map<String, String> entries, long ttlMillis) {
        Objects.requireNonNull(entries, "entries cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        if (entries.isEmpty()) {
            return Promise.of(null);
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                Pipeline pipeline = jedis.pipelined();

                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    String fullKey = fullKey(entry.getKey());

                    if (ttlMillis > 0) {
                        pipeline.set(fullKey, entry.getValue(), SetParams.setParams().px(ttlMillis));
                    } else {
                        pipeline.set(fullKey, entry.getValue());
                    }
                }

                pipeline.sync();
                logger.debug("Batch put {} entries in Redis", entries.size());
            }
            return (Void) null;
        });
    }

    /**
     * Get value from Redis.
     *
     * @param key State key
     * @return Promise<Optional<String>> with value or empty
     */
    @Override
    public Promise<Optional<String>> get(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String value = jedis.get(fullKey(key));

                if (value != null) {
                    logger.debug("Got key {} from Redis", key);
                    return Optional.of(value);
                }
                return Optional.<String>empty();
            }
        });
    }

    /**
     * Batch get multiple keys using MGET.
     *
     * @param keys List of state keys
     * @return Promise<Map<String, String>> with found values
     */
    @Override
    public Promise<Map<String, String>> getAll(Collection<String> keys) {
        Objects.requireNonNull(keys, "keys cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        if (keys.isEmpty()) {
            return Promise.of(Collections.emptyMap());
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                List<String> keyList = new ArrayList<>(keys);
                String[] fullKeys = keyList.stream()
                        .map(this::fullKey)
                        .toArray(String[]::new);

                List<String> values = jedis.mget(fullKeys);
                Map<String, String> result = new HashMap<>();

                for (int i = 0; i < keyList.size(); i++) {
                    String value = values.get(i);
                    if (value != null) {
                        result.put(keyList.get(i), value);
                    }
                }

                logger.debug("Batch got {} of {} keys from Redis", result.size(), keys.size());
                return result;
            }
        });
    }

    /**
     * Delete value from Redis.
     *
     * @param key State key
     * @return Promise completing when deleted
     */
    @Override
    public Promise<Void> delete(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(fullKey(key));
                logger.debug("Deleted key {} from Redis", key);
            }
            return (Void) null;
        });
    }

    /**
     * Batch delete multiple keys.
     *
     * @param keys List of state keys
     * @return Promise completing when batch deleted
     */
    @Override
    public Promise<Void> deleteAll(Collection<String> keys) {
        Objects.requireNonNull(keys, "keys cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        if (keys.isEmpty()) {
            return Promise.of(null);
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String[] fullKeys = keys.stream()
                        .map(this::fullKey)
                        .toArray(String[]::new);

                jedis.del(fullKeys);
                logger.debug("Batch deleted {} keys from Redis", keys.size());
            }
            return (Void) null;
        });
    }

    /**
     * Clear all entries with the configured prefix.
     *
     * <p>
     * WARNING: Uses SCAN to find keys, may be slow for large datasets.
     *
     * @return Promise completing when cleared
     */
    @Override
    public Promise<Void> clear() {
        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        logger.warn("DESTRUCTIVE OPERATION: clear() called on RedisStateAdapter (prefix='{}') — "
                + "scanning and deleting ALL keys matching this prefix.", keyPrefix);

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String pattern = keyPrefix + "*";
                String cursor = "0";
                int deletedCount = 0;

                do {
                    var scanResult = jedis.scan(cursor, new redis.clients.jedis.params.ScanParams()
                            .match(pattern)
                            .count(1000));

                    cursor = scanResult.getCursor();
                    List<String> foundKeys = scanResult.getResult();

                    if (!foundKeys.isEmpty()) {
                        jedis.del(foundKeys.toArray(new String[0]));
                        deletedCount += foundKeys.size();
                    }
                } while (!"0".equals(cursor));

                logger.info("Cleared {} keys from Redis (prefix={})", deletedCount, keyPrefix);
            }
            return (Void) null;
        });
    }

    /**
     * Check if key exists.
     *
     * @param key State key
     * @return Promise<Boolean> true if exists
     */
    @Override
    public Promise<Boolean> exists(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.exists(fullKey(key));
            }
        });
    }

    /**
     * Get Redis statistics.
     *
     * @return Promise<Map<String, Object>> with statistics
     */
    @Override
    public Promise<Map<String, Object>> getStatistics() {
        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                Map<String, Object> stats = new HashMap<>();
                stats.put("adapter_type", ADAPTER_TYPE);
                stats.put("host", host);
                stats.put("port", port);
                stats.put("database", database);
                stats.put("key_prefix", keyPrefix);
                stats.put("timestamp", System.currentTimeMillis());

                stats.put("pool_active", jedisPool.getNumActive());
                stats.put("pool_idle", jedisPool.getNumIdle());
                stats.put("pool_waiters", jedisPool.getNumWaiters());

                String info = jedis.info("memory");
                if (info != null) {
                    for (String line : info.split("\n")) {
                        if (line.startsWith("used_memory:")) {
                            stats.put("used_memory", Long.parseLong(line.split(":")[1].trim()));
                        } else if (line.startsWith("used_memory_human:")) {
                            stats.put("used_memory_human", line.split(":")[1].trim());
                        }
                    }
                }

                return stats;
            }
        });
    }

    /**
     * Get database size in bytes.
     *
     * @return Promise<Long> approximate used memory
     */
    @Override
    public Promise<Long> getSize() {
        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String info = jedis.info("memory");
                for (String line : info.split("\n")) {
                    if (line.startsWith("used_memory:")) {
                        return Long.parseLong(line.split(":")[1].trim());
                    }
                }
                return 0L;
            }
        });
    }

    /**
     * Get key count for the prefix.
     *
     * <p>
     * Note: Uses SCAN, may be slow for large datasets.
     *
     * @return Promise<Long> number of keys with prefix
     */
    @Override
    public Promise<Long> getCount() {
        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String pattern = keyPrefix + "*";
                String cursor = "0";
                long count = 0;

                do {
                    var scanResult = jedis.scan(cursor, new redis.clients.jedis.params.ScanParams()
                            .match(pattern)
                            .count(1000));

                    cursor = scanResult.getCursor();
                    count += scanResult.getResult().size();
                } while (!"0".equals(cursor));

                return count;
            }
        });
    }

    /**
     * Close Redis pool and release resources.
     *
     * @return Promise completing when closed
     */
    @Override
    public Promise<Void> close() {
        if (closed.compareAndSet(false, true)) {
            try {
                if (jedisPool != null && !jedisPool.isClosed()) {
                    jedisPool.close();
                }
                logger.info("Redis adapter closed");
                return Promise.of(null);
            } catch (Exception e) {
                logger.error("Failed to close Redis adapter", e);
                return Promise.ofException(e);
            }
        }
        return Promise.of(null);
    }

    /**
     * Get adapter type identifier.
     *
     * @return "Redis"
     */
    @Override
    public String getAdapterType() {
        return ADAPTER_TYPE;
    }

    /**
     * Check if adapter is healthy.
     *
     * @return Promise<Boolean> true if Redis is reachable
     */
    @Override
    public Promise<Boolean> isHealthy() {
        if (closed.get()) {
            return Promise.of(false);
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String pong = jedis.ping();
                return "PONG".equals(pong);
            } catch (Exception e) {
                logger.warn("Redis health check failed", e);
                return false;
            }
        });
    }

    /**
     * Get key prefix.
     *
     * @return Key prefix used for namespace isolation
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Scan keys by pattern.
     *
     * @param pattern Key pattern (without prefix)
     * @return Map of matching keys and values
     */
    public Promise<Map<String, String>> scanKeys(String pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String fullPattern = keyPrefix + pattern;
                String cursor = "0";
                Map<String, String> result = new HashMap<>();

                do {
                    var scanResult = jedis.scan(cursor, new redis.clients.jedis.params.ScanParams()
                            .match(fullPattern)
                            .count(1000));

                    cursor = scanResult.getCursor();
                    List<String> foundKeys = scanResult.getResult();

                    if (!foundKeys.isEmpty()) {
                        List<String> values = jedis.mget(foundKeys.toArray(new String[0]));
                        for (int i = 0; i < foundKeys.size(); i++) {
                            String value = values.get(i);
                            if (value != null) {
                                String originalKey = foundKeys.get(i).substring(keyPrefix.length());
                                result.put(originalKey, value);
                            }
                        }
                    }
                } while (!"0".equals(cursor));

                logger.debug("Scanned {} keys matching pattern '{}'", result.size(), pattern);
                return result;
            }
        });
    }

    /**
     * Set TTL on existing key.
     *
     * @param key State key
     * @param ttlMillis New TTL in milliseconds
     * @return Promise<Boolean> true if TTL was set
     */
    public Promise<Boolean> expire(String key, long ttlMillis) {
        Objects.requireNonNull(key, "key cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                long result = jedis.pexpire(fullKey(key), ttlMillis);
                return result == 1;
            }
        });
    }

    /**
     * Get remaining TTL for a key.
     *
     * @param key State key
     * @return Promise<Long> TTL in milliseconds, -1 if no TTL, -2 if key
     * doesn't exist
     */
    public Promise<Long> getTtl(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.pttl(fullKey(key));
            }
        });
    }

    /**
     * Increment numeric value atomically.
     *
     * @param key State key
     * @param delta Amount to increment (can be negative)
     * @return Promise<Long> new value after increment
     */
    public Promise<Long> increment(String key, long delta) {
        Objects.requireNonNull(key, "key cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("Redis adapter is closed"));
        }

        return Promise.ofBlocking(executor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.incrBy(fullKey(key), delta);
            }
        });
    }
}
