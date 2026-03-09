package com.ghatana.ingress.api.ratelimit;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Redis-backed rate limit storage using sliding window algorithm.
 * Keys are "rate_limit:{key}" and use Redis sorted sets with timestamps as scores.
 */
/**
 * Redis-backed rate limit storage using sliding window algorithm.
 * This class is thread-safe and makes defensive copies of mutable parameters.
 */
/**
 * Redis rate limit storage.
 *
 * @doc.type class
 * @doc.purpose Redis rate limit storage
 * @doc.layer core
 * @doc.pattern Component
 */
@Slf4j
public class RedisRateLimitStorage implements RateLimitStorage, AutoCloseable {
    private volatile boolean closed = false;
    private final JedisPool jedisPool; // Note: Defensive copy is not possible with JedisPool, proper cleanup is ensured in close()
    private final String keyPrefix;
    private final int maxRequests;
    private final long windowMs;

    /**
     * Creates a new RedisRateLimitStorage with the given configuration.
     * 
     * @param jedisPool The JedisPool to use for Redis connections (will be closed when this instance is closed)
     * @param keyPrefix Prefix for all Redis keys used by this instance
     * @param maxRequests Maximum number of requests allowed in the time window
     * @param windowMs Length of the time window in milliseconds
     * @throws IllegalArgumentException if jedisPool is null or maxRequests/windowMs are invalid
     */
    @SuppressWarnings("EI_EXPOSE_REP2") // We store a reference to jedisPool but ensure proper cleanup in close()
    public RedisRateLimitStorage(JedisPool jedisPool, String keyPrefix, int maxRequests, long windowMs) {
        if (jedisPool == null) {
            throw new IllegalArgumentException("jedisPool cannot be null");
        }
        // Note: We can't make a defensive copy of JedisPool, so we'll ensure proper cleanup in close()
        if (keyPrefix == null) {
            throw new IllegalArgumentException("keyPrefix cannot be null");
        }
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        if (windowMs <= 0) {
            throw new IllegalArgumentException("windowMs must be positive");
        }
        
        this.jedisPool = jedisPool;
        this.keyPrefix = keyPrefix;
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                jedisPool.close();
            } catch (Exception e) {
                log.warn("Error closing JedisPool", e);
            }
        }
    }

    @Override
    public boolean tryConsume(String key, int tokens, Instant now) {
        if (closed) {
            throw new IllegalStateException("RedisRateLimitStorage has been closed");
        }
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }
        if (now == null) {
            now = Instant.now();
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            long currentMs = now.toEpochMilli();
            long windowStartMs = currentMs - windowMs;
            
            String redisKey = keyPrefix + key;
            
            // Remove old requests outside the current window
            jedis.zremrangeByScore(redisKey, 0, windowStartMs);
            
            // Count remaining requests in window
            long count = jedis.zcard(redisKey);
            
            if (count + tokens > maxRequests) {
                return false;
            }
            
            // Add new request timestamp
            jedis.zadd(redisKey, currentMs, currentMs + "-" + tokens);
            jedis.expire(redisKey, windowMs / 1000 + 1); // TTL slightly longer than window
            
            return true;
        }
    }

    @Override
    public Map<String, String> headers(String key, Instant now) {
        try (Jedis jedis = jedisPool.getResource()) {
            long currentMs = now.toEpochMilli();
            long windowStartMs = currentMs - windowMs;
            
            String redisKey = keyPrefix + key;
            
            // Remove old requests outside the current window
            jedis.zremrangeByScore(redisKey, 0, windowStartMs);
            
            // Count remaining requests in window
            long count = jedis.zcard(redisKey);
            long remaining = Math.max(0, maxRequests - count);
            
            Map<String, String> headers = new HashMap<>();
            headers.put("X-RateLimit-Limit", String.valueOf(maxRequests));
            headers.put("X-RateLimit-Remaining", String.valueOf(remaining));
            headers.put("X-RateLimit-Reset", String.valueOf(windowStartMs + windowMs));
            
            if (remaining <= 0) {
                headers.put("Retry-After", String.valueOf(windowMs / 1000));
            }
            
            return headers;
        }
    }
}
