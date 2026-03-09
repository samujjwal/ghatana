package com.ghatana.ingress.app;

import com.ghatana.contracts.event.v1.IngestResponseProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for handling idempotent event ingestion using Redis as a backend.
 * Keys are "idempotency:{tenantId}:{key}" and store serialized IngestResponse.
 * 
 * <p>This class is thread-safe and properly manages Redis connections.</p>
 
 *
 * @doc.type class
 * @doc.purpose Idempotency service
 * @doc.layer core
 * @doc.pattern Service
*/
public class IdempotencyService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String DEFAULT_KEY_PREFIX = "idempotency:";
    
    private final JedisPool jedisPool; // Defensive copy is not possible with JedisPool, so we'll ensure proper cleanup in close()
    private final String keyPrefix;
    private final long ttlSeconds;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a new IdempotencyService with the specified parameters.
     * 
     * @param jedisPool The JedisPool to use for Redis connections (will be closed when this service is closed)
     * @param keyPrefix The prefix to use for all Redis keys (if null, defaults to "idempotency:")
     * @param ttl The time-to-live for idempotency keys (must be positive)
     * @throws IllegalArgumentException if jedisPool is null or ttl is invalid
     */
    @SuppressWarnings("EI_EXPOSE_REP2") // We store a reference to jedisPool but ensure proper cleanup in close()
    public IdempotencyService(JedisPool jedisPool, String keyPrefix, Duration ttl) {
        if (jedisPool == null) {
            throw new IllegalArgumentException("jedisPool cannot be null");
        }
        // Note: We can't make a defensive copy of JedisPool, so we'll ensure proper cleanup in close()
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be a positive duration");
        }
        
        this.jedisPool = jedisPool;
        this.keyPrefix = keyPrefix != null ? keyPrefix : DEFAULT_KEY_PREFIX;
        this.ttlSeconds = ttl.getSeconds();
    }

    /**
     * Checks if an idempotency key has been seen before.
     * 
     * @param tenantId The tenant ID (must not be null or empty)
     * @param key The idempotency key (must not be null or empty)
     * @return Optional containing the previous response if the key was seen, or empty if not
     * @throws ValidationException if tenantId or key are invalid
     * @throws IllegalStateException if the service has been closed
     */
    public Optional<IngestResponseProto> seen(String tenantId, String key) {
        validateNotClosed();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("tenantId cannot be null or empty");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key cannot be null or empty");
        }
        
        String redisKey = buildRedisKey(tenantId, key);
        
        try (Jedis jedis = jedisPool.getResource()) {
            byte[] bytes = jedis.get(redisKey.getBytes());

            if (bytes == null || bytes.length == 0) {
                return Optional.empty();
            }

            try {
                return Optional.of(IngestResponseProto.parseFrom(bytes));
            } catch (Exception e) {
                log.warn("Failed to deserialize idempotency response for key: {}", redisKey, e);
                return Optional.empty();
            }
        } catch (JedisException e) {
            log.error("Redis error while checking idempotency key: {}", redisKey, e);
            return Optional.empty();
        }
    }

    /**
     * Stores an idempotency key and its response for future reference.
     * 
     * @param tenantId The tenant ID (must not be null or empty)
     * @param key The idempotency key (must not be null or empty)
     * @param response The response to store (must not be null)
     * @throws ValidationException if any parameter is invalid
     * @throws IllegalStateException if the service has been closed
     */
    public void remember(String tenantId, String key, IngestResponseProto response) {
        validateNotClosed();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("tenantId cannot be null or empty");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key cannot be null or empty");
        }
        Objects.requireNonNull(response, "response cannot be null");
        
        String redisKey = buildRedisKey(tenantId, key);
        
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(
                redisKey.getBytes(), 
                (int) ttlSeconds, 
                response.toByteArray()
            );
            log.debug("Stored idempotency key: {}", redisKey);
        } catch (JedisException e) {
            log.error("Failed to store idempotency key: {}", redisKey, e);
            // Don't fail the request on Redis errors - this is a best-effort operation
        }
    }
    
    /**
     * Builds the Redis key for the given tenant and idempotency key.
     */
    private String buildRedisKey(String tenantId, String key) {
        return keyPrefix + tenantId + ":" + key;
    }
    
    /**
     * Validates that the service has not been closed.
     * 
     * @throws IllegalStateException if the service has been closed
     */
    private void validateNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("IdempotencyService has been closed");
        }
    }
    
    /**
     * Closes the underlying Redis connection pool.
     * After calling this method, this instance can no longer be used.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                jedisPool.destroy();
            } catch (Exception e) {
                log.warn("Error closing JedisPool", e);
            }
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}
