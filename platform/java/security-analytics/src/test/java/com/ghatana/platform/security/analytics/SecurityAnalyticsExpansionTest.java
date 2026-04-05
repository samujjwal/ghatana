/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.analytics;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 Expansion tests for Security Analytics module.
 * Tests egress monitoring, prompt injection detection, and concurrent access.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for security analytics subsystem
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("SecurityAnalytics - Phase 3 Expansion")
class SecurityAnalyticsExpansionTest extends EventloopTestBase {

    // ============================================
    // EGRESS MONITORING (4 tests)
    // ============================================

    @Nested
    @DisplayName("Egress Monitoring")
    class EgressMonitoringTests {

        private DefaultEgressMonitor monitor;

        @BeforeEach
        void setUp() {
            monitor = new DefaultEgressMonitor(10000); // 10 KB limit
        }

        @Test
        @DisplayName("Record single egress event")
        void recordSingleEvent() {
            runBlocking(() -> monitor.record("tenant-1", "agent-1", "search", 100));

            long total = runPromise(() -> monitor.currentWindowBytes("tenant-1", "agent-1"));
            assertThat(total).isEqualTo(100);
        }

        @Test
        @DisplayName("Accumulate many egress events")
        void accumulateManyEvents() {
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                runBlocking(() -> monitor.record("t1", "a1", "op-" + idx, 50));
            }

            long total = runPromise(() -> monitor.currentWindowBytes("t1", "a1"));
            assertThat(total).isEqualTo(5000);
        }

        @Test
        @DisplayName("Reject egress exceeding limit")
        void rejectExceedsLimit() {
            assertThatThrownBy(() ->
                runPromise(() -> monitor.record("t1", "a1", "large-op", 20000))
            ).isInstanceOf(EgressLimitExceededException.class);
        }

