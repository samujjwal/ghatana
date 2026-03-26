package com.ghatana.services.auth;

import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import com.ghatana.platform.observability.NoopMetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for auth-gateway RateLimiter.
 *
 * @doc.type    class
 * @doc.purpose Tests for per-tenant rate limiting in auth gateway
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("RateLimiter Tests")
class RateLimiterTest {

    private DefaultRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = DefaultRateLimiter.create(
                RateLimiterConfig.builder()
                        .maxRequestsPerMinute(5)
                        .burstSize(5)
                        .windowDuration(Duration.ofMinutes(1))
                        .build(),
                new NoopMetricsCollector(),
                "auth.gateway.rate_limit"
        );
    }

    // ─── Basic allow / reject ─────────────────────────────────────────────────

    @Test
    @DisplayName("Requests within limit should be allowed")
    void requestsWithinLimitShouldBeAllowed() {
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryAcquire("tenant-1").allowed()).isTrue();
        }
    }

    @Test
    @DisplayName("Request exceeding limit should be rejected")
    void requestExceedingLimitShouldBeRejected() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("tenant-1");
        }
        assertThat(rateLimiter.tryAcquire("tenant-1").allowed()).isFalse();
    }

    // ─── Tenant isolation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Rate limit should be per-tenant")
    void rateLimitShouldBePerTenant() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("tenant-1");
        }
        assertThat(rateLimiter.tryAcquire("tenant-1").allowed()).isFalse();
        assertThat(rateLimiter.tryAcquire("tenant-2").allowed()).isTrue();
    }

    // ─── Reset ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Reset should clear tenant counter")
    void resetShouldClearCounter() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("tenant-1");
        }
        rateLimiter.reset("tenant-1");
        assertThat(rateLimiter.tryAcquire("tenant-1").allowed()).isTrue();
    }

    @Test
    @DisplayName("resetAll should clear all tenant counters")
    void resetAllShouldClearAllCounters() {
        rateLimiter.tryAcquire("tenant-1");
        rateLimiter.tryAcquire("tenant-2");
        rateLimiter.resetAll();
        assertThat(rateLimiter.getTrackedKeyCount()).isEqualTo(0);
    }

    // ─── Inspection ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentCount should reflect actual count")
    void getCurrentCountShouldReflectActualCount() {
        rateLimiter.tryAcquire("tenant-1");
        rateLimiter.tryAcquire("tenant-1");
        rateLimiter.tryAcquire("tenant-1");
        assertThat(rateLimiter.getStats().getTotalAllowed()).isEqualTo(3);
    }

    @Test
    @DisplayName("getActiveTenantCount should count distinct tenants")
    void getActiveTenantCountShouldCountDistinctTenants() {
        rateLimiter.tryAcquire("tenant-1");
        rateLimiter.tryAcquire("tenant-2");
        rateLimiter.tryAcquire("tenant-3");
        assertThat(rateLimiter.getTrackedKeyCount()).isEqualTo(3);
    }

    // ─── Construction validation ──────────────────────────────────────────────

    @Test
    @DisplayName("Zero requestsPerMinute should throw")
    void zeroRequestsPerMinuteShouldThrow() {
        assertThatThrownBy(() -> RateLimiterConfig.builder().maxRequestsPerMinute(0).build())
                .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxRequestsPerMinute must be positive");
    }

    @Test
    @DisplayName("Null metrics should throw NullPointerException")
    void nullMetricsShouldThrow() {
        assertThatThrownBy(() -> DefaultRateLimiter.create(RateLimiterConfig.builder().build(), null, "auth.gateway.rate_limit"))
                .isInstanceOf(NullPointerException.class);
    }
}
