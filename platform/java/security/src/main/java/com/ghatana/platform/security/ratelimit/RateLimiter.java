package com.ghatana.platform.security.ratelimit;

/**
 * @doc.type interface
 * @doc.purpose Contract for keyed request rate limiting across platform services
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public interface RateLimiter {

    /**
     * Attempts to acquire a permit for the given key.
     *
     * @param key rate-limit key such as client IP, tenant ID, or user ID
     * @return acquisition result containing allowance and retry metadata
     */
    AcquireResult tryAcquire(String key);

    /**
     * Returns cumulative statistics for this limiter instance.
     *
     * @return cumulative statistics
     */
    Stats getStats();

    /**
     * Immutable result of a rate-limit acquisition attempt.
     *
     * @param allowed whether the request is permitted
     * @param remainingTokens remaining tokens in the current bucket/window
     * @param retryAfterSeconds seconds the caller should wait before retrying, or {@code 0} when allowed
     * @param resetAtEpochSeconds epoch-second when the current bucket/window fully resets
     */
    record AcquireResult(boolean allowed, int remainingTokens, long retryAfterSeconds, long resetAtEpochSeconds) {
    }

    /**
     * Cumulative rate-limiter statistics.
     */
    interface Stats {
        long getTotalAllowed();

        long getTotalRejected();
    }
}