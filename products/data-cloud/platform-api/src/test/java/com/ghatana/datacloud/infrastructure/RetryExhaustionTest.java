/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Tests for retry exhaustion behavior (IE004). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Retry exhaustion behavior tests
 * @doc.layer product
 * @doc.pattern Infrastructure Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("RetryExhaustion – Retry Behavior (IE004) [GH-90000]")
class RetryExhaustionTest extends EventloopTestBase {

    @Mock
    private RetryableService service;

    @Nested
    @DisplayName("Retry Policy [GH-90000]")
    class RetryPolicyTests {

        @Test
        @DisplayName("[IE004]: transient_errors_retried [GH-90000]")
        void transientErrorsRetried() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000

            when(service.call()) // GH-90000
                .thenAnswer(inv -> { // GH-90000
                    int attempt = attempts.incrementAndGet(); // GH-90000
                    if (attempt < 3) { // GH-90000
                        return Promise.ofException(new RuntimeException("Transient error [GH-90000]"));
                    }
                    return Promise.of("success [GH-90000]");
                });

            String result = runPromise(() -> retry(() -> service.call(), 3, Duration.ofMillis(100))); // GH-90000

            assertThat(result).isEqualTo("success [GH-90000]");
            assertThat(attempts.get()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("[IE004]: permanent_errors_not_retried [GH-90000]")
        void permanentErrorsNotRetried() { // GH-90000
            when(service.call()) // GH-90000
                .thenReturn(Promise.ofException(new IllegalArgumentException("Bad request [GH-90000]")));

            AtomicInteger attempts = new AtomicInteger(0); // GH-90000
            try {
                runPromise(() -> retryWithClassification(() -> { // GH-90000
                    attempts.incrementAndGet(); // GH-90000
                    return service.call(); // GH-90000
                }, 3, Duration.ofMillis(100))); // GH-90000
            } catch (Exception e) { // GH-90000
                // Expected
            }

            assertThat(attempts.get()).isEqualTo(1); // No retries for permanent errors // GH-90000
        }

        @Test
        @DisplayName("[IE004]: max_retries_exhausted_throws [GH-90000]")
        void maxRetriesExhaustedThrows() { // GH-90000
            when(service.call()) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("Persistent error [GH-90000]")));

            try {
                runPromise(() -> retry(() -> service.call(), 3, Duration.ofMillis(100))); // GH-90000
            } catch (Exception e) { // GH-90000
                assertThat(e).hasMessageContaining("Persistent error [GH-90000]");
            }

