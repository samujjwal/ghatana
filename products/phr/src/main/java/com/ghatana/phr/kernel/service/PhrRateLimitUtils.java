package com.ghatana.phr.kernel.service;

import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import java.time.Duration;

/**
 * @doc.type class
 * @doc.purpose Shared rate-limiter helpers for PHR service boundaries
 * @doc.layer product
 * @doc.pattern Utils
 */
public final class PhrRateLimitUtils {

    private PhrRateLimitUtils() {
    }

    public static RateLimiter createLimiter(int maxRequestsPerWindow, Duration windowDuration) {
        return DefaultRateLimiter.create(
            RateLimiterConfig.builder()
                .maxRequestsPerMinute(maxRequestsPerWindow)
                .burstSize(maxRequestsPerWindow)
                .windowDuration(windowDuration)
                .build()
        );
    }

    public static void requireAllowed(RateLimiter limiter, String key, String message) {
        if (!limiter.tryAcquire(key).allowed()) {
            throw new IllegalStateException(message);
        }
    }
}