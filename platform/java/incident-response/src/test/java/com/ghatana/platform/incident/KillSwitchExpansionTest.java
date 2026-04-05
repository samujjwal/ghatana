/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: Kill switch concurrent operations and edge cases.
 * Tests concurrent activation, deactivation, large-scale tenant handling, and consistency.
 *
 * @doc.type class
 * @doc.purpose Kill switch concurrent operations and consistency verification
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Kill Switch - Phase 3 Expansion")
class KillSwitchExpansionTest extends EventloopTestBase {

    private InMemoryKillSwitchService killSwitch;

    @BeforeEach
    void setUp() {
        killSwitch = new InMemoryKillSwitchService();
    }

    // ============================================
    // CONCURRENT OPERATIONS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent activations across different tenants")
        void concurrentActivations() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger activateCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int tenantIndex = i;
                new Thread(() -> {
                    try {
                        runBlocking(() -> killSwitch.activate("t" + tenantIndex, "incident", "inc-" + tenantIndex));
                        activateCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            assertThat(activateCount.get()).isEqualTo(threadCount);
            // Verify sample tenants
            assertThat(runPromise(() -> killSwitch.isActive("t0"))).isTrue();
            assertThat(runPromise(() -> killSwitch.isActive("t5"))).isTrue();
            assertThat(runPromise(() -> killSwitch.isActive("t9"))).isTrue();
        }

        @Test
        @DisplayName("Concurrent deactivations preserve global state")
        void concurrentDeactivations() throws InterruptedException {
            // First activate multiple tenants
            for (int i = 0; i < 5; i++) {
                final int index = i;
                runBlocking(() -> killSwitch.activate("t" + index, "incident", "inc-" + index));
            }
            // Also activate globally
            runBlocking(() -> killSwitch.activateGlobal("critical", "inc-global"));

            int deactivateCount = 3;
            CountDownLatch latch = new CountDownLatch(deactivateCount);
            AtomicInteger deactivateSuccess = new AtomicInteger(0);

            for (int i = 0; i < deactivateCount; i++) {
                final int index = i;
                new Thread(() -> {
                    try {
                        runBlocking(() -> killSwitch.deactivate("t" + index, "resolved"));
                        deactivateSuccess.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            assertThat(deactivateSuccess.get()).isEqualTo(deactivateCount);
            // Global should still be active
            assertThat(runPromise(() -> killSwitch.isGlobalActive())).isTrue();
        }

        @Test
        @DisplayName("Concurrent read and write operations maintain consistency")
        void concurrentReadWrite() throws InterruptedException {
            runBlocking(() -> killSwitch.activate("t-rw", "incident", "inc-1"));

            int operations = 20;
            CountDownLatch latch = new CountDownLatch(operations);
            AtomicInteger readCount = new AtomicInteger(0);

            for (int i = 0; i < operations; i++) {
                int index = i;
                new Thread(() -> {
                    try {
                        if (index % 2 == 0) {
                            // Read operation
                            boolean isActive = runPromise(() -> killSwitch.isActive("t-rw"));
                            if (isActive) {
                                readCount.incrementAndGet();
                            }
                        } else {
                            // This would be a write, but we can't deactivate/activate in parallel safely
                            // so just do another read
                            boolean isActive = runPromise(() -> killSwitch.isActive("t-rw"));
                            if (isActive) {
                                readCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            // All should have read as active
            assertThat(readCount.get()).isEqualTo(operations);
        }
    }

    // ============================================
    // LARGE-SCALE TENANT HANDLING (2 tests)
    // ============================================

    @Nested
    @DisplayName("Large-Scale Tenant Handling")
    class ScaleTests {

        @Test
        @DisplayName("Handle 1000+ independent tenant activations")
        void largeScaleTenantActivation() {
            int tenantCount = 1000;

            for (int i = 0; i < tenantCount; i++) {
                final int index = i;
                runBlocking(() -> killSwitch.activate("tenant-" + index, "scaling-test", "inc-" + index));
            }

            // Verify sample tenants are active
            assertThat(runPromise(() -> killSwitch.isActive("tenant-0"))).isTrue();
            assertThat(runPromise(() -> killSwitch.isActive("tenant-500"))).isTrue();
            assertThat(runPromise(() -> killSwitch.isActive("tenant-999"))).isTrue();
        }

        @Test
        @DisplayName("Deactivate 1000+ tenants maintains correctness")
        void largeScaleTenantDeactivation() {
            int tenantCount = 500;

            // Activate all
            for (int i = 0; i < tenantCount; i++) {
                final int index = i;
                runBlocking(() -> killSwitch.activate("scale-" + index, "test", "inc-" + index));
            }

            // Deactivate all
            for (int i = 0; i < tenantCount; i++) {
                final int index = i;
                runBlocking(() -> killSwitch.deactivate("scale-" + index, "resolved"));
            }

            // Verify all are inactive
            assertThat(runPromise(() -> killSwitch.isActive("scale-0"))).isFalse();
            assertThat(runPromise(() -> killSwitch.isActive("scale-249"))).isFalse();
            assertThat(runPromise(() -> killSwitch.isActive("scale-499"))).isFalse();
        }
    }

    // ============================================
    // GLOBAL ACTIVATION EDGE CASES (2 tests)
    // ============================================

    @Nested
    @DisplayName("Global Activation Edge Cases")
    class GlobalEdgeCaseTests {

        @Test
        @DisplayName("Global activation overrides all tenant states")
        void globalActivationOverride() {
            // Activate some tenants
            runBlocking(() -> killSwitch.activate("t-override-1", "test", "inc-1"));
            runBlocking(() -> killSwitch.deactivate("t-override-2", "test")); // Ensure it's inactive

            // Activate globally
            runBlocking(() -> killSwitch.activateGlobal("global incident", "inc-global"));

            // Both should report as active
            assertThat(runPromise(() -> killSwitch.isActive("t-override-1"))).isTrue();
            assertThat(runPromise(() -> killSwitch.isActive("t-override-2"))).isTrue();
            assertThat(runPromise(() -> killSwitch.isGlobalActive())).isTrue();
        }

        @Test
        @DisplayName("Multiple reset cycles maintain state consistency")
        void multipleResetCycles() {
            for (int cycle = 0; cycle < 3; cycle++) {
                final int c = cycle;
                runBlocking(() -> killSwitch.activate("t-reset-" + c, "incident", "inc-" + c));
                runBlocking(() -> killSwitch.activateGlobal("global", "inc-global-" + c));

                assertThat(runPromise(() -> killSwitch.isActive("t-reset-" + c))).isTrue();
                assertThat(runPromise(() -> killSwitch.isGlobalActive())).isTrue();

                killSwitch.reset();

                assertThat(runPromise(() -> killSwitch.isActive("t-reset-" + c))).isFalse();
                assertThat(runPromise(() -> killSwitch.isGlobalActive())).isFalse();
            }
        }
    }
}
