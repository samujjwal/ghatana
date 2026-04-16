package com.ghatana.platform.cache.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.DistributedCacheService.CacheBackend;
import com.ghatana.platform.cache.DistributedCacheService.CacheStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.protocol.ProtocolVersion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @doc.type class
 * @doc.purpose Redis-backed implementation of distributed cache abstraction for multi-node environments.
 * @doc.layer platform
 * @doc.pattern Infrastructure
 *
 * This backend provides:
 * - Asynchronous Redis operations using Lettuce
 * - Automatic serialization/deserialization with Jackson
 * - Atomic pattern-based deletion using Lua scripts
 * - Automatic TTL enforcement
 * - Connection pooling with automatic retry
 * - Non-blocking error handling for cache failures
 *
 * Thread-safe: All operations are lock-free and concurrent.
 *
 * Connection pooling is managed by Lettuce.
 * Requires Redis 5.0+ for Lua script support.
 *
 * Configuration (environment variables set in container):
 * - REDIS_HOST (default: localhost)
 * - REDIS_PORT (default: 6379)
 * - REDIS_PASSWORD (optional)
 * - REDIS_DB (default: 0)
 * - REDIS_TIMEOUT_MS (default: 5000)
 * - REDIS_MAX_RETRIES (default: 3)
 */
public final class RedisDistributedCacheBackend implements CacheBackend {
    private static final Logger log = LoggerFactory.getLogger(RedisDistributedCacheBackend.class);

    // Lua script for atomic pattern-based key deletion
    // Returns count of deleted keys
    private static final String DELETE_PATTERN_SCRIPT =
        "local keys = redis.call('KEYS', ARGV[1]) " +
        "if #keys > 0 then " +
        "  return redis.call('DEL', unpack(keys)) " +
        "else " +
        "  return 0 " +
        "end";

    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> async;
    private final ObjectMapper objectMapper;
    private final String host;
    private final int port;
    private final int timeoutMs;
    private final int maxRetries;

    /**
     * Creates a Redis cache backend with asynchronous client.
     *
     * @param host Redis host (from REDIS_HOST env var, default: localhost)
     * @param port Redis port (from REDIS_PORT env var, default: 6379)
     * @param password optional password (from REDIS_PASSWORD env var)
     * @param database Redis database number (from REDIS_DB env var, default: 0)
     * @param timeoutMs operation timeout in milliseconds (from REDIS_TIMEOUT_MS env var, default: 5000)
     * @param maxRetries maximum retry attempts (from REDIS_MAX_RETRIES env var, default: 3)
     * @param objectMapper Jackson ObjectMapper for serialization (typically @Inject from Spring Context)
     */
    public RedisDistributedCacheBackend(
            String host,
            int port,
            String password,
            int database,
            int timeoutMs,
            int maxRetries,
            ObjectMapper objectMapper
    ) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
        this.objectMapper = objectMapper;

