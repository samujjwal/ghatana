package com.ghatana.platform.security.ratelimit;

import com.ghatana.platform.observability.NoopMetricsCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Tests for platform security rate limiter contract and default implementation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DefaultRateLimiter Tests")
class DefaultRateLimiterTest {

    @Test
    @DisplayName("should allow requests until burst is exhausted")
    void shouldAllowRequestsUntilBurstExhausted() {
        DefaultRateLimiter limiter = DefaultRateLimiter.create(
                RateLimiterConfig.builder()
                        .maxRequestsPerMinute(2)
                        .burstSize(2)
                        .windowDuration(Duration.ofMinutes(1))
                        .build()
        );

        assertThat(limiter.tryAcquire("tenant-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("tenant-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("tenant-a").allowed()).isFalse();
        assertThat(limiter.getStats().getTotalAllowed()).isEqualTo(2);
        assertThat(limiter.getStats().getTotalRejected()).isEqualTo(1);
    }

    @Test
    @DisplayName("should track keys independently")
    void shouldTrackKeysIndependently() {
        DefaultRateLimiter limiter = DefaultRateLimiter.create(
                RateLimiterConfig.builder().maxRequestsPerMinute(1).burstSize(1).build()
        );

        assertThat(limiter.tryAcquire("tenant-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("tenant-a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("tenant-b").allowed()).isTrue();
    }

    @Test
    @DisplayName("should support reset operations for testing")
    void shouldSupportResetOperationsForTesting() {
        DefaultRateLimiter limiter = DefaultRateLimiter.create(
                RateLimiterConfig.builder().maxRequestsPerMinute(1).burstSize(1).build()
        );

        assertThat(limiter.tryAcquire("tenant-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("tenant-a").allowed()).isFalse();
        limiter.reset("tenant-a");
        assertThat(limiter.tryAcquire("tenant-a").allowed()).isTrue();

        limiter.tryAcquire("tenant-b");
        limiter.resetAll();
        assertThat(limiter.getTrackedKeyCount()).isZero();
    }

    @Test
    @DisplayName("should emit retry metadata when rejected")
    void shouldEmitRetryMetadataWhenRejected() {
        DefaultRateLimiter limiter = DefaultRateLimiter.create(
                RateLimiterConfig.builder().maxRequestsPerMinute(1).burstSize(1).build(),
                new NoopMetricsCollector(),
                "security.rate.limit"
        );

        limiter.tryAcquire("tenant-a");
        RateLimiter.AcquireResult result = limiter.tryAcquire("tenant-a");

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isGreaterThanOrEqualTo(1L);
        assertThat(result.resetAtEpochSeconds()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("should validate config input")
    void shouldValidateConfigInput() {
        assertThatThrownBy(() -> RateLimiterConfig.builder().maxRequestsPerMinute(0).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RateLimiterConfig.builder().burstSize(0).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RateLimiterConfig.builder().windowDuration(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefaultRateLimiter.create(
                RateLimiterConfig.builder().build(), null, "security.rate.limit"))
                .isInstanceOf(NullPointerException.class);
    }
}