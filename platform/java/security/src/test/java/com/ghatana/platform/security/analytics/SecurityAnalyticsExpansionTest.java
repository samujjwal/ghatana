/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.security.analytics;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
@DisplayName("SecurityAnalytics - Phase 3 Expansion [GH-90000]")
class SecurityAnalyticsExpansionTest extends EventloopTestBase {

    // ============================================
    // EGRESS MONITORING (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Egress Monitoring [GH-90000]")
    class EgressMonitoringTests {

        private DefaultEgressMonitor monitor;

        @BeforeEach
        void setUp() { // GH-90000
            monitor = new DefaultEgressMonitor(10000); // 10 KB limit // GH-90000
        }

        @Test
        @DisplayName("Record single egress event [GH-90000]")
        void recordSingleEvent() { // GH-90000
            runBlocking(() -> monitor.record("tenant-1", "agent-1", "search", 100)); // GH-90000

            long total = runPromise(() -> monitor.currentWindowBytes("tenant-1", "agent-1")); // GH-90000
            assertThat(total).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("Accumulate many egress events [GH-90000]")
        void accumulateManyEvents() { // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                runBlocking(() -> monitor.record("t1", "a1", "op-" + idx, 50)); // GH-90000
            }

            long total = runPromise(() -> monitor.currentWindowBytes("t1", "a1")); // GH-90000
            assertThat(total).isEqualTo(5000); // GH-90000
        }