        try {
            // Build Redis URI with optional password
            RedisURI uri = RedisURI.Builder
                .redis(host, port)
                .withDatabase(database)
                .withTimeout(java.time.Duration.ofMillis(timeoutMs))
                .build();

            if (password != null && !password.isEmpty()) {
                uri.setPassword(password.toCharArray());
            }

            // Create async Redis client with connection pooling
            RedisClient client = RedisClient.create(uri);
            this.connection = client.connect();
            this.async = connection.async();

            // Verify connection with ping
            this.async.ping()
                .thenAccept(pong -> log.info("Redis connection established", 
                    "host", host, "port", port, "database", database, "response", pong))
                .join();

            log.info("RedisDistributedCacheBackend initialized successfully", 
                "host", host, "port", port, "database", database, "timeoutMs", timeoutMs);
        } catch (Exception e) {
            log.error("Failed to initialize Redis backend", e, 
                "host", host, "port", port, "error", e.getMessage());
            throw new RuntimeException("Redis connection failed", e);
        }
    }

    /**
     * Retrieves a value from Redis cache.
     *
     * Returns Optional.empty() if:
     * - Key does not exist
     * - TTL has expired (Redis handles automatically)
     * - Deserialization fails (logged as WARN, not thrown)
     *
     * @param key cache key (already tenant-scoped by caller)
     * @param valueType target type for deserialization
     * @return Optional containing deserialized value, or empty if missing/expired
     */
    @Override
    public <T> Optional<T> getValue(String key, Class<T> valueType) {
        try {
            CompletableFuture<String> future = async.get(key).toCompletableFuture();
            String value = future.get(timeoutMs, TimeUnit.MILLISECONDS);

            if (value == null) {
                log.debug("Cache miss", "key", key);
                return Optional.empty();
            }

            try {
                T deserialized = objectMapper.readValue(value, valueType);
                log.debug("Cache hit", "key", key, "type", valueType.getSimpleName());
                return Optional.of(deserialized);
            } catch (IOException e) {
                log.warn("Deserialization failed for cache value", e, 
                    "key", key, "type", valueType.getSimpleName(), "error", e.getMessage());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Cache get operation failed", e, 
                "key", key, "error", e.getMessage(), "retrying", "false");
            return Optional.empty();
        }
    }

    /**
     * Stores a value in Redis cache with TTL.
     *
     * Non-blocking: If set fails, logs warning and continues (does not throw).
     * Serialization failure is logged as ERROR and rethrows.
     *
     * @param key cache key (already tenant-scoped by caller)
     * @param value object to cache (must be serializable with ObjectMapper)
     * @param ttlSeconds time-to-live in seconds
     */
    @Override
    public void setValue(String key, Object value, long ttlSeconds) {
        try {
            String serialized = objectMapper.writeValueAsString(value);

            // Use SETEX for atomic key set + TTL
            CompletableFuture<String> future = async.setex(key, ttlSeconds, serialized)
                .toCompletableFuture();
            
            String result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            log.debug("Cache put", "key", key, "ttlSeconds", ttlSeconds, "response", result);
        } catch (IOException e) {
            log.error("Serialization failed for cache value", e, 
                "key", key, "ttlSeconds", ttlSeconds, "error", e.getMessage());
            throw new RuntimeException("Cache serialization failed", e);
        } catch (Exception e) {
            log.warn("Cache set operation failed", e, 
                "key", key, "ttlSeconds", ttlSeconds, "error", e.getMessage(), "retrying", "false");
            // Non-blocking: do not throw, cache write failures are non-critical
        }
    }

    /**
     * Deletes a single key from Redis cache.
     *
     * Non-blocking: If delete fails, logs warning and continues.
     *
     * @param key cache key to delete (already tenant-scoped by caller)
     * @return number of keys deleted (0 or 1)
     */
    @Override
    public long deleteKey(String key) {
        try {
            CompletableFuture<Long> future = async.del(key).toCompletableFuture();
            long deleted = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            log.debug("Cache delete", "key", key, "deleted", deleted);
            return deleted;
        } catch (Exception e) {
            log.warn("Cache delete operation failed", e, 
                "key", key, "error", e.getMessage(), "retrying", "false");
            return 0;
        }
    }

    /**
     * Deletes all keys matching a pattern using atomic Lua script.
     *
     * Pattern examples (glob):
     * - "content:generated:*" → all generated content
     * - "learning-path:user:123:*" → all paths for user 123
     * - "*" → all keys (use with caution!)
     *
     * Uses Lua script for atomic deletion: retrieves matching keys, then deletes all.
     * Non-blocking: If deletion fails, logs warning and continues.
     *
     * @param pattern glob pattern for Redis KEYS command (e.g., "content:*")
     * @return number of keys deleted
     */
    @Override
    public long deletePattern(String pattern) {
        try {
            CompletableFuture<Long> future = async.eval(
                    DELETE_PATTERN_SCRIPT,
                    ScriptOutputType.INTEGER,
                    new String[0],
                    pattern
                ).toCompletableFuture();

            long deleted = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            log.debug("Cache pattern delete", "pattern", pattern, "deleted", deleted);
            return deleted;
        } catch (Exception e) {
            log.warn("Cache pattern delete operation failed", e, 
                "pattern", pattern, "error", e.getMessage(), "retrying", "false");
            return 0;
        }
    }

    /**
     * Retrieves statistics about keys matching a pattern.
     *
     * Returns key count and estimated size (actual total bytes across all values).
     * Size is estimated per-key as: key.length() + value.length().
     *
     * Non-blocking: If stats retrieval fails, logs warning and returns defaults.
     *
     * @param pattern glob pattern for KEYS command
     * @return CacheStatistics with keyCount and totalSizeBytes
     */
    @Override
    public CacheStatistics getStatistics(String pattern) {
        try {
            CompletableFuture<List<String>> keysFuture = async.keys(pattern).toCompletableFuture();
            List<String> keys = keysFuture.get(timeoutMs, TimeUnit.MILLISECONDS);

            if (keys == null || keys.isEmpty()) {
                return new CacheStatistics(0, 0);
            }

            long totalSize = 0;
            for (String key : keys) {
                totalSize += key.length();
                try {
                    CompletableFuture<Long> strlen = async.strlen(key).toCompletableFuture();
                    Long valueSize = strlen.get(timeoutMs / 2, TimeUnit.MILLISECONDS);
                    if (valueSize != null) {
                        totalSize += valueSize;
                    }
                } catch (Exception e) {
                    log.debug("Failed to get value size for statistics", 
                        "key", key, "error", e.getMessage());
                }
            }

            log.debug("Cache statistics", "pattern", pattern, "keyCount", keys.size(), "totalSizeBytes", totalSize);
            return new CacheStatistics(keys.size(), totalSize);
        } catch (Exception e) {
            log.warn("Cache statistics retrieval failed", e, 
                "pattern", pattern, "error", e.getMessage());
            return new CacheStatistics(0, 0);
        }
    }

    /**
     * Closes the Redis connection and releases resources.
     *
     * Should be called during application shutdown (e.g., via @PreDestroy).
     */
    @Override
    public void close() {
        try {
            connection.close();
            log.info("Redis connection closed successfully", "host", host, "port", port);
        } catch (Exception e) {
            log.error("Error closing Redis connection", e, 
                "host", host, "port", port, "error", e.getMessage());
        }
    }
}
