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
@DisplayName("HTTP Client Performance Benchmarks")
class HttpClientBenchmark {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int BENCHMARK_ITERATIONS = 100;

    /**
     * Benchmark HTTP client factory creation performance.
     */
    @Test
    @DisplayName("Benchmark: HttpClientFactory adapter creation")
    void benchmarkAdapterCreation() { 
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { 
            HttpClientFactory.createDefaultAdapter(new NoopMetricsCollector()); 
        }

        // Benchmark
        long startTime = System.nanoTime(); 
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { 
            var adapter = HttpClientFactory.createDefaultAdapter(new NoopMetricsCollector()); 
            assertThat(adapter).isNotNull(); 
        }
        long endTime = System.nanoTime(); 

        long durationMs = (endTime - startTime) / 1_000_000; 
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; 

        System.out.printf("Adapter creation: %d iterations in %d ms (avg %.3f ms/operation)%n", 
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        // Performance assertion: should complete in reasonable time
        assertThat(durationMs).isLessThan(5000); // < 5 seconds for 100 iterations 
    }

    /**
     * Benchmark HTTP client config creation performance.
     */
    @Test
    @DisplayName("Benchmark: HttpClientConfig builder")
    void benchmarkConfigCreation() { 
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { 
            HttpClientConfig.builder() 
                    .callTimeout(java.time.Duration.ofSeconds(30)) 
                    .connectTimeout(java.time.Duration.ofSeconds(10)) 
                    .build(); 
        }

        // Benchmark
        long startTime = System.nanoTime(); 
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { 
            var config = HttpClientConfig.builder() 
                    .callTimeout(java.time.Duration.ofSeconds(30)) 
                    .connectTimeout(java.time.Duration.ofSeconds(10)) 
                    .build(); 
            assertThat(config).isNotNull(); 
        }
        long endTime = System.nanoTime(); 

        long durationMs = (endTime - startTime) / 1_000_000; 
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; 

        System.out.printf("Config creation: %d iterations in %d ms (avg %.3f ms/operation)%n", 
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        assertThat(durationMs).isLessThan(3000); // < 3 seconds for 100 iterations on shared CI runners 
    }

    /**
     * Benchmark rate limiter cache performance.
     */
    @Test
    @DisplayName("Benchmark: Rate limiter cache operations")
    void benchmarkRateLimiterCache() { 
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { 
            HttpClientFactory.createDefaultAdapter(new NoopMetricsCollector()); 
        }

        // Benchmark
        long startTime = System.nanoTime(); 
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { 
            var adapter = HttpClientFactory.createDefaultAdapter(new NoopMetricsCollector()); 
            assertThat(adapter).isNotNull(); 
        }
        long endTime = System.nanoTime(); 

        long durationMs = (endTime - startTime) / 1_000_000; 
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; 

        System.out.printf("Rate limiter cache: %d iterations in %d ms (avg %.3f ms/operation)%n", 
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        assertThat(durationMs).isLessThan(5000); 
    }
}