        @Test
        @DisplayName("Reject egress exceeding limit [GH-90000]")
        void rejectExceedsLimit() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> monitor.record("t1", "a1", "large-op", 20000)) // GH-90000
            ).isInstanceOf(EgressLimitExceededException.class); // GH-90000
        }

        @Test
        @DisplayName("Tenant-specific egress limits [GH-90000]")
        void tenantSpecificLimits() { // GH-90000
            monitor.setTenantLimit("premium-tenant", 1_000_000); // GH-90000
            monitor.setTenantLimit("standard-tenant", 10_000); // GH-90000

            // Premium tenant can send more
            runBlocking(() -> monitor.record("premium-tenant", "a1", "op", 50_000)); // GH-90000

            long premiumTotal = runPromise(() -> // GH-90000
                monitor.currentWindowBytes("premium-tenant", "a1")); // GH-90000
            assertThat(premiumTotal).isEqualTo(50_000); // GH-90000

            // Standard tenant limited
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> monitor.record("standard-tenant", "a1", "op", 50_000)) // GH-90000
            ).isInstanceOf(EgressLimitExceededException.class); // GH-90000
        }
    }

    // ============================================
    // MULTI-TENANT ISOLATION (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Multi-Tenant Egress Isolation [GH-90000]")
    class MultiTenantEgressTests {

        private DefaultEgressMonitor monitor;

        @BeforeEach
        void setUp() { // GH-90000
            monitor = new DefaultEgressMonitor(10000); // GH-90000
        }

        @Test
        @DisplayName("Tenant egress isolated from each other [GH-90000]")
        void tenantIsolation() { // GH-90000
            runBlocking(() -> monitor.record("tenant-a", "agent-1", "query", 1000)); // GH-90000
            runBlocking(() -> monitor.record("tenant-b", "agent-1", "query", 2000)); // GH-90000
            runBlocking(() -> monitor.record("tenant-c", "agent-1", "query", 3000)); // GH-90000

            long totalA = runPromise(() -> monitor.currentWindowBytes("tenant-a", "agent-1")); // GH-90000
            long totalB = runPromise(() -> monitor.currentWindowBytes("tenant-b", "agent-1")); // GH-90000
            long totalC = runPromise(() -> monitor.currentWindowBytes("tenant-c", "agent-1")); // GH-90000

            assertThat(totalA).isEqualTo(1000); // GH-90000
            assertThat(totalB).isEqualTo(2000); // GH-90000
            assertThat(totalC).isEqualTo(3000); // GH-90000
        }

        @Test
        @DisplayName("Agent egress isolated within same tenant [GH-90000]")
        void agentIsolation() { // GH-90000
            runBlocking(() -> monitor.record("tenant-1", "agent-a", "op", 1000)); // GH-90000
            runBlocking(() -> monitor.record("tenant-1", "agent-b", "op", 2000)); // GH-90000
            runBlocking(() -> monitor.record("tenant-1", "agent-c", "op", 3000)); // GH-90000

            long agentA = runPromise(() -> monitor.currentWindowBytes("tenant-1", "agent-a")); // GH-90000
            long agentB = runPromise(() -> monitor.currentWindowBytes("tenant-1", "agent-b")); // GH-90000
            long agentC = runPromise(() -> monitor.currentWindowBytes("tenant-1", "agent-c")); // GH-90000

            assertThat(agentA).isEqualTo(1000); // GH-90000
            assertThat(agentB).isEqualTo(2000); // GH-90000
            assertThat(agentC).isEqualTo(3000); // GH-90000
        }

        @Test
        @DisplayName("Many tenants and agents concurrent tracking [GH-90000]")
        void manyConcurrentTenants() { // GH-90000
            for (int t = 0; t < 50; t++) { // GH-90000
                final int tenantIdx = t;
                for (int a = 0; a < 10; a++) { // GH-90000
                    final int agentIdx = a;
                    runBlocking(() -> monitor.record( // GH-90000
                        "tenant-" + tenantIdx,
                        "agent-" + agentIdx,
                        "op",
                        100
                    ));
                }
            }

            // Verify specific entries
            long entry = runPromise(() -> monitor.currentWindowBytes("tenant-25", "agent-5")); // GH-90000
            assertThat(entry).isEqualTo(100); // GH-90000
        }
    }

    // ============================================
    // PROMPT INJECTION DETECTION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Prompt Injection Detection [GH-90000]")
    class InjectionDetectionTests {

        private RegexPromptInjectionDetector detector;

        @BeforeEach
        void setUp() { // GH-90000
            detector = new RegexPromptInjectionDetector(); // GH-90000
        }

        @Test
        @DisplayName("Detect SQL injection patterns [GH-90000]")
        void detectSQLInjection() { // GH-90000
            boolean detected = detector.isSuspiciousPrompt( // GH-90000
                "'; DROP TABLE users; --");

            assertThat(detected).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Allow benign prompts [GH-90000]")
        void allowBenignPrompts() { // GH-90000
            boolean detected = detector.isSuspiciousPrompt( // GH-90000
                "Please summarize the following document");

            assertThat(detected).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Detect multiple injection patterns [GH-90000]")
        void detectMultiplePatterns() { // GH-90000
            String[] suspiciousPrompts = {
                "'; DROP TABLE --",
                "SELECT * FROM users",
                "exec sp_executesql",
                "union select",
                "<script>alert('xss')</script>" // GH-90000
            };

            for (String prompt : suspiciousPrompts) { // GH-90000
                boolean detected = detector.isSuspiciousPrompt(prompt); // GH-90000
                assertThat(detected).isTrue(); // GH-90000
            }
        }

        @Test
        @DisplayName("Case-insensitive detection [GH-90000]")
        void caseInsensitiveDetection() { // GH-90000
            boolean lowercase = detector.isSuspiciousPrompt("select * from [GH-90000]");
            boolean uppercase = detector.isSuspiciousPrompt("SELECT * FROM [GH-90000]");
            boolean mixed = detector.isSuspiciousPrompt("SeLeCt * FrOm [GH-90000]");

            assertThat(lowercase).isTrue(); // GH-90000
            assertThat(uppercase).isTrue(); // GH-90000
            assertThat(mixed).isTrue(); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT EGRESS TRACKING (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Egress Tracking [GH-90000]")
    class ConcurrentEgressTests {

        private DefaultEgressMonitor monitor;

        @BeforeEach
        void setUp() { // GH-90000
            monitor = new DefaultEgressMonitor(100_000); // GH-90000
        }

        @Test
        @DisplayName("Many concurrent egress records [GH-90000]")
        void concurrentEgressRecords() throws Exception { // GH-90000
            int threadCount = 30;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < 100; i++) { // GH-90000
                                final int idx = i;
                                runBlocking(() -> monitor.record( // GH-90000
                                    "tenant-" + threadIdx,
                                    "agent-" + idx,
                                    "op-" + idx,
                                    100
                                ));
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }
        }

        @Test
        @DisplayName("Concurrent detection of injection patterns [GH-90000]")
        void concurrentInjectionDetection() throws Exception { // GH-90000
            RegexPromptInjectionDetector detector = new RegexPromptInjectionDetector(); // GH-90000

            String[] prompts = {
                "Please help me",
                "'; DROP TABLE --",
                "Normal question here",
                "SELECT * FROM",
                "What is AI?",
                "exec sp_executesql"
            };

            int threadCount = 25;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger suspiciousCount = new AtomicInteger(0); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < 1000; i++) { // GH-90000
                                String prompt = prompts[i % prompts.length];
                                if (detector.isSuspiciousPrompt(prompt)) { // GH-90000
                                    suspiciousCount.incrementAndGet(); // GH-90000
                                }
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            // Should detect suspicious prompts across all threads
            assertThat(suspiciousCount.get()).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("Mixed egress recording and injection detection [GH-90000]")
        void mixedOperations() throws Exception { // GH-90000
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000

            RegexPromptInjectionDetector detector = new RegexPromptInjectionDetector(); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < 200; i++) { // GH-90000
                                final int idx = i;
                                // Alternate between egress recording and detection
                                if (idx % 2 == 0) { // GH-90000
                                    runBlocking(() -> monitor.record( // GH-90000
                                        "tenant-" + threadIdx,
                                        "agent-" + idx,
                                        "op",
                                        50
                                    ));
                                } else {
                                    String prompt = idx % 3 == 0 ?
                                        "SELECT * FROM users" :
                                        "Normal prompt here";
                                    detector.isSuspiciousPrompt(prompt); // GH-90000
                                }
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }
        }
    }

    // ============================================
    // EDGE CASES (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very large egress amounts at limit boundary [GH-90000]")
        void largeEgressAtBoundary() { // GH-90000
            DefaultEgressMonitor monitor = new DefaultEgressMonitor(10_000); // GH-90000

            // Record up to near limit
            runBlocking(() -> monitor.record("t1", "a1", "op1", 9_500)); // GH-90000

            long total1 = runPromise(() -> monitor.currentWindowBytes("t1", "a1")); // GH-90000
            assertThat(total1).isEqualTo(9_500); // GH-90000

            // Record more at boundary
            runBlocking(() -> monitor.record("t1", "a1", "op2", 500)); // GH-90000

            long total2 = runPromise(() -> monitor.currentWindowBytes("t1", "a1")); // GH-90000
            assertThat(total2).isEqualTo(10_000); // GH-90000
        }

        @Test
        @DisplayName("Very long prompts for injection detection [GH-90000]")
        void veryLongPromptDetection() { // GH-90000
            RegexPromptInjectionDetector detector = new RegexPromptInjectionDetector(); // GH-90000

            String longBenign = "Please explain " + "concepts ".repeat(1000); // GH-90000
            String longSuspicious = "SELECT * FROM " + "users_table ".repeat(1000); // GH-90000

            boolean benignDetected = detector.isSuspiciousPrompt(longBenign); // GH-90000
            boolean suspiciousDetected = detector.isSuspiciousPrompt(longSuspicious); // GH-90000

            assertThat(benignDetected).isFalse(); // GH-90000
            assertThat(suspiciousDetected).isTrue(); // GH-90000
        }
    }
}
