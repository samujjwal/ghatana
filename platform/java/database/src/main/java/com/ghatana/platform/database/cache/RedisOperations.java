package com.ghatana.platform.database.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.database.cache.exceptions.CacheOperationException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

/**
 * Utility class for Redis operations.
 *
 * <p>Provides centralized Redis connectivity, error handling, JSON serialization,
 * and namespace management for {@link RedisCacheManager}. All Redis operations
 * are executed within try-with-resources blocks for automatic connection cleanup.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Automatic connection management via JedisPool</li>
 *   <li>JSON serialization/deserialization with Jackson</li>
 *   <li>Namespace prefixing for multi-tenant isolation</li>
 * @doc.type class
 * @doc.purpose Centralized Redis operations with connection pooling and JSON serialization
 * @doc.layer core
 * @doc.pattern Utility, Adapter
 *   <li>Centralized error handling with operation context</li>
 *   <li>Default TTL configuration</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * RedisOperations ops = new RedisOperations(
 *     jedisPool, mapper, "tenant:acme", Duration.ofMinutes(10)
 * );
 * 
 * // Execute Redis operation with automatic connection cleanup
 * String value = ops.execute(
 *     jedis -> jedis.get(ops.namespaceKey("user:123")),
 *     "get"
 * );
 * 
 * // Serialize/deserialize JSON
 * String json = ops.toJson(user);
 * User deserialized = ops.fromJson(json, User.class);
 * }</pre>
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe. JedisPool manages connection pooling; ObjectMapper is configured
 * for thread-safe operation.
 *
 * <h2>Namespace Format:</h2>
 * Keys are prefixed with "{namespace}:" to isolate tenants:
 * - Input: "user:123"
 * - Namespaced: "tenant:acme:user:123"
 *
 * @since 1.0.0
 */
public class RedisOperations {
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final String namespace;
    private final Duration defaultTtl;

    /**
     * Create RedisOperations instance.
     *
     * @param jedisPool    Jedis connection pool (non-null)
     * @param objectMapper Jackson ObjectMapper for JSON (non-null)
     * @param namespace    Namespace prefix for keys (null defaults to "cache")
     * @param defaultTtl   Default TTL for cache entries (null defaults to 10 minutes)
     * @throws NullPointerException if jedisPool or objectMapper is null
     */
    public RedisOperations(JedisPool jedisPool, ObjectMapper objectMapper, String namespace, Duration defaultTtl) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.namespace = namespace == null ? "cache" : namespace;
        this.defaultTtl = defaultTtl == null ? Duration.ofMinutes(10) : defaultTtl;
    }

    /**
     * Execute a Redis operation with automatic connection management.
     * <p>Connections are acquired from pool and automatically released via
     * try-with-resources. All exceptions are wrapped in CacheOperationException.
     *
     * @param operation     function to execute with Jedis connection
     * @param operationName operation name for error context
     * @param <T>           return type
     * @return operation result
     * @throws CacheOperationException if Redis operation fails
     */
    public <T> T execute(Function<Jedis, T> operation, String operationName) {
        try (Jedis jedis = jedisPool.getResource()) {
            return operation.apply(jedis);
        } catch (Exception e) {
            throw new CacheOperationException("Redis operation failed: " + operationName, e);
        }
    }

    /**
     * Add namespace prefix to key.
     *
     * @param key raw key (e.g., "user:123")
     * @return namespaced key (e.g., "tenant:acme:user:123")
     */
    public String namespaceKey(String key) {
        return namespace + ":" + key;
    }

    /**
     * Remove namespace prefix from key.
     *
     * @param namespacedKey namespaced key (e.g., "tenant:acme:user:123")
     * @return raw key without prefix (e.g., "user:123")
     */
    public String removeNamespace(String namespacedKey) {
        String prefix = namespace + ":";
        return namespacedKey.startsWith(prefix) ? namespacedKey.substring(prefix.length()) : namespacedKey;
    }

    /**
     * Serialize value to JSON string.
     *
     * @param value value to serialize
     * @param <T>   value type
     * @return JSON string
     * @throws CacheOperationException if serialization fails
     */
    public <T> String toJson(T value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CacheOperationException("Failed to serialize value to JSON", e);
        }
    }

    /**
     * Deserialize JSON string to typed value.
     *
     * @param json JSON string
     * @param type target type
     * @param <T>  value type
     * @return deserialized value
     * @throws CacheOperationException if deserialization fails
     */
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException e) {
            throw new CacheOperationException("Failed to deserialize value from JSON", e);
        }
    }

    /**
     * Get the configured default TTL.
     *
     * @return default TTL for cache entries
     */
    public Duration getDefaultTtl() {
        return defaultTtl;
    }
}
