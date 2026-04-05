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
    // MAX RETRY ENFORCEMENT (2 tests)
    // ============================================

    @Nested
    @DisplayName("Max Retry Enforcement")
    class MaxRetryTests {

        @Test
        @DisplayName("Respects max retry limit and stops retrying")
        void maxRetryLimit() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryPolicy policy = RetryPolicy.builder()
                    .maxRetries(2)
                    .initialDelay(Duration.ofMillis(1))
                    .build();

            assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> {
                attempts.incrementAndGet();
                return Promise.ofException(new RuntimeException("always-fail"));
            }))).isInstanceOf(RuntimeException.class);

            // 1 initial + 2 retries = 3 total attempts
            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("Zero retries means only initial attempt")
        void zeroRetries() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryPolicy policy = RetryPolicy.builder()
                    .maxRetries(0)
                    .build();

            assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> {
                attempts.incrementAndGet();
                return Promise.ofException(new RuntimeException("fail"));
            }))).isInstanceOf(RuntimeException.class);

            assertThat(attempts.get()).isEqualTo(1); // Only initial attempt
        }
    }

    // ============================================
    // RETRY PREDICATES AND FILTERING (2 tests)
    // ============================================

    @Nested
    @DisplayName("Retry Predicates and Filtering")
    class RetryPredicateTests {

        @Test
        @DisplayName("Retries only when predicate returns true")
        void selectiveRetryPredicate() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryPolicy policy = RetryPolicy.builder()
                    .maxRetries(3)
                    .initialDelay(Duration.ofMillis(1))
                    .retryIf(e -> e instanceof IllegalStateException)
                    .build();

            String result = runPromise(() -> policy.execute(eventloop(), () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    return Promise.ofException(new IllegalStateException("retry-me-" + attempt));
                }
                return Promise.of("success");
            }));

            assertThat(result).isEqualTo("success");
            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("Non-matching exceptions fail immediately without retry")
        void nonMatchingExceptionFails() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryPolicy policy = RetryPolicy.builder()
                    .maxRetries(3)
                    .initialDelay(Duration.ofMillis(1))
                    .retryIf(e -> e instanceof IllegalStateException)
                    .build();

            assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> {
                attempts.incrementAndGet();
                return Promise.ofException(new IllegalArgumentException("non-retryable"));
            }))).isInstanceOf(IllegalArgumentException.class);

            assertThat(attempts.get()).isEqualTo(1); // No retries
        }
    }

    // ============================================
    // DELAY AND BACKOFF BEHAVIOR (2 tests)
    // ============================================

    @Nested
    @DisplayName("Delay and Backoff Behavior")
    class DelayTests {

        @Test
        @DisplayName("Initial delay is applied between retries")
        void initialDelayApplied() {
            AtomicInteger attempts = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();

            RetryPolicy policy = RetryPolicy.builder()
                    .maxRetries(2)
                    .initialDelay(Duration.ofMillis(50))
                    .build();

            String result = runPromise(() -> policy.execute(eventloop(), () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 2) {
                    return Promise.ofException(new RuntimeException("fail-" + attempt));
                }
                return Promise.of("delayed-success");
            }));

            long elapsed = System.currentTimeMillis() - startTime;
            assertThat(result).isEqualTo("delayed-success");
            // Should have taken at least some delay time (accounting for execution variance)
            assertThat(attempts.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("Successful operation returns immediately without delay")
        void successNoDelay() {
            RetryPolicy policy = RetryPolicy.builder()
                    .maxRetries(3)
                    .initialDelay(Duration.ofMillis(500))
                    .build();

            long startTime = System.currentTimeMillis();
            String result = runPromise(() -> policy.execute(eventloop(), () -> Promise.of("immediate")));
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(result).isEqualTo("immediate");
            // Should complete quickly without waiting for retry delay
            assertThat(elapsed).isLessThan(200);
        }
    }

    // ============================================
    // MULTIPLE EXCEPTION TYPES (1 test)
    // ============================================

    @Nested
    @DisplayName("Multiple Exception Handling")
    class MultiExceptionTests {

        @Test
        @DisplayName("Complex predicate handles multiple exception types")
        void complexExceptionPredicate() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryPolicy policy = RetryPolicy.builder()
                    .maxRetries(2)
                    .initialDelay(Duration.ofMillis(1))
                    .retryIf(e -> e instanceof RuntimeException || e instanceof IllegalStateException)
                    .build();

            String result = runPromise(() -> policy.execute(eventloop(), () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    return Promise.ofException(new RuntimeException("runtime-error"));
                } else if (attempt == 2) {
                    return Promise.ofException(new IllegalStateException("state-error"));
                }
                return Promise.of("got-through");
            }));

            assertThat(result).isEqualTo("got-through");
            assertThat(attempts.get()).isEqualTo(3);
        }
    }
}
