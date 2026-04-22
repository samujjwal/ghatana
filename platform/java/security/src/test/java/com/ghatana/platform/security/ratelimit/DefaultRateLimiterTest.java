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
@DisplayName("DefaultRateLimiter Tests [GH-90000]")
class DefaultRateLimiterTest {

    @Test
    @DisplayName("should allow requests until burst is exhausted [GH-90000]")
    void shouldAllowRequestsUntilBurstExhausted() { // GH-90000
        DefaultRateLimiter limiter = DefaultRateLimiter.create( // GH-90000
                RateLimiterConfig.builder() // GH-90000
                        .maxRequestsPerMinute(2) // GH-90000
                        .burstSize(2) // GH-90000
                        .windowDuration(Duration.ofMinutes(1)) // GH-90000
                        .build() // GH-90000
        );

        assertThat(limiter.tryAcquire("tenant-a [GH-90000]").allowed()).isTrue();
        assertThat(limiter.tryAcquire("tenant-a [GH-90000]").allowed()).isTrue();
        assertThat(limiter.tryAcquire("tenant-a [GH-90000]").allowed()).isFalse();
        assertThat(limiter.getStats().getTotalAllowed()).isEqualTo(2); // GH-90000
        assertThat(limiter.getStats().getTotalRejected()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("should track keys independently [GH-90000]")
    void shouldTrackKeysIndependently() { // GH-90000
        DefaultRateLimiter limiter = DefaultRateLimiter.create( // GH-90000
                RateLimiterConfig.builder().maxRequestsPerMinute(1).burstSize(1).build() // GH-90000
        );

        assertThat(limiter.tryAcquire("tenant-a [GH-90000]").allowed()).isTrue();
        assertThat(limiter.tryAcquire("tenant-a [GH-90000]").allowed()).isFalse();
        assertThat(limiter.tryAcquire("tenant-b [GH-90000]").allowed()).isTrue();
    }

    @Test
    @DisplayName("should support reset operations for testing [GH-90000]")
    void shouldSupportResetOperationsForTesting() { // GH-90000
        DefaultRateLimiter limiter = DefaultRateLimiter.create( // GH-90000
                RateLimiterConfig.builder().maxRequestsPerMinute(1).burstSize(1).build() // GH-90000
        );

        assertThat(limiter.tryAcquire("tenant-a [GH-90000]").allowed()).isTrue();
        assertThat(limiter.tryAcquire("tenant-a [GH-90000]").allowed()).isFalse();
        limiter.reset("tenant-a [GH-90000]");
        assertThat(limiter.tryAcquire("tenant-a [GH-90000]").allowed()).isTrue();

        limiter.tryAcquire("tenant-b [GH-90000]");
        limiter.resetAll(); // GH-90000
        assertThat(limiter.getTrackedKeyCount()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("should emit retry metadata when rejected [GH-90000]")
    void shouldEmitRetryMetadataWhenRejected() { // GH-90000
        DefaultRateLimiter limiter = DefaultRateLimiter.create( // GH-90000
                RateLimiterConfig.builder().maxRequestsPerMinute(1).burstSize(1).build(), // GH-90000
                new NoopMetricsCollector(), // GH-90000
                "security.rate.limit"
        );

        limiter.tryAcquire("tenant-a [GH-90000]");
        RateLimiter.AcquireResult result = limiter.tryAcquire("tenant-a [GH-90000]");

        assertThat(result.allowed()).isFalse(); // GH-90000
        assertThat(result.retryAfterSeconds()).isGreaterThanOrEqualTo(1L); // GH-90000
        assertThat(result.resetAtEpochSeconds()).isGreaterThan(0L); // GH-90000
    }

    @Test
    @DisplayName("should validate config input [GH-90000]")
    void shouldValidateConfigInput() { // GH-90000
        assertThatThrownBy(() -> RateLimiterConfig.builder().maxRequestsPerMinute(0).build()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        assertThatThrownBy(() -> RateLimiterConfig.builder().burstSize(0).build()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        assertThatThrownBy(() -> RateLimiterConfig.builder().windowDuration(Duration.ZERO).build()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        assertThatThrownBy(() -> DefaultRateLimiter.create( // GH-90000
                RateLimiterConfig.builder().build(), null, "security.rate.limit")) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
