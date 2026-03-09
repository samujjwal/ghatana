package com.ghatana.security.ratelimit;

/**
 * Rate limiter abstraction for controlling request throughput.
 *
 * @doc.type interface
 * @doc.purpose Rate limiter contract
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface RateLimiter {

    /**
     * Tries to acquire a permit for the given key.
     *
     * @param key the rate-limit key (e.g. client IP, user ID)
     * @return result indicating whether the request is allowed
     */
    AcquireResult tryAcquire(String key);

    /**
     * Returns cumulative statistics for this rate limiter.
     */
    Stats getStats();

    /**
     * Result of a rate-limit acquisition attempt.
     *
     * @param allowed           whether the request is permitted
     * @param remainingTokens   tokens remaining in the current window
     * @param retryAfterSeconds seconds the caller should wait before retrying (0 if allowed)
     * @param resetAtEpochSeconds epoch-second when the window resets
     */
    record AcquireResult(boolean allowed, int remainingTokens, long retryAfterSeconds, long resetAtEpochSeconds) {}

    /**
     * Cumulative rate-limiter statistics.
     */
    interface Stats {
        long getTotalAllowed();
        long getTotalRejected();
    }
}