        @Test
        @DisplayName("Tenant-specific egress limits")
        void tenantSpecificLimits() {
            monitor.setTenantLimit("premium-tenant", 1_000_000);
            monitor.setTenantLimit("standard-tenant", 10_000);

            // Premium tenant can send more
            runBlocking(() -> monitor.record("premium-tenant", "a1", "op", 50_000));

            long premiumTotal = runPromise(() -> 
                monitor.currentWindowBytes("premium-tenant", "a1"));
            assertThat(premiumTotal).isEqualTo(50_000);

            // Standard tenant limited
            assertThatThrownBy(() ->
                runPromise(() -> monitor.record("standard-tenant", "a1", "op", 50_000))
            ).isInstanceOf(EgressLimitExceededException.class);
        }
    }

    // ============================================
    // MULTI-TENANT ISOLATION (3 tests)
    // ============================================

    @Nested
    @DisplayName("Multi-Tenant Egress Isolation")
    class MultiTenantEgressTests {

        private DefaultEgressMonitor monitor;

        @BeforeEach
        void setUp() {
            monitor = new DefaultEgressMonitor(10000);
        }

        @Test
        @DisplayName("Tenant egress isolated from each other")
        void tenantIsolation() {
            runBlocking(() -> monitor.record("tenant-a", "agent-1", "query", 1000));
            runBlocking(() -> monitor.record("tenant-b", "agent-1", "query", 2000));
            runBlocking(() -> monitor.record("tenant-c", "agent-1", "query", 3000));

            long totalA = runPromise(() -> monitor.currentWindowBytes("tenant-a", "agent-1"));
            long totalB = runPromise(() -> monitor.currentWindowBytes("tenant-b", "agent-1"));
            long totalC = runPromise(() -> monitor.currentWindowBytes("tenant-c", "agent-1"));

            assertThat(totalA).isEqualTo(1000);
            assertThat(totalB).isEqualTo(2000);
            assertThat(totalC).isEqualTo(3000);
        }

        @Test
        @DisplayName("Agent egress isolated within same tenant")
        void agentIsolation() {
            runBlocking(() -> monitor.record("tenant-1", "agent-a", "op", 1000));
            runBlocking(() -> monitor.record("tenant-1", "agent-b", "op", 2000));
            runBlocking(() -> monitor.record("tenant-1", "agent-c", "op", 3000));

            long agentA = runPromise(() -> monitor.currentWindowBytes("tenant-1", "agent-a"));
            long agentB = runPromise(() -> monitor.currentWindowBytes("tenant-1", "agent-b"));
            long agentC = runPromise(() -> monitor.currentWindowBytes("tenant-1", "agent-c"));

            assertThat(agentA).isEqualTo(1000);
            assertThat(agentB).isEqualTo(2000);
            assertThat(agentC).isEqualTo(3000);
        }

        @Test
        @DisplayName("Many tenants and agents concurrent tracking")
        void manyConcurrentTenants() {
            for (int t = 0; t < 50; t++) {
                final int tenantIdx = t;
                for (int a = 0; a < 10; a++) {
                    final int agentIdx = a;
                    runBlocking(() -> monitor.record(
                        "tenant-" + tenantIdx,
                        "agent-" + agentIdx,
                        "op",
                        100
                    ));
                }
            }

            // Verify specific entries
            long entry = runPromise(() -> monitor.currentWindowBytes("tenant-25", "agent-5"));
            assertThat(entry).isEqualTo(100);
        }
    }

    // ============================================
    // PROMPT INJECTION DETECTION (4 tests)
    // ============================================

    @Nested
    @DisplayName("Prompt Injection Detection")
    class InjectionDetectionTests {

        private RegexPromptInjectionDetector detector;

        @BeforeEach
        void setUp() {
            detector = new RegexPromptInjectionDetector();
        }

        @Test
        @DisplayName("Detect SQL injection patterns")
        void detectSQLInjection() {
            boolean detected = detector.isSuspiciousPrompt(
                "'; DROP TABLE users; --");

            assertThat(detected).isTrue();
        }

        @Test
        @DisplayName("Allow benign prompts")
        void allowBenignPrompts() {
            boolean detected = detector.isSuspiciousPrompt(
                "Please summarize the following document");

            assertThat(detected).isFalse();
        }

        @Test
        @DisplayName("Detect multiple injection patterns")
        void detectMultiplePatterns() {
            String[] suspiciousPrompts = {
                "'; DROP TABLE --",
                "SELECT * FROM users",
                "exec sp_executesql",
                "union select",
                "<script>alert('xss')</script>"
            };

            for (String prompt : suspiciousPrompts) {
                boolean detected = detector.isSuspiciousPrompt(prompt);
                assertThat(detected).isTrue();
            }
        }

        @Test
        @DisplayName("Case-insensitive detection")
        void caseInsensitiveDetection() {
            boolean lowercase = detector.isSuspiciousPrompt("select * from");
            boolean uppercase = detector.isSuspiciousPrompt("SELECT * FROM");
            boolean mixed = detector.isSuspiciousPrompt("SeLeCt * FrOm");

            assertThat(lowercase).isTrue();
            assertThat(uppercase).isTrue();
            assertThat(mixed).isTrue();
        }
    }

    // ============================================
    // CONCURRENT EGRESS TRACKING (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Egress Tracking")
    class ConcurrentEgressTests {

        private DefaultEgressMonitor monitor;

        @BeforeEach
        void setUp() {
            monitor = new DefaultEgressMonitor(100_000);
        }

        @Test
        @DisplayName("Many concurrent egress records")
        void concurrentEgressRecords() throws Exception {
            int threadCount = 30;
            CountDownLatch latch = new CountDownLatch(threadCount);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < 100; i++) {
                                final int idx = i;
                                runBlocking(() -> monitor.record(
                                    "tenant-" + threadIdx,
                                    "agent-" + idx,
                                    "op-" + idx,
                                    100
                                ));
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }
        }

        @Test
        @DisplayName("Concurrent detection of injection patterns")
        void concurrentInjectionDetection() throws Exception {
            RegexPromptInjectionDetector detector = new RegexPromptInjectionDetector();

            String[] prompts = {
                "Please help me",
                "'; DROP TABLE --",
                "Normal question here",
                "SELECT * FROM",
                "What is AI?",
                "exec sp_executesql"
            };

            int threadCount = 25;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger suspiciousCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < 1000; i++) {
                                String prompt = prompts[i % prompts.length];
                                if (detector.isSuspiciousPrompt(prompt)) {
                                    suspiciousCount.incrementAndGet();
                                }
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            // Should detect suspicious prompts across all threads
            assertThat(suspiciousCount.get()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Mixed egress recording and injection detection")
        void mixedOperations() throws Exception {
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);

            RegexPromptInjectionDetector detector = new RegexPromptInjectionDetector();

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < 200; i++) {
                                final int idx = i;
                                // Alternate between egress recording and detection
                                if (idx % 2 == 0) {
                                    runBlocking(() -> monitor.record(
                                        "tenant-" + threadIdx,
                                        "agent-" + idx,
                                        "op",
                                        50
                                    ));
                                } else {
                                    String prompt = idx % 3 == 0 ?
                                        "SELECT * FROM users" :
                                        "Normal prompt here";
                                    detector.isSuspiciousPrompt(prompt);
                                }
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }
        }
    }

    // ============================================
    // EDGE CASES (2 tests)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very large egress amounts at limit boundary")
        void largeEgressAtBoundary() {
            DefaultEgressMonitor monitor = new DefaultEgressMonitor(10_000);

            // Record up to near limit
            runBlocking(() -> monitor.record("t1", "a1", "op1", 9_500));

            long total1 = runPromise(() -> monitor.currentWindowBytes("t1", "a1"));
            assertThat(total1).isEqualTo(9_500);

            // Record more at boundary
            runBlocking(() -> monitor.record("t1", "a1", "op2", 500));

            long total2 = runPromise(() -> monitor.currentWindowBytes("t1", "a1"));
            assertThat(total2).isEqualTo(10_000);
        }

        @Test
        @DisplayName("Very long prompts for injection detection")
        void veryLongPromptDetection() {
            RegexPromptInjectionDetector detector = new RegexPromptInjectionDetector();

            String longBenign = "Please explain " + "concepts ".repeat(1000);
            String longSuspicious = "SELECT * FROM " + "users_table ".repeat(1000);

            boolean benignDetected = detector.isSuspiciousPrompt(longBenign);
            boolean suspiciousDetected = detector.isSuspiciousPrompt(longSuspicious);

            assertThat(benignDetected).isFalse();
            assertThat(suspiciousDetected).isTrue();
        }
    }
}
