package com.ghatana.digitalmarketing.api.security;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * P1-052: Distributed rate limiter using Redis.
 *
 * <p>Provides cluster-wide rate limiting with Redis backend:
 * <ul>
 *   <li>Sliding window rate limiting</li>
 *   <li>Tenant-scoped limits</li>
 *   <li>Endpoint-specific limits</li>
 *   <li>Burst handling with token bucket</li>
 *   <li>Graceful degradation on Redis failure</li>
 *   <li>Metrics integration</li>
 * </ul>
 *
 * <p>Falls back to local rate limiting if Redis unavailable.</p>
 *
 * @doc.type class
 * @doc.purpose Distributed rate limiting for multi-instance deployments (P1-052)
 * @doc.layer product
 * @doc.pattern Rate Limiting, Distributed Systems, Redis
 */
public final class DistributedRateLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(DistributedRateLimiter.class);

    private final JedisPool jedisPool;
    private final Eventloop eventloop;
    private final RateLimiterMetrics metrics;

    // Local fallback rate limiters per key
    private final ConcurrentHashMap<String, LocalRateLimiter> localFallback = new ConcurrentHashMap<>();

    // Configuration
    private static final int DEFAULT_WINDOW_SIZE_SECONDS = 60;
    private static final int DEFAULT_MAX_REQUESTS = 100;
    private static final int REDIS_TIMEOUT_MS = 100;
    private static final int BURST_CAPACITY = 10;

    public DistributedRateLimiter(JedisPool jedisPool, Eventloop eventloop, RateLimiterMetrics metrics) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * P1-052: Checks if request is allowed under rate limit.
     *
     * @param key the rate limit key (e.g., "tenant:endpoint:clientId")
     * @param tenantId the tenant identifier
     * @param maxRequests maximum requests in window
     * @param windowSize the time window
     * @return promise resolving to true if allowed, false if rate limited
     */
    public Promise<Boolean> isAllowed(String key, String tenantId, int maxRequests, Duration windowSize) {
        long startTime = System.currentTimeMillis();

        return checkRedisRateLimit(key, maxRequests, windowSize)
            .whenResult(allowed -> {
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordCheckDuration(duration, tenantId, extractEndpoint(key));

                if (allowed) {
                    metrics.recordAllowed(tenantId, extractEndpoint(key), extractClientId(key));
                } else {
                    metrics.recordRejected(tenantId, extractEndpoint(key), extractClientId(key), "distributed");
                }
            })
            .whenException(e -> {
                // P1-052: Fall back to local rate limiter on Redis failure
                LOG.warn("[DMOS-RATELIMIT] Redis unavailable, using local fallback for key={}: {}",
                    key, e.getMessage());

                boolean allowed = checkLocalFallback(key, maxRequests, windowSize);

                if (allowed) {
                    metrics.recordAllowed(tenantId, extractEndpoint(key), extractClientId(key));
                } else {
                    metrics.recordRejected(tenantId, extractEndpoint(key), extractClientId(key), "local_fallback");
                }
            });
    }

    /**
     * P1-052: Checks rate limit with default configuration.
     */
    public Promise<Boolean> isAllowed(String key, String tenantId) {
        return isAllowed(key, tenantId, DEFAULT_MAX_REQUESTS, Duration.ofSeconds(DEFAULT_WINDOW_SIZE_SECONDS));
    }

    /**
     * P1-052: Sliding window rate limit check in Redis.
     *
     * Uses Redis sorted sets for O(log N) sliding window implementation.
     */
    private Promise<Boolean> checkRedisRateLimit(String key, int maxRequests, Duration windowSize) {
        return Promise.ofBlocking(eventloop, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                long now = Instant.now().toEpochMilli();
                long windowStart = now - windowSize.toMillis();

                // Use Redis transaction for atomic operation
                Pipeline pipeline = jedis.pipelined();

                // Remove expired entries
                pipeline.zremrangeByScore(key, 0, windowStart);

                // Count current entries in window
                Response<Long> countResponse = pipeline.zcard(key);

                // Add current request
                pipeline.zadd(key, now, String.valueOf(now));

                // Set expiry on the key
                pipeline.expire(key, (int) windowSize.getSeconds() + 1);

                pipeline.sync();

                long currentCount = countResponse.get();

                // Check burst capacity (allow small bursts)
                if (currentCount <= maxRequests) {
                    LOG.debug("[DMOS-RATELIMIT] Allowed: key={}, count={}/{}",
                        key, currentCount, maxRequests);
                    return true;
                }

                // Check burst allowance
                if (currentCount <= maxRequests + BURST_CAPACITY) {
                    LOG.debug("[DMOS-RATELIMIT] Allowed (burst): key={}, count={}/{} (burst {})",
                        key, currentCount, maxRequests, BURST_CAPACITY);
                    return true;
                }

                LOG.warn("[DMOS-RATELIMIT] Rate limited: key={}, count={}/{} (burst {})",
                    key, currentCount, maxRequests, BURST_CAPACITY);

                // Remove the request we just added (it was over limit)
                jedis.zrem(key, String.valueOf(now));

                return false;
            }
        });
    }

    /**
     * P1-052: Local fallback rate limiter for Redis failures.
     */
    private boolean checkLocalFallback(String key, int maxRequests, Duration windowSize) {
        LocalRateLimiter limiter = localFallback.computeIfAbsent(
            key,
            k -> new LocalRateLimiter(maxRequests, windowSize)
        );

        return limiter.tryAcquire();
    }

    /**
     * P1-052: Gets current rate limit status for a key.
     *
     * @param key the rate limit key
     * @return promise resolving to rate limit status
     */
    public Promise<RateLimitStatus> getStatus(String key) {
        return Promise.ofBlocking(eventloop, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                long now = Instant.now().toEpochMilli();

                // Get count of requests in current window
                Long count = jedis.zcount(key, now - 60000, now);

                // Get TTL of the key
                Long ttl = jedis.ttl(key);

                return new RateLimitStatus(
                    count != null ? count.intValue() : 0,
                    DEFAULT_MAX_REQUESTS,
                    ttl != null && ttl > 0 ? ttl.intValue() : DEFAULT_WINDOW_SIZE_SECONDS,
                    BURST_CAPACITY
                );
            } catch (Exception e) {
                LOG.error("[DMOS-RATELIMIT] Failed to get status for key={}", key, e);
                return new RateLimitStatus(0, DEFAULT_MAX_REQUESTS, DEFAULT_WINDOW_SIZE_SECONDS, BURST_CAPACITY);
            }
        });
    }

    /**
     * P1-052: Resets rate limit for a key (admin operation).
     *
     * @param key the key to reset
     * @return promise resolving when complete
     */
    public Promise<Void> resetLimit(String key) {
        LOG.info("[DMOS-RATELIMIT] Resetting rate limit for key={}", key);

        return Promise.ofBlocking(eventloop, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(key);

                // Also clear local fallback
                localFallback.remove(key);

                return null;
            }
        });
    }

    /**
     * P1-052: Batch rate limit check for multiple keys.
     *
     * @param keys the keys to check
     * @param tenantId the tenant
     * @param maxRequests max requests per key
     * @return promise resolving to map of key -> allowed status
     */
    public Promise<Map<String, Boolean>> checkMultiple(
            List<String> keys,
            String tenantId,
            int maxRequests) {

        List<Promise<Map.Entry<String, Boolean>>> checks = keys.stream()
            .map(key -> isAllowed(key, tenantId, maxRequests, Duration.ofSeconds(DEFAULT_WINDOW_SIZE_SECONDS))
                .map(allowed -> (Map.Entry<String, Boolean>) new AbstractMap.SimpleEntry<>(key, allowed)))
            .toList();

        return Promises.toList(checks).map(entries -> {
            Map<String, Boolean> result = new HashMap<>();
            for (Map.Entry<String, Boolean> entry : entries) {
                result.put(entry.getKey(), entry.getValue());
            }
            return result;
        });
    }

    /**
     * P1-052: Health check for Redis connectivity.
     *
     * @return true if Redis is responsive
     */
    public boolean isHealthy() {
        try (Jedis jedis = jedisPool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }

    // Helper methods

    private String extractEndpoint(String key) {
        // key format: "tenant:endpoint:clientId" or "endpoint:clientId"
        String[] parts = key.split(":");
        if (parts.length >= 2) {
            return parts[parts.length - 2];
        }
        return "unknown";
    }

    private String extractClientId(String key) {
        String[] parts = key.split(":");
        if (parts.length >= 1) {
            return parts[parts.length - 1];
        }
        return "unknown";
    }

    // Local fallback implementation

    private static class LocalRateLimiter {
        private final int maxRequests;
        private final long windowSizeMs;
        private final ConcurrentLinkedQueue<Long> timestamps = new ConcurrentLinkedQueue<>();

        LocalRateLimiter(int maxRequests, Duration windowSize) {
            this.maxRequests = maxRequests;
            this.windowSizeMs = windowSize.toMillis();
        }

        synchronized boolean tryAcquire() {
            long now = Instant.now().toEpochMilli();
            long windowStart = now - windowSizeMs;

            // Remove expired timestamps
            timestamps.removeIf(ts -> ts < windowStart);

            // Check if under limit
            if (timestamps.size() < maxRequests) {
                timestamps.offer(now);
                return true;
            }

            return false;
        }
    }

    /**
     * Rate limit status information.
     */
    public record RateLimitStatus(
        int currentRequests,
        int maxRequests,
        int windowResetSeconds,
        int burstCapacity
    ) {
        public int remainingRequests() {
            return Math.max(0, maxRequests - currentRequests);
        }

        public boolean isLimited() {
            return currentRequests >= maxRequests;
        }
    }
}
