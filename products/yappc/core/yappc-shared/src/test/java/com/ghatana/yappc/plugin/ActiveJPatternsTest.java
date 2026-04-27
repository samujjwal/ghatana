package com.ghatana.yappc.plugin;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for ActiveJPatterns async-utility class
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ActiveJPatterns")
class ActiveJPatternsTest extends EventloopTestBase {

    // =========================================================================
    // withRetry
    // =========================================================================

    @Test
    @DisplayName("withRetry() succeeds on first attempt when no failure")
    void withRetry_succeedsOnFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        String result = runPromise(() ->
                ActiveJPatterns.withRetry(
                        () -> {
                            attempts.incrementAndGet();
                            return Promise.of("ok");
                        },
                        3,
                        Duration.ofMillis(1)));

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("withRetry() retries on transient failure and eventually succeeds")
    void withRetry_retriesAndSucceeds() {
        AtomicInteger attempts = new AtomicInteger();

        String result = runPromise(() ->
                ActiveJPatterns.withRetry(
                        () -> {
                            int count = attempts.incrementAndGet();
                            if (count < 3) {
                                return Promise.ofException(new RuntimeException("transient"));
                            }
                            return Promise.of("recovered");
                        },
                        5,
                        Duration.ofMillis(1)));

        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("withRetry() fails after maxAttempts are exhausted")
    void withRetry_failsAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() ->
                runPromise(() ->
                        ActiveJPatterns.withRetry(
                                () -> {
                                    attempts.incrementAndGet();
                                    return Promise.ofException(new RuntimeException("always fails"));
                                },
                                3,
                                Duration.ofMillis(1))))
                .hasMessageContaining("always fails");

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("withRetry() throws when maxAttempts < 1")
    void withRetry_throwsOnInvalidMaxAttempts() {
        assertThatThrownBy(() ->
                ActiveJPatterns.withRetry(() -> Promise.of("x"), 0, Duration.ofMillis(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // withFallback
    // =========================================================================

    @Test
    @DisplayName("withFallback() passes through success")
    void withFallback_passesThroughSuccess() {
        String result = runPromise(() ->
                ActiveJPatterns.withFallback(
                        Promise.of("primary"),
                        e -> "fallback"));

        assertThat(result).isEqualTo("primary");
    }

    @Test
    @DisplayName("withFallback() returns fallback on failure")
    void withFallback_returnsFallbackOnFailure() {
        String result = runPromise(() ->
                ActiveJPatterns.withFallback(
                        Promise.ofException(new RuntimeException("boom")),
                        e -> "fallback"));

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    @DisplayName("withFallback() propagates failure when fallback supplier throws")
    void withFallback_propagatesFailureWhenFallbackThrows() {
        assertThatThrownBy(() ->
                runPromise(() ->
                        ActiveJPatterns.withFallback(
                                Promise.ofException(new RuntimeException("primary")),
                                e -> {
                                    throw new RuntimeException("fallback also failed");
                                })))
                .hasMessageContaining("fallback also failed");
    }

    // =========================================================================
    // withBlocking
    // =========================================================================

    @Test
    @DisplayName("withBlocking() executes task and returns result")
    void withBlocking_executesTaskAndReturnsResult() {
        var executor = Executors.newSingleThreadExecutor();
        try {
            String result = runPromise(() ->
                    ActiveJPatterns.withBlocking(executor, () -> {
                        Thread.sleep(1); // simulate blocking I/O
                        return "blocking-result";
                    }));

            assertThat(result).isEqualTo("blocking-result");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("withBlocking() propagates exceptions from task")
    void withBlocking_propagatesExceptionsFromTask() {
        var executor = Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(() ->
                    runPromise(() ->
                            ActiveJPatterns.withBlocking(executor, () -> {
                                throw new RuntimeException("task failed");
                            })))
                    .hasMessageContaining("task failed");
        } finally {
            executor.shutdownNow();
        }
    }
}
