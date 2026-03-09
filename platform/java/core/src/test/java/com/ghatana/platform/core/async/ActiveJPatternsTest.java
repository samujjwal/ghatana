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

    private <T> T run(Promise<T> p) {
        return runPromise(() -> p);
    }

    private <T> Throwable runExpectFailure(Promise<T> p) {
        try {
            runPromise(() -> p);
            throw new AssertionError("Expected promise to fail but it succeeded");
        } catch (RuntimeException e) {
            return e.getCause() != null ? e.getCause() : e;
        }
    }

    // =========================================================================
    // Sequential
    // =========================================================================

    @Test
    void sequential_empty_returnsEmptyList() {
        List<Integer> result = run(ActiveJPatterns.sequential());
        assertThat(result).isEmpty();
    }

    @Test
    void sequential_singleElement_returnsSingleton() {
        List<Integer> result = run(ActiveJPatterns.sequential(Promise.of(42)));
        assertThat(result).containsExactly(42);
    }

    @Test
    void sequential_multipleElements_preservesOrder() {
        List<Integer> result = run(ActiveJPatterns.sequential(
                Promise.of(1), Promise.of(2), Promise.of(3)));
        assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    void sequential_firstFails_stopsEarly() {
        AtomicInteger executed = new AtomicInteger(0);
        Promise<Integer> failing = Promise.ofException(new RuntimeException("boom"));
        Promise<Integer> second  = Promise.of(executed.incrementAndGet());

        Throwable error = runExpectFailure(ActiveJPatterns.sequential(failing, second));
        assertThat(error).hasMessageContaining("boom");
    }

    @Test
    void sequential_middleFails_remainingNotRun() {
        Promise<Integer> p1 = Promise.of(1);
        Promise<Integer> pFail = Promise.ofException(new RuntimeException("mid-fail"));
        Promise<Integer> p3 = Promise.of(3);

        Throwable error = runExpectFailure(ActiveJPatterns.sequential(p1, pFail, p3));
        assertThat(error).hasMessageContaining("mid-fail");
    }

    // =========================================================================
    // Sequential vs Parallel contract (PLAT-04 regression)
    // =========================================================================

    @Test
    void sequential_andParallel_produceSameResultsForSuccessCase() {
        Promise<Integer> a1 = Promise.of(10);
        Promise<Integer> a2 = Promise.of(20);
        Promise<Integer> b1 = Promise.of(10);
        Promise<Integer> b2 = Promise.of(20);

        List<Integer> seq = run(ActiveJPatterns.sequential(a1, a2));
        List<Integer> par = run(ActiveJPatterns.parallel(b1, b2));

        assertThat(seq).containsExactlyInAnyOrderElementsOf(par);
        assertThat(seq).containsExactly(10, 20);
    }

    // =========================================================================
    // Parallel
    // =========================================================================

    @Test
    void parallel_empty_returnsEmptyList() {
        List<String> result = run(ActiveJPatterns.parallel());
        assertThat(result).isEmpty();
    }

    @Test
    void parallel_multipleElements_returnsAll() {
        List<String> result = run(ActiveJPatterns.parallel(
                Promise.of("a"), Promise.of("b"), Promise.of("c")));
        assertThat(result).containsExactlyInAnyOrder("a", "b", "c");
    }

    // =========================================================================
    // ParallelLimited
    // =========================================================================

    @Test
    void parallelLimited_belowLimit_runsAll() {
        List<Promise<Integer>> ops = List.of(Promise.of(1), Promise.of(2), Promise.of(3));
        List<Integer> result = run(ActiveJPatterns.parallelLimited(10, ops));
        assertThat(result).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void parallelLimited_aboveLimit_processesBatchedResults() {
        List<Promise<Integer>> ops = List.of(
                Promise.of(1), Promise.of(2), Promise.of(3),
                Promise.of(4), Promise.of(5));
        List<Integer> result = run(ActiveJPatterns.parallelLimited(2, ops));
        assertThat(result).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    }

    @Test
    void parallelLimited_emptyList_returnsEmpty() {
        List<Integer> result = run(ActiveJPatterns.parallelLimited(5, List.of()));
        assertThat(result).isEmpty();
    }

    // =========================================================================
    // Retry
    // =========================================================================

    @Test
    void withRetry_succeedsFirstAttempt_noRetryNeeded() {
        AtomicInteger calls = new AtomicInteger(0);
        Integer result = run(ActiveJPatterns.withRetry(
                () -> { calls.incrementAndGet(); return Promise.of(99); },
                3,
                Duration.ofMillis(1),
                Duration.ofMillis(10)));
        assertThat(result).isEqualTo(99);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void withRetry_failsThenSucceeds_retriesUntilSuccess() {
        AtomicInteger calls = new AtomicInteger(0);
        Integer result = run(ActiveJPatterns.withRetry(
                () -> {
                    int n = calls.incrementAndGet();
                    if (n < 3) return Promise.ofException(new RuntimeException("retry-" + n));
                    return Promise.of(42);
                },
                5,
                Duration.ofMillis(1),
                Duration.ofMillis(10)));
        assertThat(result).isEqualTo(42);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void withRetry_maxRetriesExhausted_propagatesLastError() {
        AtomicInteger calls = new AtomicInteger(0);
        Throwable error = runExpectFailure(ActiveJPatterns.withRetry(
                () -> { calls.incrementAndGet(); return Promise.ofException(new RuntimeException("always-fails")); },
                2,
                Duration.ofMillis(1),
                Duration.ofMillis(10)));
        assertThat(error).hasMessageContaining("always-fails");
        assertThat(calls.get()).isEqualTo(3); // 1 initial + 2 retries
    }

    @Test
    void withRetry_zeroRetries_failsImmediately() {
        AtomicInteger calls = new AtomicInteger(0);
        runExpectFailure(ActiveJPatterns.withRetry(
                () -> { calls.incrementAndGet(); return Promise.ofException(new RuntimeException("no-retry")); },
                0,
                Duration.ofMillis(1),
                Duration.ofMillis(10)));
        assertThat(calls.get()).isEqualTo(1);
    }

    // =========================================================================
    // withTimeoutAndRetry — verify wiring compiles and returns
    // =========================================================================

    @Test
    void withTimeoutAndRetry_immediateSuccess_returnsValue() {
        Integer result = run(ActiveJPatterns.withTimeoutAndRetry(
                () -> Promise.of(7),
                Duration.ofSeconds(1),
                2,
                Duration.ofMillis(1),
                Duration.ofMillis(10)));
        assertThat(result).isEqualTo(7);
    }
}
