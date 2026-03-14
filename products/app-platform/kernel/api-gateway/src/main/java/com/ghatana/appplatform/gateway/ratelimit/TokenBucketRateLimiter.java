package com.ghatana.appplatform.gateway.ratelimit;

/**
 * Token bucket rate limiter. Delegates bucket state to a {@link RateLimitStore}
 * for distributed enforcement across gateway instances.
 *
 * <p>Used by {@link RateLimitFilter} to limit requests per tenant, per route, or
 * per authenticated principal.
 *
 * @doc.type class
 * @doc.purpose Token bucket rate limiter facade (STORY-K11)
 * @doc.layer product
 * @doc.pattern Service
 */
public class TokenBucketRateLimiter {

    private final RateLimitStore store;
    private final long defaultCapacity;
    private final double defaultRefillRate; // tokens per second

    public TokenBucketRateLimiter(RateLimitStore store, long defaultCapacity, double defaultRefillRate) {
        this.store = store;
        this.defaultCapacity = defaultCapacity;
        this.defaultRefillRate = defaultRefillRate;
    }

    /**
     * Attempt to consume one token for a request on the given bucket.
     *
     * @param bucketKey Unique bucket identifier (e.g. "tenant:t1" or "tenant:t1:route:/payments")
     * @return consume result with allow/deny decision and retry-after hint
     */
    public RateLimitStore.ConsumeResult tryConsume(String bucketKey) {
        return store.tryConsume(bucketKey, defaultCapacity, defaultRefillRate, 1);
    }

    /**
     * Attempt to consume {@code tokens} tokens with custom limits.
     *
     * @param bucketKey    Unique bucket identifier
     * @param capacity     Maximum bucket size
     * @param refillRate   Tokens per second refill rate
     * @param tokens       Number of tokens to consume
     */
    public RateLimitStore.ConsumeResult tryConsume(String bucketKey, long capacity,
                                                    double refillRate, long tokens) {
        return store.tryConsume(bucketKey, capacity, refillRate, tokens);
    }

    /** Returns true if the request is within rate limits. */
    public boolean isAllowed(String bucketKey) {
        return tryConsume(bucketKey).allowed();
    }
}
