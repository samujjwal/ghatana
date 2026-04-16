package com.ghatana.yappc.infrastructure.security;

import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.observability.NoopMetricsCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AuthRateLimiterConfig.
 *
 * @doc.type class
 * @doc.purpose Unit tests for auth rate limiter configurations
 * @doc.layer infrastructure
 * @doc.pattern Test
 */
@DisplayName("AuthRateLimiterConfig — Auth endpoint rate limiter configurations")
class AuthRateLimiterConfigTest {

    @Test
    @DisplayName("loginLimiter returns non-null rate limiter")
    void loginLimiterReturnsNonNull() {
        RateLimiter limiter = AuthRateLimiterConfig.loginLimiter(new NoopMetricsCollector());
        assertThat(limiter).isNotNull();
    }

    @Test
    @DisplayName("loginLimiter enforces 10 req/min limit")
    void loginLimiterEnforcesLimit() {
        RateLimiter limiter = AuthRateLimiterConfig.loginLimiter(new NoopMetricsCollector());
        
        // First 5 requests should be allowed
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("test@example.com").allowed()).isTrue();
        }
        
        // 6th request should be rejected (burst size is 5)
        assertThat(limiter.tryAcquire("test@example.com").allowed()).isFalse();
    }

    @Test
    @DisplayName("tokenRefreshLimiter returns non-null rate limiter")
    void tokenRefreshLimiterReturnsNonNull() {
        RateLimiter limiter = AuthRateLimiterConfig.tokenRefreshLimiter(new NoopMetricsCollector());
        assertThat(limiter).isNotNull();
    }

    @Test
    @DisplayName("passwordResetLimiter returns non-null rate limiter")
    void passwordResetLimiterReturnsNonNull() {
        RateLimiter limiter = AuthRateLimiterConfig.passwordResetLimiter(new NoopMetricsCollector());
        assertThat(limiter).isNotNull();
    }

    @Test
    @DisplayName("passwordResetLimiter enforces 5 req/min limit")
    void passwordResetLimiterEnforcesLimit() {
        RateLimiter limiter = AuthRateLimiterConfig.passwordResetLimiter(new NoopMetricsCollector());
        
        // First 2 requests should be allowed (burst size is 2)
        for (int i = 0; i < 2; i++) {
            assertThat(limiter.tryAcquire("test@example.com").allowed()).isTrue();
        }
        
        // 3rd request should be rejected
        assertThat(limiter.tryAcquire("test@example.com").allowed()).isFalse();
    }

    @Test
    @DisplayName("generalAuthLimiter returns non-null rate limiter")
    void generalAuthLimiterReturnsNonNull() {
        RateLimiter limiter = AuthRateLimiterConfig.generalAuthLimiter(new NoopMetricsCollector());
        assertThat(limiter).isNotNull();
    }

    @Test
    @DisplayName("different users have independent rate limits")
    void differentUsersHaveIndependentLimits() {
        RateLimiter limiter = AuthRateLimiterConfig.loginLimiter(new NoopMetricsCollector());
        
        // User 1 can make 5 requests
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("user1@example.com").allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire("user1@example.com").allowed()).isFalse();
        
        // User 2 can also make 5 requests
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("user2@example.com").allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire("user2@example.com").allowed()).isFalse();
    }

    @Test
    @DisplayName("rate limiter returns retry-after on rejection")
    void rateLimiterReturnsRetryAfter() {
        RateLimiter limiter = AuthRateLimiterConfig.passwordResetLimiter(new NoopMetricsCollector());
        
        // Exhaust burst
        for (int i = 0; i < 2; i++) {
            limiter.tryAcquire("test@example.com");
        }
        
        // Next request should be rejected with retry-after
        RateLimiter.AcquireResult result = limiter.tryAcquire("test@example.com");
        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isGreaterThan(0);
    }
}
