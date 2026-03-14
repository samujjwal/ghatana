package com.ghatana.appplatform.gateway.ratelimit;

/**
 * Port for persisting and updating token bucket state for rate limiting.
 *
 * @doc.type interface
 * @doc.purpose Rate limit token bucket state store port (STORY-K11)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface RateLimitStore {

    /**
     * Result of a token consumption attempt.
     *
     * @param allowed       whether the request is allowed
     * @param tokensLeft    remaining tokens after this request
     * @param retryAfterMs  milliseconds until next token is available (0 if allowed)
     */
    record ConsumeResult(boolean allowed, long tokensLeft, long retryAfterMs) {}

    /**
     * Attempt to consume {@code tokens} from the bucket identified by {@code bucketKey}.
     * If insufficient tokens are available, the request is denied.
     *
     * @param bucketKey       Unique bucket identifier (e.g. "tenant:t1:api:v1")
     * @param capacity        Maximum token capacity
     * @param refillRate      Tokens added per second
     * @param tokensRequested Tokens to consume for this request
     * @return result of the consumption attempt
     */
    ConsumeResult tryConsume(String bucketKey, long capacity, double refillRate, long tokensRequested);
}
