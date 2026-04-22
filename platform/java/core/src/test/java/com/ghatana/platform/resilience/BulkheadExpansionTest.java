/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 expansion: Bulkhead concurrency limits and rejection behavior.
 * Tests permit enforcement, sequential execution, and monitoring metrics.
 *
 * @doc.type class
 * @doc.purpose Bulkhead concurrency limits and rejection behavior
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Bulkhead - Phase 3 Expansion [GH-90000]")
class BulkheadExpansionTest {

    // ============================================
    // PERMIT ENFORCEMENT (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Permit Enforcement [GH-90000]")
    class PermitTests {

        @Test
        @DisplayName("Executes successfully when permits available [GH-90000]")
        void executionWithinLimits() throws Exception { // GH-90000
            Bulkhead bulkhead = Bulkhead.of("api-calls", 5); // GH-90000

            String result = bulkhead.tryExecuteBlocking(() -> "success"); // GH-90000

            assertThat(result).isEqualTo("success [GH-90000]");
            assertThat(bulkhead.getTotalAcquired()).isGreaterThanOrEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("Multiple sequential executions track acquired count [GH-90000]")
        void sequentialExecutions() throws Exception { // GH-90000
            Bulkhead bulkhead = Bulkhead.of("sequential-service", 3); // GH-90000

            for (int i = 0; i < 3; i++) { // GH-90000
                bulkhead.tryExecuteBlocking(() -> "call"); // GH-90000
            }

            assertThat(bulkhead.getTotalAcquired()).isEqualTo(3); // GH-90000
        }
    }

    // ============================================
    // DIFFERENT BULKHEAD SIZES (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Different Bulkhead Sizes [GH-90000]")
    class SizeVariationTests {

        @Test
        @DisplayName("Small bulkhead (size 1) executes single call [GH-90000]")
        void smallBulkheadSize() throws Exception { // GH-90000
            Bulkhead bulkhead = Bulkhead.of("single-permit", 1); // GH-90000

            String result = bulkhead.tryExecuteBlocking(() -> "single"); // GH-90000

            assertThat(result).isEqualTo("single [GH-90000]");
            assertThat(bulkhead.getTotalAcquired()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("Medium bulkhead (size 10) executes multiple calls [GH-90000]")
        void mediumBulkheadSize() throws Exception { // GH-90000
            Bulkhead bulkhead = Bulkhead.of("medium-service", 10); // GH-90000

            for (int i = 0; i < 5; i++) { // GH-90000
                bulkhead.tryExecuteBlocking(() -> "call"); // GH-90000
            }

            assertThat(bulkhead.getTotalAcquired()).isEqualTo(5); // GH-90000
            assertThat(bulkhead.getTotalRejected()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("Large bulkhead (size 100) handles many calls [GH-90000]")
        void largeBulkheadSize() throws Exception { // GH-90000
            Bulkhead bulkhead = Bulkhead.of("large-pool", 100); // GH-90000

            for (int i = 0; i < 50; i++) { // GH-90000
                bulkhead.tryExecuteBlocking(() -> "large"); // GH-90000
            }

            assertThat(bulkhead.getTotalAcquired()).isEqualTo(50); // GH-90000
        }
    }

    // ============================================
    // METRICS TRACKING (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Metrics Tracking [GH-90000]")
    class MetricsTests {

        @Test
        @DisplayName("Total acquired count increases with each execution [GH-90000]")
        void acquiredCountMetric() throws Exception { // GH-90000
            Bulkhead bulkhead = Bulkhead.of("metrics-acquired", 5); // GH-90000

            long startAcquired = bulkhead.getTotalAcquired(); // GH-90000

            for (int i = 0; i < 3; i++) { // GH-90000
                bulkhead.tryExecuteBlocking(() -> "tracked"); // GH-90000
            }

            assertThat(bulkhead.getTotalAcquired()).isEqualTo(startAcquired + 3); // GH-90000
        }

        @Test
        @DisplayName("Bulkhead name is stored and retrievable [GH-90000]")
        void bulkheadNameStorage() throws Exception { // GH-90000
            String[] names = {"service-a", "service-b", "service-c"};

            for (String name : names) { // GH-90000
                Bulkhead bulkhead = Bulkhead.of(name, 5); // GH-90000
                assertThat(bulkhead.getName()).isEqualTo(name); // GH-90000
            }
        }
    }

    // ============================================
    // EXCEPTION HANDLING (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Exception Handling [GH-90000]")
    class ExceptionTests {

        @Test
        @DisplayName("Exception from task propagates through bulkhead [GH-90000]")
        void exceptionPropagation() { // GH-90000
            Bulkhead bulkhead = Bulkhead.of("exception-test", 3); // GH-90000

            assertThatThrownBy(() -> { // GH-90000
                bulkhead.tryExecuteBlocking(() -> { // GH-90000
                    throw new RuntimeException("task-failed [GH-90000]");
                });
            }).isInstanceOf(RuntimeException.class) // GH-90000
                    .hasMessage("task-failed [GH-90000]");
        }
    }
}
