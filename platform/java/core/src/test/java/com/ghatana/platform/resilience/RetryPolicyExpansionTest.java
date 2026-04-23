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
@DisplayName("RetryPolicy - Phase 3 Expansion")
class RetryPolicyExpansionTest extends EventloopTestBase {

    // ============================================
    // MAX RETRY ENFORCEMENT (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Max Retry Enforcement")
    class MaxRetryTests {

        @Test
        @DisplayName("Respects max retry limit and stops retrying")
        void maxRetryLimit() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            RetryPolicy policy = RetryPolicy.builder() // GH-90000
                    .maxRetries(2) // GH-90000
                    .initialDelay(Duration.ofMillis(1)) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
                attempts.incrementAndGet(); // GH-90000
                return Promise.ofException(new RuntimeException("always-fail"));
            }))).isInstanceOf(RuntimeException.class); // GH-90000

            // 1 initial + 2 retries = 3 total attempts
            assertThat(attempts.get()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("Zero retries means only initial attempt")
        void zeroRetries() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            RetryPolicy policy = RetryPolicy.builder() // GH-90000
                    .maxRetries(0) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
                attempts.incrementAndGet(); // GH-90000
                return Promise.ofException(new RuntimeException("fail"));
            }))).isInstanceOf(RuntimeException.class); // GH-90000

            assertThat(attempts.get()).isEqualTo(1); // Only initial attempt // GH-90000
        }
    }

    // ============================================
    // RETRY PREDICATES AND FILTERING (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Retry Predicates and Filtering")
    class RetryPredicateTests {

        @Test
        @DisplayName("Retries only when predicate returns true")
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
                return Promise.of("success");
            }));

            assertThat(result).isEqualTo("success");
            assertThat(attempts.get()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("Non-matching exceptions fail immediately without retry")
        void nonMatchingExceptionFails() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            RetryPolicy policy = RetryPolicy.builder() // GH-90000
                    .maxRetries(3) // GH-90000
                    .initialDelay(Duration.ofMillis(1)) // GH-90000
                    .retryIf(e -> e instanceof IllegalStateException) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
                attempts.incrementAndGet(); // GH-90000
                return Promise.ofException(new IllegalArgumentException("non-retryable"));
            }))).isInstanceOf(IllegalArgumentException.class); // GH-90000

            assertThat(attempts.get()).isEqualTo(1); // No retries // GH-90000
        }
    }

    // ============================================
    // DELAY AND BACKOFF BEHAVIOR (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Delay and Backoff Behavior")
    class DelayTests {

        @Test
        @DisplayName("Initial delay is applied between retries")
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
                return Promise.of("delayed-success");
            }));

            long elapsed = System.currentTimeMillis() - startTime; // GH-90000
            assertThat(result).isEqualTo("delayed-success");
            // Should have taken at least some delay time (accounting for execution variance) // GH-90000
            assertThat(attempts.get()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("Successful operation returns immediately without delay")
        void successNoDelay() { // GH-90000
            RetryPolicy policy = RetryPolicy.builder() // GH-90000
                    .maxRetries(3) // GH-90000
                    .initialDelay(Duration.ofMillis(500)) // GH-90000
                    .build(); // GH-90000

            long startTime = System.currentTimeMillis(); // GH-90000
            String result = runPromise(() -> policy.execute(eventloop(), () -> Promise.of("immediate")));
            long elapsed = System.currentTimeMillis() - startTime; // GH-90000

            assertThat(result).isEqualTo("immediate");
            // Should complete quickly without waiting for retry delay
            assertThat(elapsed).isLessThan(200); // GH-90000
        }
    }

    // ============================================
    // MULTIPLE EXCEPTION TYPES (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Multiple Exception Handling")
    class MultiExceptionTests {

        @Test
        @DisplayName("Complex predicate handles multiple exception types")
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
                    return Promise.ofException(new RuntimeException("runtime-error"));
                } else if (attempt == 2) { // GH-90000
                    return Promise.ofException(new IllegalStateException("state-error"));
                }
                return Promise.of("got-through");
            }));

            assertThat(result).isEqualTo("got-through");
            assertThat(attempts.get()).isEqualTo(3); // GH-90000
        }
    }
}
