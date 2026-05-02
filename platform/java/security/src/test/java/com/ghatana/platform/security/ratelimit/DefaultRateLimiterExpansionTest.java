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
@DisplayName("DefaultRateLimiter - Phase 3 Expansion")
class DefaultRateLimiterExpansionTest {

    private DefaultRateLimiter limiter;

    @BeforeEach
    void setUp() { 
        limiter = DefaultRateLimiter.create( 
            RateLimiterConfig.builder() 
                .maxRequestsPerMinute(5) 
                .burstSize(5) 
                .windowDuration(Duration.ofMinutes(1)) 
                .build() 
        );
    }

    // ============================================
    // QUOTA EXHAUSTION AND RECOVERY (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Quota Exhaustion and Recovery")
    class QuotaTests {

        @Test
        @DisplayName("After quota exhaustion, subsequent requests are rejected")
        void quotaExhaustionBlocks() { 
            String tenant = "tenant-exhausted";

            // Exhaust the quota (5 requests allowed) 
            for (int i = 0; i < 5; i++) { 
                assertThat(limiter.tryAcquire(tenant).allowed()).isTrue(); 
            }

            // 6th request should be rejected
            assertThat(limiter.tryAcquire(tenant).allowed()).isFalse(); 

            // Additional attempts should remain rejected
            assertThat(limiter.tryAcquire(tenant).allowed()).isFalse(); 
            assertThat(limiter.tryAcquire(tenant).allowed()).isFalse(); 
        }

        @Test
        @DisplayName("Reset quota allows same tenant to acquire again")
        void quotaResetAllowsRecovery() { 
            String tenant = "tenant-reset";

            // Exhaust quota
            for (int i = 0; i < 5; i++) { 
                limiter.tryAcquire(tenant); 
            }
            assertThat(limiter.tryAcquire(tenant).allowed()).isFalse(); 

            // Reset should allow quota reload
            limiter.reset(tenant); 
            assertThat(limiter.tryAcquire(tenant).allowed()).isTrue(); 
            assertThat(limiter.tryAcquire(tenant).allowed()).isTrue(); 
        }
    }

    // ============================================
    // CONCURRENT REQUEST HANDLING (1 test) 
    // ============================================

    @Nested
    @DisplayName("Concurrent Request Handling")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent requests from same tenant properly enforce quota")
        void concurrentRequestsEnforceQuota() throws InterruptedException { 
            String tenant = "tenant-concurrent";
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount); 
            AtomicInteger allowedCount = new AtomicInteger(0); 
            AtomicInteger rejectedCount = new AtomicInteger(0); 

            for (int i = 0; i < threadCount; i++) { 
                new Thread(() -> { 
                    try {
                        RateLimiter.AcquireResult result = limiter.tryAcquire(tenant); 
                        if (result.allowed()) { 
                            allowedCount.incrementAndGet(); 
                        } else {
                            rejectedCount.incrementAndGet(); 
                        }
                    } finally {
                        latch.countDown(); 
                    }
                }).start(); 
            }

            latch.await(); 

            // Only 5 should be allowed (burst size), rest rejected 
            assertThat(allowedCount.get()).isEqualTo(5); 
            assertThat(rejectedCount.get()).isEqualTo(5); 
            assertThat(limiter.getStats().getTotalAllowed()).isGreaterThanOrEqualTo(5); 
        }
    }

    // ============================================
    // MULTI-TENANT ISOLATION (1 test) 
    // ============================================

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    class MultiTenantTests {

        @Test
        @DisplayName("Different tenants have independent quotas")
        void tenantQuotasAreIndependent() { 
            String tenant1 = "tenant-1";
            String tenant2 = "tenant-2";

            // Exhaust tenant-1's quota
            for (int i = 0; i < 5; i++) { 
                assertThat(limiter.tryAcquire(tenant1).allowed()).isTrue(); 
            }
            assertThat(limiter.tryAcquire(tenant1).allowed()).isFalse(); 

            // tenant-2 should still have full quota
            for (int i = 0; i < 5; i++) { 
                assertThat(limiter.tryAcquire(tenant2).allowed()).isTrue(); 
            }
            assertThat(limiter.tryAcquire(tenant2).allowed()).isFalse(); 
        }
    }

    // ============================================
    // RESET BEHAVIOR (1 test) 
    // ============================================

    @Nested
    @DisplayName("Reset Behavior")
    class ResetTests {

        @Test
        @DisplayName("resetAll() clears quotas for all tracked tenants")
        void resetAllClearsAllTenants() { 
            // Create quota usage across multiple tenants
            limiter.tryAcquire("tenant-1");
            limiter.tryAcquire("tenant-2");
            limiter.tryAcquire("tenant-3");

            long trackedBefore = limiter.getTrackedKeyCount(); 
            assertThat(trackedBefore).isGreaterThan(0); 

            // Reset all should clear all tracking
            limiter.resetAll(); 
            assertThat(limiter.getTrackedKeyCount()).isZero(); 

            // All tenants should be able to start fresh
            assertThat(limiter.tryAcquire("tenant-1").allowed()).isTrue();
            assertThat(limiter.tryAcquire("tenant-1").allowed()).isTrue();
        }
    }
}
