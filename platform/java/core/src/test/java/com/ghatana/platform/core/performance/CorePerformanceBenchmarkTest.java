package com.ghatana.platform.core.performance;

import com.ghatana.platform.core.async.ActiveJPatterns;
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

    private <T> T run(Promise<T> p) {
        return runPromise(() -> p);
    }

    @Test
    @DisplayName("should handle 1000 sequential promises within 100ms")
    void shouldHandle1000SequentialPromisesWithin100ms() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            run(Promise.of(i));
        }
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            run(Promise.of(i));
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
        run(ActiveJPatterns.parallelLimited(1000, promises));
        promises.clear();
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            promises.add(Promise.of(i));
        }
        
        List<Integer> results = run(ActiveJPatterns.parallelLimited(1000, promises));
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(results).hasSize(BENCHMARK_ITERATIONS);
        assertThat(elapsed).isLessThan(Duration.ofMillis(50));
    }

    @Test
    @DisplayName("should handle promise chaining with minimal overhead")
    void shouldHandlePromiseChainingWithMinimalOverhead() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            run(Promise.of(1)
                .then(v -> Promise.of(v + 1))
                .then(v -> Promise.of(v + 1)));
        }
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            run(Promise.of(1)
                .then(v -> Promise.of(v + 1))
                .then(v -> Promise.of(v + 1))
                .then(v -> Promise.of(v + 1))
                .then(v -> Promise.of(v + 1)));
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(elapsed).isLessThan(MAX_ACCEPTABLE_DURATION);
    }

    @Test
    @DisplayName("should handle promise transformation with acceptable performance")
    void shouldHandlePromiseTransformationWithAcceptablePerformance() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            run(Promise.of(10).then(v -> Promise.of(v * 2)));
        }
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            run(Promise.of(10).then(v -> Promise.of(v * 2)));
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
            run(Promise.of(counter.incrementAndGet()));
        }
        counter.set(0);
        
        // Benchmark
        Instant start = Instant.now();
        
        List<Promise<Integer>> promises = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            promises.add(Promise.of(counter.incrementAndGet()));
        }
        
        run(ActiveJPatterns.parallelLimited(1000, promises));
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(counter.get()).isEqualTo(BENCHMARK_ITERATIONS);
        assertThat(elapsed).isLessThan(Duration.ofMillis(50));
    }

    @Test
    @DisplayName("should handle promise composition with good performance")
    void shouldHandlePromiseCompositionWithGoodPerformance() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            run(Promise.of(10).combine(Promise.of(5), (a, b) -> a + b));
        }
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            run(Promise.of(10).combine(Promise.of(5), (a, b) -> a + b));
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
            run(Promise.of(i));
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(elapsed).isLessThan(Duration.ofMillis(150));
        
        // Cleanup
        memoryPressure.clear();
    }

    @Test
    @DisplayName("should handle basic promise operations efficiently")
    void shouldHandleBasicPromiseOperationsEfficiently() {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            run(Promise.of(42));
        }
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            run(Promise.of(42));
        }
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(elapsed).isLessThan(MAX_ACCEPTABLE_DURATION);
    }

    @Test
    @DisplayName("should handle sequential operations efficiently")
    void shouldHandleSequentialOperationsEfficiently() {
        List<Promise<Integer>> promises = new ArrayList<>();
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            promises.add(Promise.of(i));
        }
        run(ActiveJPatterns.sequential(promises.toArray(new Promise[0])));
        promises.clear();
        
        // Benchmark
        Instant start = Instant.now();
        
        for (int i = 0; i < 100; i++) {  // Smaller count for sequential
            promises.add(Promise.of(i));
        }
        run(ActiveJPatterns.sequential(promises.toArray(new Promise[0])));
        
        Duration elapsed = Duration.between(start, Instant.now());
        
        assertThat(elapsed).isLessThan(MAX_ACCEPTABLE_DURATION);
    }
}
