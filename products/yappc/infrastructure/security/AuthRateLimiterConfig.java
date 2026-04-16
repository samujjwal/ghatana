package com.ghatana.yappc.infrastructure.security;

import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;

import java.time.Duration;

/**
 * Rate limiter configurations for YAPPC authentication endpoints.
 *
 * <p><b>Purpose</b><br>
 * Provides pre-configured rate limiters for different auth endpoint types:
 * - Login: 10 req/min (strict to prevent brute force)
 * - Token refresh: 100 req/min (moderate for legitimate use)
 * - Password reset: 5 req/min (very strict)
 * - Other auth endpoints: 100 req/min
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RateLimiter loginLimiter = AuthRateLimiterConfig.loginLimiter(metrics);
 * RateLimiter.AcquireResult result = loginLimiter.tryAcquire("user@example.com");
 * if (!result.allowed()) {
 *     // Return 429 with Retry-After header
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Auth-specific rate limiter configurations
 * @doc.layer infrastructure
 * @doc.pattern Configuration
 */
public final class AuthRateLimiterConfig {

    private AuthRateLimiterConfig() {
        // Utility class
    }

    /**
     * Create rate limiter for login endpoint (10 req/min).
     * Strict limit to prevent brute force attacks.
     */
    public static RateLimiter loginLimiter(com.ghatana.platform.observability.MetricsCollector metrics) {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .maxRequestsPerMinute(10)
                .burstSize(5)
                .windowDuration(Duration.ofMinutes(1))
                .build();
        return DefaultRateLimiter.create(config, metrics, "auth.login");
    }

    /**
     * Create rate limiter for token refresh endpoint (100 req/min).
     * Moderate limit for legitimate token refresh operations.
     */
    public static RateLimiter tokenRefreshLimiter(com.ghatana.platform.observability.MetricsCollector metrics) {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .maxRequestsPerMinute(100)
                .burstSize(20)
                .windowDuration(Duration.ofMinutes(1))
                .build();
        return DefaultRateLimiter.create(config, metrics, "auth.token_refresh");
    }

    /**
     * Create rate limiter for password reset endpoint (5 req/min).
     * Very strict limit to prevent abuse.
     */
    public static RateLimiter passwordResetLimiter(com.ghatana.platform.observability.MetricsCollector metrics) {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .maxRequestsPerMinute(5)
                .burstSize(2)
                .windowDuration(Duration.ofMinutes(1))
                .build();
        return DefaultRateLimiter.create(config, metrics, "auth.password_reset");
    }

    /**
     * Create rate limiter for general auth endpoints (100 req/min).
     * Standard limit for API key validation and other auth operations.
     */
    public static RateLimiter generalAuthLimiter(com.ghatana.platform.observability.MetricsCollector metrics) {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .maxRequestsPerMinute(100)
                .burstSize(20)
                .windowDuration(Duration.ofMinutes(1))
                .build();
        return DefaultRateLimiter.create(config, metrics, "auth.general");
    }
}
