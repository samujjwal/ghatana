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
@DisplayName("RateLimiter Tests [GH-90000]")
class RateLimiterTest {

    private DefaultRateLimiter rateLimiter;

    @BeforeEach
    void setUp() { // GH-90000
        rateLimiter = DefaultRateLimiter.create( // GH-90000
                RateLimiterConfig.builder() // GH-90000
                        .maxRequestsPerMinute(5) // GH-90000
                        .burstSize(5) // GH-90000
                        .windowDuration(Duration.ofMinutes(1)) // GH-90000
                        .build(), // GH-90000
                new NoopMetricsCollector(), // GH-90000
                "auth.gateway.rate_limit"
        );
    }

    // ─── Basic allow / reject ─────────────────────────────────────────────────

    @Test
    @DisplayName("Requests within limit should be allowed [GH-90000]")
    void requestsWithinLimitShouldBeAllowed() { // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            assertThat(rateLimiter.tryAcquire("tenant-1 [GH-90000]").allowed()).isTrue();
        }
    }

    @Test
    @DisplayName("Request exceeding limit should be rejected [GH-90000]")
    void requestExceedingLimitShouldBeRejected() { // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            rateLimiter.tryAcquire("tenant-1 [GH-90000]");
        }
        assertThat(rateLimiter.tryAcquire("tenant-1 [GH-90000]").allowed()).isFalse();
    }

    // ─── Tenant isolation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Rate limit should be per-tenant [GH-90000]")
    void rateLimitShouldBePerTenant() { // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            rateLimiter.tryAcquire("tenant-1 [GH-90000]");
        }
        assertThat(rateLimiter.tryAcquire("tenant-1 [GH-90000]").allowed()).isFalse();
        assertThat(rateLimiter.tryAcquire("tenant-2 [GH-90000]").allowed()).isTrue();
    }

    // ─── Reset ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Reset should clear tenant counter [GH-90000]")
    void resetShouldClearCounter() { // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            rateLimiter.tryAcquire("tenant-1 [GH-90000]");
        }
        rateLimiter.reset("tenant-1 [GH-90000]");
        assertThat(rateLimiter.tryAcquire("tenant-1 [GH-90000]").allowed()).isTrue();
    }

    @Test
    @DisplayName("resetAll should clear all tenant counters [GH-90000]")
    void resetAllShouldClearAllCounters() { // GH-90000
        rateLimiter.tryAcquire("tenant-1 [GH-90000]");
        rateLimiter.tryAcquire("tenant-2 [GH-90000]");
        rateLimiter.resetAll(); // GH-90000
        assertThat(rateLimiter.getTrackedKeyCount()).isEqualTo(0); // GH-90000
    }

    // ─── Inspection ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentCount should reflect actual count [GH-90000]")
    void getCurrentCountShouldReflectActualCount() { // GH-90000
        rateLimiter.tryAcquire("tenant-1 [GH-90000]");
        rateLimiter.tryAcquire("tenant-1 [GH-90000]");
        rateLimiter.tryAcquire("tenant-1 [GH-90000]");
        assertThat(rateLimiter.getStats().getTotalAllowed()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("getActiveTenantCount should count distinct tenants [GH-90000]")
    void getActiveTenantCountShouldCountDistinctTenants() { // GH-90000
        rateLimiter.tryAcquire("tenant-1 [GH-90000]");
        rateLimiter.tryAcquire("tenant-2 [GH-90000]");
        rateLimiter.tryAcquire("tenant-3 [GH-90000]");
        assertThat(rateLimiter.getTrackedKeyCount()).isEqualTo(3); // GH-90000
    }

    // ─── Construction validation ──────────────────────────────────────────────

    @Test
    @DisplayName("Zero requestsPerMinute should throw [GH-90000]")
    void zeroRequestsPerMinuteShouldThrow() { // GH-90000
        assertThatThrownBy(() -> RateLimiterConfig.builder().maxRequestsPerMinute(0).build()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("maxRequestsPerMinute must be positive [GH-90000]");
    }

    @Test
    @DisplayName("Null metrics should throw NullPointerException [GH-90000]")
    void nullMetricsShouldThrow() { // GH-90000
        assertThatThrownBy(() -> DefaultRateLimiter.create(RateLimiterConfig.builder().build(), null, "auth.gateway.rate_limit")) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
