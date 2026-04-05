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
 * Phase 3 expansion: Graceful degradation manager concurrent mode transitions and edge cases.
 * Tests concurrent mode changes, large tenant scale, action allowance under load, and consistency.
 *
 * @doc.type class
 * @doc.purpose Graceful degradation concurrent transitions and consistency verification
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Degradation Manager - Phase 3 Expansion")
class DegradationManagerExpansionTest extends EventloopTestBase {

    private InMemoryGracefulDegradationManager manager;

    @BeforeEach
    void setUp() {
        manager = new InMemoryGracefulDegradationManager();
    }

    // ============================================
    // CONCURRENT MODE TRANSITIONS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Mode Transitions")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent mode changes across tenants")
        void concurrentModeChanges() throws InterruptedException {
            final DegradationMode[] modes = {DegradationMode.READ_ONLY, DegradationMode.NOTIFICATIONS_ONLY, DegradationMode.OFFLINE};
            int tenantCount = 9;
            CountDownLatch latch = new CountDownLatch(tenantCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < tenantCount; i++) {
                final int tenantIndex = i;
                new Thread(() -> {
                    try {
                        DegradationMode mode = modes[tenantIndex % modes.length];
                        runBlocking(() -> manager.setMode("t" + tenantIndex, mode));
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            assertThat(successCount.get()).isEqualTo(tenantCount);
            // Verify sample tenants
            assertThat(runPromise(() -> manager.getMode("t0"))).isEqualTo(DegradationMode.READ_ONLY);
            assertThat(runPromise(() -> manager.getMode("t1"))).isEqualTo(DegradationMode.NOTIFICATIONS_ONLY);
            assertThat(runPromise(() -> manager.getMode("t8"))).isEqualTo(DegradationMode.OFFLINE);
        }

        @Test
        @DisplayName("Rapid mode changes for same tenant")
        void rapidModeChanges() {
            String tenantId = "t-rapid";
            DegradationMode[] modes = {DegradationMode.FULL, DegradationMode.READ_ONLY, DegradationMode.OFFLINE, DegradationMode.NOTIFICATIONS_ONLY};

            for (int cycle = 0; cycle < 10; cycle++) {
                DegradationMode mode = modes[cycle % modes.length];
                runBlocking(() -> manager.setMode(tenantId, mode));
                DegradationMode result = runPromise(() -> manager.getMode(tenantId));
                assertThat(result).isEqualTo(mode);
            }
        }

        @Test
        @DisplayName("Read operations concurrent with mode transitions")
        void concurrentReadDuringTransition() throws InterruptedException {
            runBlocking(() -> manager.setMode("t-concurrent", DegradationMode.READ_ONLY));

            int operations = 20;
            CountDownLatch latch = new CountDownLatch(operations);
            AtomicInteger allowedReads = new AtomicInteger(0);
            AtomicInteger blockedWrites = new AtomicInteger(0);

            for (int i = 0; i < operations; i++) {
                int index = i;
                new Thread(() -> {
                    try {
                        if (index % 2 == 0) {
                            // Read should be allowed
                            if (runPromise(() -> manager.isActionAllowed("t-concurrent", "READ"))) {
                                allowedReads.incrementAndGet();
                            }
                        } else {
                            // Write should be blocked
                            if (!runPromise(() -> manager.isActionAllowed("t-concurrent", "WRITE"))) {
                                blockedWrites.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            assertThat(allowedReads.get()).isEqualTo(10);
            assertThat(blockedWrites.get()).isEqualTo(10);
        }
    }

    // ============================================
    // ACTION ALLOWANCE EDGE CASES (2 tests)
    // ============================================

    @Nested
    @DisplayName("Action Allowance Edge Cases")
    class ActionAllowanceTests {

        @Test
        @DisplayName("Mode transitions affect action allowance immediately")
        void modeTransitionAffectsActions() {
            String tenantId = "t-action";

            // Start in FULL mode
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "DELETE"))).isTrue();

            // Transition to READ_ONLY
            runBlocking(() -> manager.setMode(tenantId, DegradationMode.READ_ONLY));
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "READ"))).isTrue();
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "DELETE"))).isFalse();

            // Transition to OFFLINE
            runBlocking(() -> manager.setMode(tenantId, DegradationMode.OFFLINE));
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "READ"))).isFalse();
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "NOTIFY"))).isFalse();

            // Back to FULL
            runBlocking(() -> manager.setMode(tenantId, DegradationMode.FULL));
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "DELETE"))).isTrue();
        }

        @Test
        @DisplayName("Action allowance across all modes and actions")
        void allModeActionCombinations() {
            String tenantId = "t-combinations";
            final String[] actions = {"READ", "WRITE", "DELETE", "NOTIFY"};

            // FULL mode - all allowed
            runBlocking(() -> manager.setMode(tenantId, DegradationMode.FULL));
            for (String action : actions) {
                assertThat(runPromise(() -> manager.isActionAllowed(tenantId, action))).isTrue();
            }

            // READ_ONLY - only READ allowed
            runBlocking(() -> manager.setMode(tenantId, DegradationMode.READ_ONLY));
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "READ"))).isTrue();
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "WRITE"))).isFalse();
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "DELETE"))).isFalse();
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "NOTIFY"))).isFalse();

            // NOTIFICATIONS_ONLY - only NOTIFY allowed
            runBlocking(() -> manager.setMode(tenantId, DegradationMode.NOTIFICATIONS_ONLY));
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "NOTIFY"))).isTrue();
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "READ"))).isFalse();
            assertThat(runPromise(() -> manager.isActionAllowed(tenantId, "WRITE"))).isFalse();

            // OFFLINE - nothing allowed
            runBlocking(() -> manager.setMode(tenantId, DegradationMode.OFFLINE));
            for (String action : actions) {
                assertThat(runPromise(() -> manager.isActionAllowed(tenantId, action))).isFalse();
            }
        }
    }

    // ============================================
    // LARGE-SCALE TENANT HANDLING (2 tests)
    // ============================================

    @Nested
    @DisplayName("Large-Scale Tenant Handling")
    class ScaleTests {

        @Test
        @DisplayName("Handle 100+ tenants with different degradation modes")
        void largeScaleModeAssignment() {
            final DegradationMode[] modes = {DegradationMode.FULL, DegradationMode.READ_ONLY, DegradationMode.OFFLINE};
            int tenantCount = 100;

            for (int i = 0; i < tenantCount; i++) {
                final int index = i;
                DegradationMode mode = modes[index % modes.length];
                runBlocking(() -> manager.setMode("tenant-" + index, mode));
            }

            // Verify sample tenants
            // 0 % 3 = 0 → FULL, 1 % 3 = 1 → READ_ONLY, 2 % 3 = 2 → OFFLINE
            assertThat(runPromise(() -> manager.getMode("tenant-0"))).isEqualTo(DegradationMode.FULL);
            assertThat(runPromise(() -> manager.getMode("tenant-1"))).isEqualTo(DegradationMode.READ_ONLY);
            assertThat(runPromise(() -> manager.getMode("tenant-2"))).isEqualTo(DegradationMode.OFFLINE);
            // 99 % 3 = 0 → FULL
            assertThat(runPromise(() -> manager.getMode("tenant-99"))).isEqualTo(DegradationMode.FULL);
        }

        @Test
        @DisplayName("Perform action checks on 500+ tenants in degraded modes")
        void largeScaleActionChecks() {
            int tenantCount = 500;

            // Set degradation modes
            for (int i = 0; i < tenantCount; i++) {
                final int index = i;
                if (index % 2 == 0) {
                    runBlocking(() -> manager.setMode("act-" + index, DegradationMode.READ_ONLY));
                } else {
                    runBlocking(() -> manager.setMode("act-" + index, DegradationMode.OFFLINE));
                }
            }

            // Verify action allowance at sample indices
            assertThat(runPromise(() -> manager.isActionAllowed("act-0", "READ"))).isTrue();
            assertThat(runPromise(() -> manager.isActionAllowed("act-0", "WRITE"))).isFalse();
            assertThat(runPromise(() -> manager.isActionAllowed("act-1", "READ"))).isFalse();
            assertThat(runPromise(() -> manager.isActionAllowed("act-250", "READ"))).isTrue();
            assertThat(runPromise(() -> manager.isActionAllowed("act-251", "READ"))).isFalse();
        }
    }

    // ============================================
    // MODE STATE CONSISTENCY (1 test)
    // ============================================

    @Nested
    @DisplayName("Mode State Consistency")
    class ConsistencyTests {

        @Test
        @DisplayName("Mode state remains consistent across repeated queries")
        void consistentModeQueries() {
            String tenantId = "t-consistency";
            runBlocking(() -> manager.setMode(tenantId, DegradationMode.READ_ONLY));

            // Query multiple times and ensure consistency
            for (int i = 0; i < 10; i++) {
                DegradationMode mode = runPromise(() -> manager.getMode(tenantId));
                assertThat(mode).isEqualTo(DegradationMode.READ_ONLY);
            }
        }
    }
}
