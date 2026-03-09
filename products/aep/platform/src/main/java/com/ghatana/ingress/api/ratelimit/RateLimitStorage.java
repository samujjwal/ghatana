package com.ghatana.ingress.api.ratelimit;

import java.time.Instant;
import java.util.Map;

/**
 * Storage backend for distributed rate limiting. Implementations may use Redis or other stores.
 * Keys should be stable identifiers, e.g., "tenant:{tenantId}:events".
 
 *
 * @doc.type interface
 * @doc.purpose Rate limit storage
 * @doc.layer core
 * @doc.pattern Interface
*/
public interface RateLimitStorage {
    /**
     * Attempts to consume the given number of tokens at the provided instant.
     * Returns true if allowed, false if the rate limit has been exceeded.
     */
    boolean tryConsume(String key, int tokens, Instant now);

    /**
     * Returns standard rate-limit headers such as X-RateLimit-Limit, X-RateLimit-Remaining,
     * X-RateLimit-Reset, and Retry-After (when relevant).
     */
    Map<String, String> headers(String key, Instant now);
}
