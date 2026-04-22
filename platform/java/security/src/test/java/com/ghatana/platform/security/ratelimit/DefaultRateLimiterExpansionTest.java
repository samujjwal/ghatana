package com.ghatana.platform.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: Rate limiter edge cases and concurrent scenarios.
 * Tests quota management, burst handling, concurrent requests, and recovery behavior.
 *
 * @doc.type class
 * @doc.purpose Rate limiter edge cases and concurrent request handling
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DefaultRateLimiter - Phase 3 Expansion [GH-90000]")
class DefaultRateLimiterExpansionTest {

    private DefaultRateLimiter limiter;

    @BeforeEach
    void setUp() { // GH-90000
        limiter = DefaultRateLimiter.create( // GH-90000
            RateLimiterConfig.builder() // GH-90000
                .maxRequestsPerMinute(5) // GH-90000
                .burstSize(5) // GH-90000
                .windowDuration(Duration.ofMinutes(1)) // GH-90000
                .build() // GH-90000
        );
    }

    // ============================================
    // QUOTA EXHAUSTION AND RECOVERY (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Quota Exhaustion and Recovery [GH-90000]")
    class QuotaTests {

        @Test
        @DisplayName("After quota exhaustion, subsequent requests are rejected [GH-90000]")
        void quotaExhaustionBlocks() { // GH-90000
            String tenant = "tenant-exhausted";

            // Exhaust the quota (5 requests allowed) // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                assertThat(limiter.tryAcquire(tenant).allowed()).isTrue(); // GH-90000
            }

            // 6th request should be rejected
            assertThat(limiter.tryAcquire(tenant).allowed()).isFalse(); // GH-90000

            // Additional attempts should remain rejected
            assertThat(limiter.tryAcquire(tenant).allowed()).isFalse(); // GH-90000
            assertThat(limiter.tryAcquire(tenant).allowed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Reset quota allows same tenant to acquire again [GH-90000]")
        void quotaResetAllowsRecovery() { // GH-90000
            String tenant = "tenant-reset";

            // Exhaust quota
            for (int i = 0; i < 5; i++) { // GH-90000
                limiter.tryAcquire(tenant); // GH-90000
            }
            assertThat(limiter.tryAcquire(tenant).allowed()).isFalse(); // GH-90000

            // Reset should allow quota reload
            limiter.reset(tenant); // GH-90000
            assertThat(limiter.tryAcquire(tenant).allowed()).isTrue(); // GH-90000
            assertThat(limiter.tryAcquire(tenant).allowed()).isTrue(); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT REQUEST HANDLING (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Request Handling [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent requests from same tenant properly enforce quota [GH-90000]")
        void concurrentRequestsEnforceQuota() throws InterruptedException { // GH-90000
            String tenant = "tenant-concurrent";
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger allowedCount = new AtomicInteger(0); // GH-90000
            AtomicInteger rejectedCount = new AtomicInteger(0); // GH-90000

            for (int i = 0; i < threadCount; i++) { // GH-90000
                new Thread(() -> { // GH-90000
                    try {
                        RateLimiter.AcquireResult result = limiter.tryAcquire(tenant); // GH-90000
                        if (result.allowed()) { // GH-90000
                            allowedCount.incrementAndGet(); // GH-90000
                        } else {
                            rejectedCount.incrementAndGet(); // GH-90000
                        }
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                }).start(); // GH-90000
            }

            latch.await(); // GH-90000

            // Only 5 should be allowed (burst size), rest rejected // GH-90000
            assertThat(allowedCount.get()).isEqualTo(5); // GH-90000
            assertThat(rejectedCount.get()).isEqualTo(5); // GH-90000
            assertThat(limiter.getStats().getTotalAllowed()).isGreaterThanOrEqualTo(5); // GH-90000
        }
    }

    // ============================================
    // MULTI-TENANT ISOLATION (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Multi-Tenant Isolation [GH-90000]")
    class MultiTenantTests {

        @Test
        @DisplayName("Different tenants have independent quotas [GH-90000]")
        void tenantQuotasAreIndependent() { // GH-90000
            String tenant1 = "tenant-1";
            String tenant2 = "tenant-2";

            // Exhaust tenant-1's quota
            for (int i = 0; i < 5; i++) { // GH-90000
                assertThat(limiter.tryAcquire(tenant1).allowed()).isTrue(); // GH-90000
            }
            assertThat(limiter.tryAcquire(tenant1).allowed()).isFalse(); // GH-90000

            // tenant-2 should still have full quota
            for (int i = 0; i < 5; i++) { // GH-90000
                assertThat(limiter.tryAcquire(tenant2).allowed()).isTrue(); // GH-90000
            }
            assertThat(limiter.tryAcquire(tenant2).allowed()).isFalse(); // GH-90000
        }
    }

    // ============================================
    // RESET BEHAVIOR (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Reset Behavior [GH-90000]")
    class ResetTests {

        @Test
        @DisplayName("resetAll() clears quotas for all tracked tenants [GH-90000]")
        void resetAllClearsAllTenants() { // GH-90000
            // Create quota usage across multiple tenants
            limiter.tryAcquire("tenant-1 [GH-90000]");
            limiter.tryAcquire("tenant-2 [GH-90000]");
            limiter.tryAcquire("tenant-3 [GH-90000]");

            long trackedBefore = limiter.getTrackedKeyCount(); // GH-90000
            assertThat(trackedBefore).isGreaterThan(0); // GH-90000

            // Reset all should clear all tracking
            limiter.resetAll(); // GH-90000
            assertThat(limiter.getTrackedKeyCount()).isZero(); // GH-90000

            // All tenants should be able to start fresh
            assertThat(limiter.tryAcquire("tenant-1 [GH-90000]").allowed()).isTrue();
            assertThat(limiter.tryAcquire("tenant-1 [GH-90000]").allowed()).isTrue();
        }
    }
}
