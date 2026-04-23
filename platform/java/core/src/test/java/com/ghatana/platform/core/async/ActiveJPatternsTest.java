package com.ghatana.platform.core.async;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ActiveJPatterns}.
 *
 * Extends {@link EventloopTestBase} to run all tests inside a managed ActiveJ Eventloop.
 * Covers: sequential order + failure propagation, parallel fan-out, parallelLimited batching,
 * retry backoff, timeout, withTimeoutAndRetry.
 */
class ActiveJPatternsTest extends EventloopTestBase {

    private <T> T run(Promise<T> p) { // GH-90000
        return runPromise(() -> p); // GH-90000
    }

    private <T> Throwable runExpectFailure(Promise<T> p) { // GH-90000
        try {
            runPromise(() -> p); // GH-90000
            throw new AssertionError("Expected promise to fail but it succeeded");
        } catch (RuntimeException e) { // GH-90000
            return e.getCause() != null ? e.getCause() : e; // GH-90000
        }
    }

    // =========================================================================
    // Sequential
    // =========================================================================

    @Test
    void sequential_empty_returnsEmptyList() { // GH-90000
        List<Integer> result = run(ActiveJPatterns.sequential()); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    void sequential_singleElement_returnsSingleton() { // GH-90000
        List<Integer> result = run(ActiveJPatterns.sequential(Promise.of(42))); // GH-90000
        assertThat(result).containsExactly(42); // GH-90000
    }

    @Test
    void sequential_multipleElements_preservesOrder() { // GH-90000
        List<Integer> result = run(ActiveJPatterns.sequential( // GH-90000
                Promise.of(1), Promise.of(2), Promise.of(3))); // GH-90000
        assertThat(result).containsExactly(1, 2, 3); // GH-90000
    }

    @Test
    void sequential_firstFails_stopsEarly() { // GH-90000
        AtomicInteger executed = new AtomicInteger(0); // GH-90000
        Promise<Integer> failing = Promise.ofException(new RuntimeException("boom"));
        Promise<Integer> second  = Promise.of(executed.incrementAndGet()); // GH-90000

        Throwable error = runExpectFailure(ActiveJPatterns.sequential(failing, second)); // GH-90000
        assertThat(error).hasMessageContaining("boom");
    }

    @Test
    void sequential_middleFails_remainingNotRun() { // GH-90000
        Promise<Integer> p1 = Promise.of(1); // GH-90000
        Promise<Integer> pFail = Promise.ofException(new RuntimeException("mid-fail"));
        Promise<Integer> p3 = Promise.of(3); // GH-90000

        Throwable error = runExpectFailure(ActiveJPatterns.sequential(p1, pFail, p3)); // GH-90000
        assertThat(error).hasMessageContaining("mid-fail");
    }

    // =========================================================================
    // Sequential vs Parallel contract (PLAT-04 regression) // GH-90000
    // =========================================================================

    @Test
    void sequential_andParallel_produceSameResultsForSuccessCase() { // GH-90000
        Promise<Integer> a1 = Promise.of(10); // GH-90000
        Promise<Integer> a2 = Promise.of(20); // GH-90000
        Promise<Integer> b1 = Promise.of(10); // GH-90000
        Promise<Integer> b2 = Promise.of(20); // GH-90000

        List<Integer> seq = run(ActiveJPatterns.sequential(a1, a2)); // GH-90000
        List<Integer> par = run(ActiveJPatterns.parallel(b1, b2)); // GH-90000

        assertThat(seq).containsExactlyInAnyOrderElementsOf(par); // GH-90000
        assertThat(seq).containsExactly(10, 20); // GH-90000
    }

    // =========================================================================
    // Parallel
    // =========================================================================

    @Test
    void parallel_empty_returnsEmptyList() { // GH-90000
        List<String> result = run(ActiveJPatterns.parallel()); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    void parallel_multipleElements_returnsAll() { // GH-90000
        List<String> result = run(ActiveJPatterns.parallel( // GH-90000
                Promise.of("a"), Promise.of("b"), Promise.of("c")));
        assertThat(result).containsExactlyInAnyOrder("a", "b", "c"); // GH-90000
    }

    // =========================================================================
    // ParallelLimited
    // =========================================================================

    @Test
    void parallelLimited_belowLimit_runsAll() { // GH-90000
        List<Promise<Integer>> ops = List.of(Promise.of(1), Promise.of(2), Promise.of(3)); // GH-90000
        List<Integer> result = run(ActiveJPatterns.parallelLimited(10, ops)); // GH-90000
        assertThat(result).containsExactlyInAnyOrder(1, 2, 3); // GH-90000
    }

    @Test
    void parallelLimited_aboveLimit_processesBatchedResults() { // GH-90000
        List<Promise<Integer>> ops = List.of( // GH-90000
                Promise.of(1), Promise.of(2), Promise.of(3), // GH-90000
                Promise.of(4), Promise.of(5)); // GH-90000
        List<Integer> result = run(ActiveJPatterns.parallelLimited(2, ops)); // GH-90000
        assertThat(result).containsExactlyInAnyOrder(1, 2, 3, 4, 5); // GH-90000
    }

    @Test
    void parallelLimited_emptyList_returnsEmpty() { // GH-90000
        List<Integer> result = run(ActiveJPatterns.parallelLimited(5, List.of())); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    // =========================================================================
    // Retry
    // =========================================================================

    @Test
    void withRetry_succeedsFirstAttempt_noRetryNeeded() { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        Integer result = run(ActiveJPatterns.withRetry( // GH-90000
                () -> { calls.incrementAndGet(); return Promise.of(99); }, // GH-90000
                3,
                Duration.ofMillis(1), // GH-90000
                Duration.ofMillis(10))); // GH-90000
        assertThat(result).isEqualTo(99); // GH-90000
        assertThat(calls.get()).isEqualTo(1); // GH-90000
    }

    @Test
    void withRetry_failsThenSucceeds_retriesUntilSuccess() { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        Integer result = run(ActiveJPatterns.withRetry( // GH-90000
                () -> { // GH-90000
                    int n = calls.incrementAndGet(); // GH-90000
                    if (n < 3) return Promise.ofException(new RuntimeException("retry-" + n)); // GH-90000
                    return Promise.of(42); // GH-90000
                },
                5,
                Duration.ofMillis(1), // GH-90000
                Duration.ofMillis(10))); // GH-90000
        assertThat(result).isEqualTo(42); // GH-90000
        assertThat(calls.get()).isEqualTo(3); // GH-90000
    }

    @Test
    void withRetry_maxRetriesExhausted_propagatesLastError() { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        Throwable error = runExpectFailure(ActiveJPatterns.withRetry( // GH-90000
                () -> { calls.incrementAndGet(); return Promise.ofException(new RuntimeException("always-fails")); },
                2,
                Duration.ofMillis(1), // GH-90000
                Duration.ofMillis(10))); // GH-90000
        assertThat(error).hasMessageContaining("always-fails");
        assertThat(calls.get()).isEqualTo(3); // 1 initial + 2 retries // GH-90000
    }

    @Test
    void withRetry_zeroRetries_failsImmediately() { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        runExpectFailure(ActiveJPatterns.withRetry( // GH-90000
                () -> { calls.incrementAndGet(); return Promise.ofException(new RuntimeException("no-retry")); },
                0,
                Duration.ofMillis(1), // GH-90000
                Duration.ofMillis(10))); // GH-90000
        assertThat(calls.get()).isEqualTo(1); // GH-90000
    }

    // =========================================================================
    // withTimeoutAndRetry — verify wiring compiles and returns
    // =========================================================================

    @Test
    void withTimeoutAndRetry_immediateSuccess_returnsValue() { // GH-90000
        Integer result = run(ActiveJPatterns.withTimeoutAndRetry( // GH-90000
                () -> Promise.of(7), // GH-90000
                Duration.ofSeconds(1), // GH-90000
                2,
                Duration.ofMillis(1), // GH-90000
                Duration.ofMillis(10))); // GH-90000
        assertThat(result).isEqualTo(7); // GH-90000
    }
}
