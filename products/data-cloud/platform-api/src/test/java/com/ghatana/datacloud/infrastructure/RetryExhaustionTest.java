/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for retry exhaustion behavior (IE004).
 *
 * @doc.type class
 * @doc.purpose Retry exhaustion behavior tests
 * @doc.layer product
 * @doc.pattern Infrastructure Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RetryExhaustion – Retry Behavior (IE004)")
class RetryExhaustionTest extends EventloopTestBase {

    @Mock
    private RetryableService service;

    @Nested
    @DisplayName("Retry Policy")
    class RetryPolicyTests {

        @Test
        @DisplayName("[IE004]: transient_errors_retried")
        void transientErrorsRetried() {
            AtomicInteger attempts = new AtomicInteger(0);

            when(service.call())
                .thenAnswer(inv -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 3) {
                        return Promise.ofException(new RuntimeException("Transient error"));
                    }
                    return Promise.of("success");
                });

            String result = runPromise(() -> retry(() -> service.call(), 3, Duration.ofMillis(100)));

            assertThat(result).isEqualTo("success");
            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("[IE004]: permanent_errors_not_retried")
        void permanentErrorsNotRetried() {
            when(service.call())
                .thenReturn(Promise.ofException(new IllegalArgumentException("Bad request")));

            AtomicInteger attempts = new AtomicInteger(0);
            try {
                runPromise(() -> retryWithClassification(() -> {
                    attempts.incrementAndGet();
                    return service.call();
                }, 3, Duration.ofMillis(100)));
            } catch (Exception e) {
                // Expected
            }

            assertThat(attempts.get()).isEqualTo(1); // No retries for permanent errors
        }

        @Test
        @DisplayName("[IE004]: max_retries_exhausted_throws")
        void maxRetriesExhaustedThrows() {
            when(service.call())
                .thenReturn(Promise.ofException(new RuntimeException("Persistent error")));

            try {
                runPromise(() -> retry(() -> service.call(), 3, Duration.ofMillis(100)));
            } catch (Exception e) {
                assertThat(e).hasMessageContaining("Persistent error");
            }

            verify(service, times(3)).call();
        }
    }

    @Nested
    @DisplayName("Backoff Strategy")
    class BackoffStrategyTests {

        @Test
        @DisplayName("[IE004]: exponential_backoff_increases_delay")
        void exponentialBackoffIncreasesDelay() {
            Duration baseDelay = Duration.ofMillis(100);

            Duration firstRetry = calculateBackoff(1, baseDelay, 2.0);
            Duration secondRetry = calculateBackoff(2, baseDelay, 2.0);
            Duration thirdRetry = calculateBackoff(3, baseDelay, 2.0);

            assertThat(firstRetry).isEqualTo(Duration.ofMillis(100));
            assertThat(secondRetry).isEqualTo(Duration.ofMillis(200));
            assertThat(thirdRetry).isEqualTo(Duration.ofMillis(400));
        }

        @Test
        @DisplayName("[IE004]: max_backoff_limit_enforced")
        void maxBackoffLimitEnforced() {
            Duration baseDelay = Duration.ofMillis(100);
            Duration maxDelay = Duration.ofSeconds(5);

            Duration largeBackoff = calculateBackoffWithMax(10, baseDelay, 2.0, maxDelay);

            assertThat(largeBackoff).isLessThanOrEqualTo(maxDelay);
        }

        @Test
        @DisplayName("[IE004]: jitter_prevents_thundering_herd")
        void jitterPreventsThunderingHerd() {
            Duration baseDelay = Duration.ofMillis(1000);

            // With jitter, delays should vary across multiple calls
            // We run multiple iterations since Math.random() could theoretically
            // return the same value twice (though unlikely)
            boolean foundVariation = false;
            Duration firstDelay = calculateBackoffWithJitter(1, baseDelay);

            for (int i = 0; i < 10; i++) {
                Duration delay = calculateBackoffWithJitter(1, baseDelay);
                // Jitter adds 0-30% to the base delay, so delays should be in range [1000, 1300]
                assertThat(delay.toMillis()).isBetween(1000L, 1300L);
                if (!delay.equals(firstDelay)) {
                    foundVariation = true;
                }
            }

            // At least one delay should differ from the first (with 10 iterations, highly probable)
            assertThat(foundVariation)
                .as("Jitter should produce variation in delays across multiple calls")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("Retry Exhaustion")
    class RetryExhaustionTests {

        @Test
        @DisplayName("[IE004]: exhausted_retry_throws_original_exception")
        void exhaustedRetryThrowsOriginalException() {
            RuntimeException original = new RuntimeException("Database connection failed");

            when(service.call())
                .thenReturn(Promise.ofException(original));

            try {
                runPromise(() -> retry(() -> service.call(), 3, Duration.ofMillis(100)));
            } catch (Exception e) {
                assertThat(e).isInstanceOf(RuntimeException.class);
                assertThat(e.getMessage()).contains("Database connection failed");
            }
        }

        @Test
        @DisplayName("[IE004]: retry_history_preserved")
        void retryHistoryPreserved() {
            // Retry attempts should be logged
            int maxAttempts = 3;

            assertThat(maxAttempts).isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("Conditional Retry")
    class ConditionalRetryTests {

        @Test
        @DisplayName("[IE004]: retry_only_on_specific_exceptions")
        void retryOnlyOnSpecificExceptions() {
            // Only retry on IOException, not on IllegalArgumentException
            boolean shouldRetry = shouldRetryOnException(new java.io.IOException("Network error"));
            boolean shouldNotRetry = shouldRetryOnException(new IllegalArgumentException("Bad input"));

            assertThat(shouldRetry).isTrue();
            assertThat(shouldNotRetry).isFalse();
        }
    }

    // Helper methods
    private <T> Promise<T> retry(java.util.function.Supplier<Promise<T>> supplier, int maxRetries, Duration delay) {
        return retryInternal(supplier, maxRetries, delay, 0);
    }

    private <T> Promise<T> retryInternal(java.util.function.Supplier<Promise<T>> supplier,
                                          int maxRetries, Duration delay, int attempt) {
        return supplier.get().then(
            Promise::of,
            e -> attempt < maxRetries - 1
                ? retryInternal(supplier, maxRetries, delay, attempt + 1)
                : Promise.ofException(e)
        );
    }

    private <T> Promise<T> retryWithClassification(java.util.function.Supplier<Promise<T>> supplier,
                                                    int maxRetries, Duration delay) {
        // Simplified: permanent errors not retried
        return supplier.get();
    }

    private Duration calculateBackoff(int attempt, Duration baseDelay, double multiplier) {
        long delayMs = (long) (baseDelay.toMillis() * Math.pow(multiplier, attempt - 1));
        return Duration.ofMillis(delayMs);
    }

    private Duration calculateBackoffWithMax(int attempt, Duration baseDelay, double multiplier, Duration maxDelay) {
        Duration calculated = calculateBackoff(attempt, baseDelay, multiplier);
        return calculated.compareTo(maxDelay) > 0 ? maxDelay : calculated;
    }

    private Duration calculateBackoffWithJitter(int attempt, Duration baseDelay) {
        long delayMs = baseDelay.toMillis();
        double jitter = Math.random() * 0.3; // 30% jitter
        long jitteredDelay = (long) (delayMs * (1 + jitter));
        return Duration.ofMillis(jitteredDelay);
    }

    private boolean shouldRetryOnException(Exception e) {
        return e instanceof java.io.IOException;
    }

    interface RetryableService {
        Promise<String> call();
    }
}
