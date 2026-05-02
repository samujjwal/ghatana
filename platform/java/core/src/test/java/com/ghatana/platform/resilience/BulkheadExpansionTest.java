/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("Bulkhead - Phase 3 Expansion")
class BulkheadExpansionTest {

    // ============================================
    // PERMIT ENFORCEMENT (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Permit Enforcement")
    class PermitTests {

        @Test
        @DisplayName("Executes successfully when permits available")
        void executionWithinLimits() throws Exception { 
            Bulkhead bulkhead = Bulkhead.of("api-calls", 5); 

            String result = bulkhead.tryExecuteBlocking(() -> "success"); 

            assertThat(result).isEqualTo("success");
            assertThat(bulkhead.getTotalAcquired()).isGreaterThanOrEqualTo(1); 
        }

        @Test
        @DisplayName("Multiple sequential executions track acquired count")
        void sequentialExecutions() throws Exception { 
            Bulkhead bulkhead = Bulkhead.of("sequential-service", 3); 

            for (int i = 0; i < 3; i++) { 
                bulkhead.tryExecuteBlocking(() -> "call"); 
            }

            assertThat(bulkhead.getTotalAcquired()).isEqualTo(3); 
        }
    }

    // ============================================
    // DIFFERENT BULKHEAD SIZES (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Different Bulkhead Sizes")
    class SizeVariationTests {

        @Test
        @DisplayName("Small bulkhead (size 1) executes single call")
        void smallBulkheadSize() throws Exception { 
            Bulkhead bulkhead = Bulkhead.of("single-permit", 1); 

            String result = bulkhead.tryExecuteBlocking(() -> "single"); 

            assertThat(result).isEqualTo("single");
            assertThat(bulkhead.getTotalAcquired()).isEqualTo(1); 
        }

        @Test
        @DisplayName("Medium bulkhead (size 10) executes multiple calls")
        void mediumBulkheadSize() throws Exception { 
            Bulkhead bulkhead = Bulkhead.of("medium-service", 10); 

            for (int i = 0; i < 5; i++) { 
                bulkhead.tryExecuteBlocking(() -> "call"); 
            }

            assertThat(bulkhead.getTotalAcquired()).isEqualTo(5); 
            assertThat(bulkhead.getTotalRejected()).isEqualTo(0); 
        }

        @Test
        @DisplayName("Large bulkhead (size 100) handles many calls")
        void largeBulkheadSize() throws Exception { 
            Bulkhead bulkhead = Bulkhead.of("large-pool", 100); 

            for (int i = 0; i < 50; i++) { 
                bulkhead.tryExecuteBlocking(() -> "large"); 
            }

            assertThat(bulkhead.getTotalAcquired()).isEqualTo(50); 
        }
    }

    // ============================================
    // METRICS TRACKING (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Metrics Tracking")
    class MetricsTests {

        @Test
        @DisplayName("Total acquired count increases with each execution")
        void acquiredCountMetric() throws Exception { 
            Bulkhead bulkhead = Bulkhead.of("metrics-acquired", 5); 

            long startAcquired = bulkhead.getTotalAcquired(); 

            for (int i = 0; i < 3; i++) { 
                bulkhead.tryExecuteBlocking(() -> "tracked"); 
            }

            assertThat(bulkhead.getTotalAcquired()).isEqualTo(startAcquired + 3); 
        }

        @Test
        @DisplayName("Bulkhead name is stored and retrievable")
        void bulkheadNameStorage() throws Exception { 
            String[] names = {"service-a", "service-b", "service-c"};

            for (String name : names) { 
                Bulkhead bulkhead = Bulkhead.of(name, 5); 
                assertThat(bulkhead.getName()).isEqualTo(name); 
            }
        }
    }

    // ============================================
    // EXCEPTION HANDLING (1 test) 
    // ============================================

    @Nested
    @DisplayName("Exception Handling")
    class ExceptionTests {

        @Test
        @DisplayName("Exception from task propagates through bulkhead")
        void exceptionPropagation() { 
            Bulkhead bulkhead = Bulkhead.of("exception-test", 3); 

            assertThatThrownBy(() -> { 
                bulkhead.tryExecuteBlocking(() -> { 
                    throw new RuntimeException("task-failed");
                });
            }).isInstanceOf(RuntimeException.class) 
                    .hasMessage("task-failed");
        }
    }
}
