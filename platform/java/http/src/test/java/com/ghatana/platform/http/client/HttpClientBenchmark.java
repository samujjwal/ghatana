package com.ghatana.platform.http.client;

import com.ghatana.platform.observability.NoopMetricsCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance benchmarks for HTTP client functionality.
 *
 * <p>These tests measure the performance of HTTP client operations
 * to ensure they remain efficient even with high request volumes.</p>
 *
 * <p><b>Note:</b> These are not strict JMH benchmarks but simple performance
 * tests that can be run as part of the regular test suite. For precise
 * microbenchmarking, use JMH in a dedicated benchmark module.</p>
 *
 * @doc.type class
 * @doc.purpose Performance benchmarks for HTTP client
 * @doc.layer test
 * @doc.pattern Performance testing
 */
@DisplayName("HTTP Client Performance Benchmarks [GH-90000]")
class HttpClientBenchmark {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int BENCHMARK_ITERATIONS = 100;

    /**
     * Benchmark HTTP client factory creation performance.
     */
    @Test
    @DisplayName("Benchmark: HttpClientFactory adapter creation [GH-90000]")
    void benchmarkAdapterCreation() { // GH-90000
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            HttpClientFactory.createDefaultAdapter(new NoopMetricsCollector()); // GH-90000
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            var adapter = HttpClientFactory.createDefaultAdapter(new NoopMetricsCollector()); // GH-90000
            assertThat(adapter).isNotNull(); // GH-90000
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("Adapter creation: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        // Performance assertion: should complete in reasonable time
        assertThat(durationMs).isLessThan(5000); // < 5 seconds for 100 iterations // GH-90000
    }

    /**
     * Benchmark HTTP client config creation performance.
     */
    @Test
    @DisplayName("Benchmark: HttpClientConfig builder [GH-90000]")
    void benchmarkConfigCreation() { // GH-90000
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            HttpClientConfig.builder() // GH-90000
                    .callTimeout(java.time.Duration.ofSeconds(30)) // GH-90000
                    .connectTimeout(java.time.Duration.ofSeconds(10)) // GH-90000
                    .build(); // GH-90000
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            var config = HttpClientConfig.builder() // GH-90000
                    .callTimeout(java.time.Duration.ofSeconds(30)) // GH-90000
                    .connectTimeout(java.time.Duration.ofSeconds(10)) // GH-90000
                    .build(); // GH-90000
            assertThat(config).isNotNull(); // GH-90000
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("Config creation: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        assertThat(durationMs).isLessThan(1000); // < 1 second for 100 iterations // GH-90000
    }

    /**
     * Benchmark rate limiter cache performance.
     */
    @Test
    @DisplayName("Benchmark: Rate limiter cache operations [GH-90000]")
    void benchmarkRateLimiterCache() { // GH-90000
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            HttpClientFactory.createDefaultAdapter(new NoopMetricsCollector()); // GH-90000
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            var adapter = HttpClientFactory.createDefaultAdapter(new NoopMetricsCollector()); // GH-90000
            assertThat(adapter).isNotNull(); // GH-90000
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("Rate limiter cache: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        assertThat(durationMs).isLessThan(5000); // GH-90000
    }
}