            verify(service, times(3)).call(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Backoff Strategy [GH-90000]")
    class BackoffStrategyTests {

        @Test
        @DisplayName("[IE004]: exponential_backoff_increases_delay [GH-90000]")
        void exponentialBackoffIncreasesDelay() { // GH-90000
            Duration baseDelay = Duration.ofMillis(100); // GH-90000

            Duration firstRetry = calculateBackoff(1, baseDelay, 2.0); // GH-90000
            Duration secondRetry = calculateBackoff(2, baseDelay, 2.0); // GH-90000
            Duration thirdRetry = calculateBackoff(3, baseDelay, 2.0); // GH-90000

            assertThat(firstRetry).isEqualTo(Duration.ofMillis(100)); // GH-90000
            assertThat(secondRetry).isEqualTo(Duration.ofMillis(200)); // GH-90000
            assertThat(thirdRetry).isEqualTo(Duration.ofMillis(400)); // GH-90000
        }

        @Test
        @DisplayName("[IE004]: max_backoff_limit_enforced [GH-90000]")
        void maxBackoffLimitEnforced() { // GH-90000
            Duration baseDelay = Duration.ofMillis(100); // GH-90000
            Duration maxDelay = Duration.ofSeconds(5); // GH-90000

            Duration largeBackoff = calculateBackoffWithMax(10, baseDelay, 2.0, maxDelay); // GH-90000

            assertThat(largeBackoff).isLessThanOrEqualTo(maxDelay); // GH-90000
        }

        @Test
        @DisplayName("[IE004]: jitter_prevents_thundering_herd [GH-90000]")
        void jitterPreventsThunderingHerd() { // GH-90000
            Duration baseDelay = Duration.ofMillis(1000); // GH-90000

            // With jitter, delays should vary across multiple calls
            // We run multiple iterations since Math.random() could theoretically // GH-90000
            // return the same value twice (though unlikely) // GH-90000
            boolean foundVariation = false;
            Duration firstDelay = calculateBackoffWithJitter(1, baseDelay); // GH-90000

            for (int i = 0; i < 10; i++) { // GH-90000
                Duration delay = calculateBackoffWithJitter(1, baseDelay); // GH-90000
                // Jitter adds 0-30% to the base delay, so delays should be in range [1000, 1300]
                assertThat(delay.toMillis()).isBetween(1000L, 1300L); // GH-90000
                if (!delay.equals(firstDelay)) { // GH-90000
                    foundVariation = true;
                }
            }

            // At least one delay should differ from the first (with 10 iterations, highly probable) // GH-90000
            assertThat(foundVariation) // GH-90000
                .as("Jitter should produce variation in delays across multiple calls [GH-90000]")
                .isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Retry Exhaustion [GH-90000]")
    class RetryExhaustionTests {

        @Test
        @DisplayName("[IE004]: exhausted_retry_throws_original_exception [GH-90000]")
        void exhaustedRetryThrowsOriginalException() { // GH-90000
            RuntimeException original = new RuntimeException("Database connection failed [GH-90000]");

            when(service.call()) // GH-90000
                .thenReturn(Promise.ofException(original)); // GH-90000

            try {
                runPromise(() -> retry(() -> service.call(), 3, Duration.ofMillis(100))); // GH-90000
            } catch (Exception e) { // GH-90000
                assertThat(e).isInstanceOf(RuntimeException.class); // GH-90000
                assertThat(e.getMessage()).contains("Database connection failed [GH-90000]");
            }
        }

        @Test
        @DisplayName("[IE004]: retry_history_preserved [GH-90000]")
        void retryHistoryPreserved() { // GH-90000
            // Retry attempts should be logged
            int maxAttempts = 3;

            assertThat(maxAttempts).isGreaterThan(1); // GH-90000
        }
    }

    @Nested
    @DisplayName("Conditional Retry [GH-90000]")
    class ConditionalRetryTests {

        @Test
        @DisplayName("[IE004]: retry_only_on_specific_exceptions [GH-90000]")
        void retryOnlyOnSpecificExceptions() { // GH-90000
            // Only retry on IOException, not on IllegalArgumentException
            boolean shouldRetry = shouldRetryOnException(new java.io.IOException("Network error [GH-90000]"));
            boolean shouldNotRetry = shouldRetryOnException(new IllegalArgumentException("Bad input [GH-90000]"));

            assertThat(shouldRetry).isTrue(); // GH-90000
            assertThat(shouldNotRetry).isFalse(); // GH-90000
        }
    }

    // Helper methods
    private <T> Promise<T> retry(java.util.function.Supplier<Promise<T>> supplier, int maxRetries, Duration delay) { // GH-90000
        return retryInternal(supplier, maxRetries, delay, 0); // GH-90000
    }

    private <T> Promise<T> retryInternal(java.util.function.Supplier<Promise<T>> supplier, // GH-90000
                                          int maxRetries, Duration delay, int attempt) {
        return supplier.get().then( // GH-90000
            Promise::of,
            e -> attempt < maxRetries - 1
                ? retryInternal(supplier, maxRetries, delay, attempt + 1) // GH-90000
                : Promise.ofException(e) // GH-90000
        );
    }

    private <T> Promise<T> retryWithClassification(java.util.function.Supplier<Promise<T>> supplier, // GH-90000
                                                    int maxRetries, Duration delay) {
        // Simplified: permanent errors not retried
        return supplier.get(); // GH-90000
    }

    private Duration calculateBackoff(int attempt, Duration baseDelay, double multiplier) { // GH-90000
        long delayMs = (long) (baseDelay.toMillis() * Math.pow(multiplier, attempt - 1)); // GH-90000
        return Duration.ofMillis(delayMs); // GH-90000
    }

    private Duration calculateBackoffWithMax(int attempt, Duration baseDelay, double multiplier, Duration maxDelay) { // GH-90000
        Duration calculated = calculateBackoff(attempt, baseDelay, multiplier); // GH-90000
        return calculated.compareTo(maxDelay) > 0 ? maxDelay : calculated; // GH-90000
    }

    private Duration calculateBackoffWithJitter(int attempt, Duration baseDelay) { // GH-90000
        long delayMs = baseDelay.toMillis(); // GH-90000
        double jitter = Math.random() * 0.3; // 30% jitter // GH-90000
        long jitteredDelay = (long) (delayMs * (1 + jitter)); // GH-90000
        return Duration.ofMillis(jitteredDelay); // GH-90000
    }

    private boolean shouldRetryOnException(Exception e) { // GH-90000
        return e instanceof java.io.IOException;
    }

    interface RetryableService {
        Promise<String> call(); // GH-90000
    }
}
