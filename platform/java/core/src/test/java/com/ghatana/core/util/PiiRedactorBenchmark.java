package com.ghatana.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance benchmarks for PiiRedactor utility class.
 *
 * <p>These tests measure the performance of PII redaction operations
 * to ensure they remain efficient even with large inputs.</p>
 *
 * <p><b>Note:</b> These are not strict JMH benchmarks but simple performance
 * tests that can be run as part of the regular test suite. For precise
 * microbenchmarking, use JMH in a dedicated benchmark module.</p>
 *
 * @doc.type class
 * @doc.purpose Performance benchmarks for PII redaction
 * @doc.layer test
 * @doc.pattern Performance testing
 */
@DisplayName("PiiRedactor Performance Benchmarks")
class PiiRedactorBenchmark {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int BENCHMARK_ITERATIONS = 100;

    /**
     * Benchmark email redaction performance.
     */
    @Test
    @DisplayName("Benchmark: Email redaction performance")
    void benchmarkEmailRedaction() { // GH-90000
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            PiiRedactor.redact("Contact user@example.com for support");
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            String result = PiiRedactor.redact("Contact user@example.com for support");
            assertThat(result).contains("***@***.***");
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("Email redaction: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        // Performance assertion: should complete in reasonable time
        assertThat(durationMs).isLessThan(1000); // < 1 second for 100 iterations // GH-90000
    }

    /**
     * Benchmark phone number redaction performance.
     */
    @Test
    @DisplayName("Benchmark: Phone number redaction performance")
    void benchmarkPhoneRedaction() { // GH-90000
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            PiiRedactor.redact("Call me at (555) 123-4567");
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            String result = PiiRedactor.redact("Call me at (555) 123-4567");
            assertThat(result).contains("***");
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("Phone redaction: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        assertThat(durationMs).isLessThan(1000); // GH-90000
    }

    /**
     * Benchmark credit card redaction performance.
     */
    @Test
    @DisplayName("Benchmark: Credit card redaction performance")
    void benchmarkCreditCardRedaction() { // GH-90000
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            PiiRedactor.redact("Card: 4111-1111-1111-1111");
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            String result = PiiRedactor.redact("Card: 4111-1111-1111-1111");
            assertThat(result).contains("****-1111");
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("Credit card redaction: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        assertThat(durationMs).isLessThan(1000); // GH-90000
    }

    /**
     * Benchmark SSN redaction performance.
     */
    @Test
    @DisplayName("Benchmark: SSN redaction performance")
    void benchmarkSsnRedaction() { // GH-90000
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            PiiRedactor.redact("SSN: 123-45-6789");
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            String result = PiiRedactor.redact("SSN: 123-45-6789");
            assertThat(result).contains("****");
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("SSN redaction: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        assertThat(durationMs).isLessThan(1000); // GH-90000
    }

    /**
     * Benchmark redaction with large input text.
     */
    @Test
    @DisplayName("Benchmark: Redaction performance with large input")
    void benchmarkLargeInputRedaction() { // GH-90000
        // Create large input with multiple PII types
        StringBuilder sb = new StringBuilder(); // GH-90000
        for (int i = 0; i < 100; i++) { // GH-90000
            sb.append("Contact user")
              .append(i) // GH-90000
              .append("@example.com for support. ")
              .append("Call (555) 123-4567. ")
              .append("Card: 4111-1111-1111-1111. ")
              .append("SSN: 123-45-6789. ");
        }
        String largeInput = sb.toString(); // GH-90000

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            PiiRedactor.redact(largeInput); // GH-90000
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            String result = PiiRedactor.redact(largeInput); // GH-90000
            assertThat(result).contains("***");
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("Large input redaction (%d chars): %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                largeInput.length(), BENCHMARK_ITERATIONS, durationMs, avgPerOpMs); // GH-90000

        // Performance assertion: keep benchmark useful while tolerating slower shared CI runners.
        assertThat(durationMs).isLessThan(8000); // < 8 seconds for 100 iterations with large input // GH-90000
    }

    /**
     * Benchmark containsPii detection performance.
     */
    @Test
    @DisplayName("Benchmark: PII detection performance")
    void benchmarkContainsPii() { // GH-90000
        String input = "Contact user@example.com for support";

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            PiiRedactor.containsPii(input); // GH-90000
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            boolean result = PiiRedactor.containsPii(input); // GH-90000
            assertThat(result).isTrue(); // GH-90000
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("PII detection: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        assertThat(durationMs).isLessThan(1000); // GH-90000
    }
}
