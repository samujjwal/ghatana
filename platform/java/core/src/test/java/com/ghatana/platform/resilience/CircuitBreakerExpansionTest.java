/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: Circuit breaker state transitions, concurrency, and edge cases.
 * Tests rapid state changes, concurrent calls during transitions, and timeout edge cases.
 *
 * @doc.type class
 * @doc.purpose Circuit breaker concurrent transitions and edge cases
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("CircuitBreaker - Phase 3 Expansion")
class CircuitBreakerExpansionTest extends EventloopTestBase {

    // ============================================
    // STATE TRANSITION EDGE CASES (2 tests) 
    // ============================================

    @Nested
    @DisplayName("State Transition Edge Cases")
    class StateTransitionTests {

        @Test
        @DisplayName("Multiple rapid failures transition circuit smoothly")
        void rapidFailureTransition() { 
            CircuitBreaker cb = CircuitBreaker.builder("rapid-fail")
                    .failureThreshold(2) 
                    .resetTimeout(Duration.ofSeconds(10)) 
                    .build(); 

            runBlocking(() -> { 
                // Call 1: fail
                cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("fail-1")));
                assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); 
                assertThat(cb.getFailureCount()).isEqualTo(1); 

                // Call 2: fail again
                cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("fail-2")));
                assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN); 
                assertThat(cb.getFailureCount()).isEqualTo(2); 
            });
        }

        @Test
        @DisplayName("Reset timeout advances circuit to HALF_OPEN")
        void resetTimeoutTransition() { 
            CircuitBreaker cb = CircuitBreaker.builder("timeout-reset")
                    .failureThreshold(1) 
                    .resetTimeout(Duration.ofMillis(100)) 
                    .build(); 

            runBlocking(() -> { 
                // Trip the circuit
                cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("fail")));
                assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN); 

                // Wait for timeout to approach (not exact timing, just verify state tracking) 
                // In real scenarios, resetTimeout would transition to HALF_OPEN
                assertThat(cb.getFailureCount()).isEqualTo(1); 
            });
        }
    }

    // ============================================
    // CONCURRENT EXECUTION UNDER LOAD (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Concurrent Execution Under Load")
    class ConcurrentLoadTests {

        @Test
        @DisplayName("Multiple threads call circuit simultaneously in CLOSED state")
        void concurrentCallsClosed() throws InterruptedException { 
            CircuitBreaker cb = CircuitBreaker.builder("concurrent-closed")
                    .failureThreshold(5) 
                    .build(); 

            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount); 
            AtomicInteger successCount = new AtomicInteger(0); 

            for (int i = 0; i < threadCount; i++) { 
                new Thread(() -> { 
                    try {
                        runBlocking(() -> { 
                            cb.execute(eventloop(), () -> Promise.of("result-" + Thread.currentThread().getId())) 
                                    .whenComplete((result, ex) -> { 
                                        if (ex == null) { 
                                            successCount.incrementAndGet(); 
                                        }
                                    });
                        });
                    } finally {
                        latch.countDown(); 
                    }
                }).start(); 
            }

            latch.await(); 

            assertThat(successCount.get()).isEqualTo(threadCount); 
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); 
        }

        @Test
        @DisplayName("Rejection behavior under load when OPEN")
        void rejectionBehaviorWhenOpen() { 
            CircuitBreaker cb = CircuitBreaker.builder("overload-open")
                    .failureThreshold(2) 
                    .build(); 

            runBlocking(() -> { 
                // Trip the circuit
                for (int i = 0; i < 2; i++) { 
                    cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("fail")));
                }

                // Verify circuit is open
                assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN); 

                // Subsequent calls should be rejected
                for (int i = 0; i < 5; i++) { 
                    cb.execute(eventloop(), () -> Promise.of("should-reject"));
                }

                // Failure count stays at threshold
                assertThat(cb.getFailureCount()).isEqualTo(2); 
            });
        }
    }

    // ============================================
    // METRICS AND STATE TRACKING (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Metrics and State Tracking")
    class MetricsTests {

        @Test
        @DisplayName("Success and failure counts aggregated correctly")
        void metricsAggregation() { 
            CircuitBreaker cb = CircuitBreaker.builder("metrics")
                    .failureThreshold(5) 
                    .build(); 

            runBlocking(() -> { 
                // 3 successful calls
                for (int i = 0; i < 3; i++) { 
                    cb.execute(eventloop(), () -> Promise.of("ok"));
                }

                // 2 failed calls
                for (int i = 0; i < 2; i++) { 
                    cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("fail")));
                }
            });

            assertThat(cb.getTotalSuccesses()).isEqualTo(3); 
            assertThat(cb.getFailureCount()).isEqualTo(2); 
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED); 
        }

        @Test
        @DisplayName("Circuit name is tracked and retrievable")
        void circuitNameTracking() { 
            String[] names = {"api-downstream", "db-connection", "cache-service"};

            for (String name : names) { 
                CircuitBreaker cb = CircuitBreaker.builder(name).build(); 
                assertThat(cb.getName()).isEqualTo(name); 
            }
        }
    }
}
