package com.ghatana.platform.core.performance;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Performance benchmark tests for Core Platform components.
 * 
 * @doc.type class
 * @doc.purpose Performance benchmarking tests for core platform operations
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Core Platform Performance Benchmarks")
@Tag("performance")
class CorePerformanceBenchmarkTest extends EventloopTestBase {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final Duration MAX_ACCEPTABLE_DURATION = Duration.ofMillis(100);

    @Test
    @DisplayName("should handle 1000 sequential promises within 100ms")
    void shouldHandle1000SequentialPromisesWithin100ms() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runPromise(() -> Promise.of(i));
        }
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runPromise(() -> Promise.of(i));
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(elapsed).isLessThan(MAX_ACCEPTABLE_DURATION);
    }

    @Test
    @DisplayName("should handle 1000 parallel promises efficiently")
    void shouldHandle1000ParallelPromisesEfficiently() {
        List<Promise<Integer>> promises = new ArrayList<>();
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            promises.add(Promise.of(i));
        }
        runPromise(() -> Promise.all(promises));
        promises.clear();
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            promises.add(Promise.of(i));
        }
        
        List<Integer> results = runPromise(() -> Promise.all(promises));
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(results).hasSize(BENCHMARK_ITERATIONS);
        assertThat(elapsed).isLessThan(Duration.ofMillis(50));
    }

    @Test
    @DisplayName("should handle promise chaining with minimal overhead")
    void shouldHandlePromiseChainingWithMinimalOverhead() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runPromise(() -> Promise.of(1)
                .then(v -> Promise.of(v + 1))
                .then(v -> Promise.of(v + 1)));
        }
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runPromise(() -> Promise.of(1)
                .then(v -> Promise.of(v + 1))
                .then(v -> Promise.of(v + 1))
                .then(v -> Promise.of(v + 1))
                .then(v -> Promise.of(v + 1)));
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(elapsed).isLessThan(MAX_ACCEPTABLE_DURATION);
    }

    @Test
    @DisplayName("should handle error recovery with acceptable performance")
    void shouldHandleErrorRecoveryWithAcceptablePerformance() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runPromise(() -> Promise.<Integer>ofException(new RuntimeException("test"))
                .whenException(e -> Promise.of(0)));
        }
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runPromise(() -> Promise.<Integer>ofException(new RuntimeException("test"))
                .whenException(e -> Promise.of(0)));
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(elapsed).isLessThan(MAX_ACCEPTABLE_DURATION);
    }

    @Test
    @DisplayName("should handle concurrent promise execution efficiently")
    void shouldHandleConcurrentPromiseExecutionEfficiently() {
        AtomicInteger counter = new AtomicInteger(0);
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runPromise(() -> Promise.of(counter.incrementAndGet()));
        }
        counter.set(0);
        
        // Benchmark
        Instant start = Instant.now();
        
        List<Promise<Integer>> promises = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            promises.add(Promise.of(counter.incrementAndGet()));
        }
        
        runPromise(() -> Promise.all(promises));
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(counter.get()).isEqualTo(BENCHMARK_ITERATIONS);
        assertThat(elapsed).isLessThan(Duration.ofMillis(50));
    }

    @Test
    @DisplayName("should handle promise composition with good performance")
    void shouldHandlePromiseCompositionWithGoodPerformance() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runPromise(() -> Promise.of(10).combine(Promise.of(5), (a, b) -> a + b));
        }
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runPromise(() -> Promise.of(10).combine(Promise.of(5), (a, b) -> a + b));
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(elapsed).isLessThan(MAX_ACCEPTABLE_DURATION);
    }

    @Test
    @DisplayName("should maintain performance under memory pressure")
    void shouldMaintainPerformanceUnderMemoryPressure() {
        List<byte[]> memoryPressure = new ArrayList<>();
        
        // Create memory pressure (10MB)
        for (int i = 0; i < 10; i++) {
            memoryPressure.add(new byte[1024 * 1024]);
        }
        
        // Benchmark with memory pressure
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runPromise(() -> Promise.of(i));
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(elapsed).isLessThan(Duration.ofMillis(150));
        
        // Cleanup
        memoryPressure.clear();
    }

    @Test
    @DisplayName("should handle nested promise chains efficiently")
    void shouldHandleNestedPromiseChainsEfficiently() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runPromise(() -> Promise.of(1)
                .then(v -> Promise.of(v + 1)
                    .then(v2 -> Promise.of(v2 + 1))));
        }
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runPromise(() -> Promise.of(1)
                .then(v -> Promise.of(v + 1)
                    .then(v2 -> Promise.of(v2 + 1)
                        .then(v3 -> Promise.of(v3 + 1)))));
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(elapsed).isLessThan(MAX_ACCEPTABLE_DURATION);
    }

    @Test
    @DisplayName("should handle promise retry with acceptable overhead")
    void shouldHandlePromiseRetryWithAcceptableOverhead() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            attempts.set(0);
            runPromise(() -> Promise.ofCallback(cb -> {
                if (attempts.incrementAndGet() < 2) {
                    cb.setException(new RuntimeException("retry"));
                } else {
                    cb.set(1);
                }
            }));
        }
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            attempts.set(0);
            runPromise(() -> Promise.ofCallback(cb -> {
                if (attempts.incrementAndGet() < 2) {
                    cb.setException(new RuntimeException("retry"));
                } else {
                    cb.set(1);
                }
            }));
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(elapsed).isLessThan(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("should handle eventloop task scheduling efficiently")
    void shouldHandleEventloopTaskSchedulingEfficiently() {
        AtomicInteger taskCount = new AtomicInteger(0);
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runPromise(() -> Promise.ofCallback(cb -> {
                eventloop().post(() -> {
                    taskCount.incrementAndGet();
                    cb.set(null);
                });
            }));
        }
        taskCount.set(0);
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runPromise(() -> Promise.ofCallback(cb -> {
                eventloop().post(() -> {
                    taskCount.incrementAndGet();
                    cb.set(null);
                });
            }));
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(taskCount.get()).isEqualTo(BENCHMARK_ITERATIONS);
        assertThat(elapsed).isLessThan(MAX_ACCEPTABLE_DURATION);
    }
}
