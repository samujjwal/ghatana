package com.ghatana.security.ratelimit;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Rate limiter for RPC endpoints using the security module's EventloopRateLimiter.
 
 *
 * @doc.type class
 * @doc.purpose Rpc rate limiter
 * @doc.layer core
 * @doc.pattern Component
*/
public class RpcRateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(RpcRateLimiter.class);
    
    private final EventloopRateLimiter rateLimiter;
    private final long maxRequestsPerMinute;
    private final long blockDurationMinutes;
    private final boolean enabled;

    /**
     * Creates a new RpcRateLimiter with default settings.
     */
    public RpcRateLimiter(Eventloop eventloop) {
        this(100, 5, true, eventloop);
    }

    /**
     * Creates a new RpcRateLimiter.
     *
     * @param maxRequestsPerMinute Maximum number of requests allowed per minute per client
     * @param blockDurationMinutes Number of minutes to block a client after exceeding the rate limit
     * @param enabled Whether rate limiting is enabled
     * @param eventloop Eventloop instance
     */
    public RpcRateLimiter(
            long maxRequestsPerMinute,
            long blockDurationMinutes,
            boolean enabled,
            Eventloop eventloop) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.blockDurationMinutes = blockDurationMinutes;
        this.enabled = enabled;
        this.rateLimiter = new EventloopRateLimiter(
            (int) maxRequestsPerMinute,
            Duration.ofMinutes(1),
            eventloop
        );
    }

    /**
     * Checks if a request from the given client should be allowed.
     *
     * @param clientId The client identifier (e.g., IP address or API key)
     * @return true if the request should be allowed, false if rate limited
     */
    public Promise<Boolean> allowRequest(String clientId) {
        if (!enabled) {
            return Promise.of(true);
        }
        return rateLimiter.allow(clientId);
    }
    
    /**
     * Gets the number of remaining requests for a client in the current time window.
     *
     * @param clientId The client identifier
     * @return Number of remaining requests, or -1 if rate limiting is disabled
     */
    public int getRemainingRequests(String clientId) {
        if (!enabled) {
            return -1;
        }
        // TODO: implement getRemainingRequests using EventloopRateLimiter
        return (int) maxRequestsPerMinute;
    }
    
    /**
     * Gets the time remaining until the rate limit resets for a client.
     *
     * @param clientId The client identifier
     * @return Time remaining in milliseconds, or 0 if not rate limited
     */
    public long getTimeUntilReset(String clientId) {
        if (!enabled) {
            return 0;
        }
        // TODO: implement getTimeUntilReset using EventloopRateLimiter
        return 60000 - (System.currentTimeMillis() % 60000);
    }
    
    /**
     * Gets the current rate limit configuration.
     *
     * @return A map containing the rate limit configuration
     */
    public java.util.concurrent.ConcurrentMap<String, Object> getRateLimitInfo() {
        return new java.util.concurrent.ConcurrentHashMap<>() {{
            put("enabled", enabled);
            put("maxRequestsPerMinute", maxRequestsPerMinute);
            put("blockDurationMinutes", blockDurationMinutes);
        }};
    }
}
