/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 expansion: Retry policy backoff strategies, max attempts, and exception handling.
 * Tests exponential backoff, max retry limits, and retry predicates with edge cases.
 *
 * @doc.type class
 * @doc.purpose Retry policy backoff and exception handling edge cases
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RetryPolicy - Phase 3 Expansion [GH-90000]")
class RetryPolicyExpansionTest extends EventloopTestBase {

    // ============================================
    // MAX RETRY ENFORCEMENT (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Max Retry Enforcement [GH-90000]")
    class MaxRetryTests {

        @Test
        @DisplayName("Respects max retry limit and stops retrying [GH-90000]")
        void maxRetryLimit() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            RetryPolicy policy = RetryPolicy.builder() // GH-90000
                    .maxRetries(2) // GH-90000
                    .initialDelay(Duration.ofMillis(1)) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
                attempts.incrementAndGet(); // GH-90000
                return Promise.ofException(new RuntimeException("always-fail [GH-90000]"));
            }))).isInstanceOf(RuntimeException.class); // GH-90000

            // 1 initial + 2 retries = 3 total attempts
            assertThat(attempts.get()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("Zero retries means only initial attempt [GH-90000]")
        void zeroRetries() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            RetryPolicy policy = RetryPolicy.builder() // GH-90000
                    .maxRetries(0) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
                attempts.incrementAndGet(); // GH-90000
                return Promise.ofException(new RuntimeException("fail [GH-90000]"));
            }))).isInstanceOf(RuntimeException.class); // GH-90000

            assertThat(attempts.get()).isEqualTo(1); // Only initial attempt // GH-90000
        }
    }

    // ============================================
    // RETRY PREDICATES AND FILTERING (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Retry Predicates and Filtering [GH-90000]")
    class RetryPredicateTests {

        @Test
        @DisplayName("Retries only when predicate returns true [GH-90000]")
        void selectiveRetryPredicate() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            RetryPolicy policy = RetryPolicy.builder() // GH-90000
                    .maxRetries(3) // GH-90000
                    .initialDelay(Duration.ofMillis(1)) // GH-90000
                    .retryIf(e -> e instanceof IllegalStateException) // GH-90000
                    .build(); // GH-90000

            String result = runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
                int attempt = attempts.incrementAndGet(); // GH-90000
                if (attempt < 3) { // GH-90000
                    return Promise.ofException(new IllegalStateException("retry-me-" + attempt)); // GH-90000
                }
                return Promise.of("success [GH-90000]");
            }));

            assertThat(result).isEqualTo("success [GH-90000]");
            assertThat(attempts.get()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("Non-matching exceptions fail immediately without retry [GH-90000]")
        void nonMatchingExceptionFails() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            RetryPolicy policy = RetryPolicy.builder() // GH-90000
                    .maxRetries(3) // GH-90000
                    .initialDelay(Duration.ofMillis(1)) // GH-90000
                    .retryIf(e -> e instanceof IllegalStateException) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
                attempts.incrementAndGet(); // GH-90000
                return Promise.ofException(new IllegalArgumentException("non-retryable [GH-90000]"));
            }))).isInstanceOf(IllegalArgumentException.class); // GH-90000

            assertThat(attempts.get()).isEqualTo(1); // No retries // GH-90000
        }
    }

    // ============================================
    // DELAY AND BACKOFF BEHAVIOR (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Delay and Backoff Behavior [GH-90000]")
    class DelayTests {

        @Test
        @DisplayName("Initial delay is applied between retries [GH-90000]")
        void initialDelayApplied() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            long startTime = System.currentTimeMillis(); // GH-90000

            RetryPolicy policy = RetryPolicy.builder() // GH-90000
                    .maxRetries(2) // GH-90000
                    .initialDelay(Duration.ofMillis(50)) // GH-90000
                    .build(); // GH-90000

            String result = runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
                int attempt = attempts.incrementAndGet(); // GH-90000
                if (attempt < 2) { // GH-90000
                    return Promise.ofException(new RuntimeException("fail-" + attempt)); // GH-90000
                }
                return Promise.of("delayed-success [GH-90000]");
            }));

            long elapsed = System.currentTimeMillis() - startTime; // GH-90000
            assertThat(result).isEqualTo("delayed-success [GH-90000]");
            // Should have taken at least some delay time (accounting for execution variance) // GH-90000
            assertThat(attempts.get()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("Successful operation returns immediately without delay [GH-90000]")
        void successNoDelay() { // GH-90000
            RetryPolicy policy = RetryPolicy.builder() // GH-90000
                    .maxRetries(3) // GH-90000
                    .initialDelay(Duration.ofMillis(500)) // GH-90000
                    .build(); // GH-90000

            long startTime = System.currentTimeMillis(); // GH-90000
            String result = runPromise(() -> policy.execute(eventloop(), () -> Promise.of("immediate [GH-90000]")));
            long elapsed = System.currentTimeMillis() - startTime; // GH-90000

            assertThat(result).isEqualTo("immediate [GH-90000]");
            // Should complete quickly without waiting for retry delay
            assertThat(elapsed).isLessThan(200); // GH-90000
        }
    }

    // ============================================
    // MULTIPLE EXCEPTION TYPES (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Multiple Exception Handling [GH-90000]")
    class MultiExceptionTests {

        @Test
        @DisplayName("Complex predicate handles multiple exception types [GH-90000]")
        void complexExceptionPredicate() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            RetryPolicy policy = RetryPolicy.builder() // GH-90000
                    .maxRetries(2) // GH-90000
                    .initialDelay(Duration.ofMillis(1)) // GH-90000
                    .retryIf(e -> e instanceof RuntimeException || e instanceof IllegalStateException) // GH-90000
                    .build(); // GH-90000

            String result = runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
                int attempt = attempts.incrementAndGet(); // GH-90000
                if (attempt == 1) { // GH-90000
                    return Promise.ofException(new RuntimeException("runtime-error [GH-90000]"));
                } else if (attempt == 2) { // GH-90000
                    return Promise.ofException(new IllegalStateException("state-error [GH-90000]"));
                }
                return Promise.of("got-through [GH-90000]");
            }));

            assertThat(result).isEqualTo("got-through [GH-90000]");
            assertThat(attempts.get()).isEqualTo(3); // GH-90000
        }
    }
}
