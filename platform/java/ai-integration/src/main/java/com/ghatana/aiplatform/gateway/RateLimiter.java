package com.ghatana.aiplatform.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.activej.promise.Promise;

/**
 * Token-bucket rate limiter for controlling the rate of outbound LLM API calls.
 *
 * <p>This class manages per-tenant token buckets to limit the number of
 * requests sent to downstream LLM providers. It is distinct from the inbound
 * request rate limiter in {@code platform:java:security} which governs incoming
 * HTTP traffic at the service boundary.
 *
 * <p>Buckets are lazily created on first use for a given tenant key. The
 * default capacity and refill rate can be overridden on a per-tenant basis via
 * {@link #setTenantLimit(String, int, double)}.
 *
 * @doc.type class
 * @doc.purpose Token-bucket rate limiter for outbound LLM gateway calls
 * @doc.layer platform
 * @doc.pattern Rate Limiter
 */
public class RateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);

    private static final int DEFAULT_CAPACITY = 1000;
    private static final double DEFAULT_REFILL_RATE = 10.0;

    private final Map<String, TokenBucket> tenantBuckets = new ConcurrentHashMap<>();
    private final int defaultCapacity;
    private final double defaultRefillRate;

    /**
     * Creates a rate limiter with default capacity and refill rate.
     */
    public RateLimiter() {
        this(DEFAULT_CAPACITY, DEFAULT_REFILL_RATE);
    }

    /**
     * Creates a rate limiter with the specified default capacity and refill rate.
     *
     * @param defaultCapacity  maximum burst capacity in tokens
     * @param defaultRefillRate tokens added per second
     */
    public RateLimiter(int defaultCapacity, double defaultRefillRate) {
        this.defaultCapacity = defaultCapacity;
        this.defaultRefillRate = defaultRefillRate;
        logger.debug("Gateway RateLimiter initialized: capacity={}, refillRate={}/s",
                defaultCapacity, defaultRefillRate);
    }

    /**
     * Overrides the token-bucket settings for a specific tenant.
     *
     * @param tenantId   tenant identifier
     * @param capacity   maximum burst capacity in tokens
     * @param refillRate tokens added per second
     */
    public void setTenantLimit(String tenantId, int capacity, double refillRate) {
        tenantBuckets.put(tenantId, new TokenBucket(capacity, refillRate));
        logger.debug("Set tenant-specific limit for {}: capacity={}, refillRate={}/s",
                tenantId, capacity, refillRate);
    }

    /**
     * Checks whether the given tenant is within its rate limit.
     *
     * @param tenantId  tenant identifier
     * @param operation operation label for logging
     * @return {@code Promise<true>} when allowed, {@code Promise<false>} when throttled
     */
    public Promise<Boolean> checkLimit(String tenantId, String operation) {
        TokenBucket bucket = tenantBuckets.computeIfAbsent(tenantId,
                id -> new TokenBucket(defaultCapacity, defaultRefillRate));
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            logger.debug("Rate limit exceeded for tenant={} operation={}", tenantId, operation);
        }
        return Promise.of(allowed);
    }

    /**
     * Returns the approximate number of available tokens for the given tenant.
     *
     * @param tenantId tenant identifier
     * @return current available token count, or {@code defaultCapacity} if the tenant has no bucket
     */
    public int getAvailableTokens(String tenantId) {
        TokenBucket bucket = tenantBuckets.get(tenantId);
        return bucket != null ? bucket.getAvailableTokens() : defaultCapacity;
    }

    // ------------------------------------------------------------------ //
    //  Inner: token bucket                                                 //
    // ------------------------------------------------------------------ //

    /**
     * Thread-safe token-bucket implementation.
     */
    static final class TokenBucket {

        private final int capacity;
        private final double refillRate;
        private final AtomicInteger tokens;
        private volatile long lastRefillTime;

        TokenBucket(int capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillTime = System.nanoTime();
        }

        boolean tryConsume(int requested) {
            refill();
            int current;
            do {
                current = tokens.get();
                if (current < requested) {
                    return false;
                }
            } while (!tokens.compareAndSet(current, current - requested));
            return true;
        }

        private void refill() {
            long now = System.nanoTime();
            long last = lastRefillTime;
            double elapsed = (now - last) / 1_000_000_000.0;
            int toAdd = (int) (elapsed * refillRate);
            if (toAdd > 0 && tryUpdateRefillTime(last, now)) {
                int current;
                do {
                    current = tokens.get();
                } while (!tokens.compareAndSet(current, Math.min(capacity, current + toAdd)));
            }
        }

        private boolean tryUpdateRefillTime(long expected, long updated) {
            // volatile compare-and-set via synchronized block for Java 21 without VarHandle
            synchronized (this) {
                if (lastRefillTime == expected) {
                    lastRefillTime = updated;
                    return true;
                }
                return false;
            }
        }

        int getAvailableTokens() {
            refill();
            return tokens.get();
        }
    }
}
