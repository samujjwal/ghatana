package com.ghatana.platform.observability.health;

import com.ghatana.platform.observability.util.PromisesCompat;
import io.activej.promise.Promise;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Redis health check implementation.
 * Tests Redis connectivity using PING command and set/get/delete test.
 *
 * <p>Performs lightweight Redis operations to verify:
 * <ul>
 *   <li>Connection pool can acquire connections</li>
 *   <li>Redis server responds to PING</li>
 *   <li>Basic read/write operations work (set, get, delete)</li>
 *   <li>Connection pool statistics are healthy</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * JedisPool jedisPool = new JedisPool("localhost", 6379);
 * RedisHealthCheck check = new RedisHealthCheck(jedisPool);
 *
 * check.check()
 *     .whenResult(result -> {
 *         if (result.isHealthy()) {
 *             Map<String, Object> details = result.getDetails();
 *             logger.info("Redis pool: {} active, {} idle",
 *                 details.get("poolActive"), details.get("poolIdle"));
 *         } else {
 *             logger.error("Redis unhealthy: {}", result.getMessage());
 *         }
 *     });
 *
 * // Register for readiness probe (non-critical)
 * HealthCheckRegistry registry = HealthCheckRegistry.getInstance();
 * registry.register(check); // isCritical=false -> readiness only
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Redis health check implementation for connection pool and read/write verification
 * @doc.layer observability
 * @doc.pattern Health Check, Component Verification
 *
 * <h2>Health Check Operations:</h2>
 * <pre>
 * 1. Acquire connection from pool
 * 2. Execute PING command → "PONG" response expected
 * 3. Execute SET healthcheck:timestamp → OK response expected
 * 4. Execute GET healthcheck:timestamp → Verify value matches
 * 5. Execute DEL healthcheck:timestamp → Cleanup test key
 * 6. Collect pool statistics (active, idle, waiters)
 * 7. Return connection to pool
 * </pre>
 *
 * <h2>Result Details:</h2>
 * Check result includes metadata:
 * - {@code pingResponse}: PING command response ("PONG")
 * - {@code poolActive}: Active connections in pool
 * - {@code poolIdle}: Idle connections in pool
 * - {@code poolWaiters}: Threads waiting for connections
 * - {@code testKeySet}: Test key was set successfully
 * - {@code testKeyDeleted}: Test key was deleted successfully
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe (JedisPool handles connection pooling concurrency).
 *
 * <h2>Performance:</h2>
 * - Typical latency: <10ms (local Redis)
 * - Remote latency: <50ms (network overhead)
 * - Timeout: 5 seconds (default)
 *
 * @since 1.0.0
 */
public class RedisHealthCheck implements HealthCheck {
    
    private final JedisPool jedisPool;
    private final String name;
    private final Duration timeout;
    
    public RedisHealthCheck(JedisPool jedisPool, String name) {
        this(jedisPool, name, Duration.ofSeconds(3));
    }
    
    public RedisHealthCheck(JedisPool jedisPool, String name, Duration timeout) {
        this.jedisPool = jedisPool;
        this.name = name;
        this.timeout = timeout;
    }
    
    @Override
    public Promise<HealthCheckResult> check() {
        return PromisesCompat.runBlocking(() -> {
            Instant start = Instant.now();
            
            try (Jedis jedis = jedisPool.getResource()) {
                // Test basic connectivity with ping
                String pingResult = jedis.ping();
                if (!"PONG".equals(pingResult)) {
                    return HealthCheckResult.unhealthy("Redis ping failed, got: " + pingResult);
                }
                
                // Test basic operations
                String testKey = "health:check:" + System.currentTimeMillis();
                String testValue = "test";
                
                jedis.setex(testKey, 10, testValue); // Set with 10 second expiration
                String retrievedValue = jedis.get(testKey);
                jedis.del(testKey); // Clean up
                
                if (!testValue.equals(retrievedValue)) {
                    return HealthCheckResult.unhealthy("Redis set/get test failed");
                }
                
                Duration duration = Duration.between(start, Instant.now());
                
                Map<String, Object> details = Map.of(
                    "ping", pingResult,
                    "setGetTest", "passed",
                    "poolActive", jedisPool.getNumActive(),
                    "poolIdle", jedisPool.getNumIdle(),
                    "poolWaiters", jedisPool.getNumWaiters()
                );
                
                return HealthCheckResult.healthy("Redis connection successful", details, duration);
                
            } catch (Exception e) {
                Duration duration = Duration.between(start, Instant.now());
                Map<String, Object> details = Map.of(
                    "error", e.getClass().getSimpleName(),
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error",
                    "poolActive", jedisPool.getNumActive(),
                    "poolIdle", jedisPool.getNumIdle()
                );
                
                return HealthCheckResult.unhealthy("Redis health check failed", details, duration, e);
            }
        });
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public Duration getTimeout() {
        return timeout;
    }
    
    @Override
    public boolean isCritical() {
        return false; // Redis is typically not critical for liveness, but important for readiness
    }
}
