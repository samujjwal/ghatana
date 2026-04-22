package com.ghatana.platform.security.crypto;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance benchmarks for crypto operations.
 *
 * <p>These tests measure the performance of password hashing and verification
 * operations to ensure they remain efficient while maintaining security.</p>
 *
 * <p><b>Note:</b> These are not strict JMH benchmarks but simple performance
 * tests that can be run as part of the regular test suite. For precise
 * microbenchmarking, use JMH in a dedicated benchmark module.</p>
 *
 * @doc.type class
 * @doc.purpose Performance benchmarks for crypto operations
 * @doc.layer test
 * @doc.pattern Performance testing
 */
@DisplayName("Crypto Operation Performance Benchmarks [GH-90000]")
@Tag("performance [GH-90000]")
class PasswordHasherBenchmark {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int BENCHMARK_ITERATIONS = 50;

    /**
     * Benchmark password hashing performance.
     */
    @Test
    @DisplayName("Benchmark: Password hashing [GH-90000]")
    void benchmarkPasswordHashing() { // GH-90000
        PasswordHasher hasher = new PasswordHasher(); // GH-90000
        String password = "test-password-12345";

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            hasher.hash(password); // GH-90000
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            String hash = hasher.hash(password); // GH-90000
            assertThat(hash).isNotNull(); // GH-90000
            assertThat(hash).isNotEmpty(); // GH-90000
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("Password hashing: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        // BCrypt with cost factor 12 should be reasonably fast
        // Performance assertion: should complete in reasonable time
        assertThat(durationMs).isLessThan(120000); // < 2 minutes for 50 iterations (jBCrypt cost 12) // GH-90000
    }

    /**
     * Benchmark password verification performance.
     */
    @Test
    @DisplayName("Benchmark: Password verification [GH-90000]")
    void benchmarkPasswordVerification() { // GH-90000
        PasswordHasher hasher = new PasswordHasher(); // GH-90000
        String password = "test-password-12345";
        String hash = hasher.hash(password); // GH-90000

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            hasher.verify(password, hash); // GH-90000
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            boolean result = hasher.verify(password, hash); // GH-90000
            assertThat(result).isTrue(); // GH-90000
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("Password verification: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        // Verification should be faster than hashing
        assertThat(durationMs).isLessThan(300000); // < 5 minutes for 50 iterations (jBCrypt cost 12) // GH-90000
    }

    /**
     * Benchmark hash and verify combined operation.
     */
    @Test
    @DisplayName("Benchmark: Hash and verify combined [GH-90000]")
    void benchmarkHashAndVerifyCombined() { // GH-90000
        PasswordHasher hasher = new PasswordHasher(); // GH-90000
        String password = "test-password-12345";

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            String hash = hasher.hash(password); // GH-90000
            hasher.verify(password, hash); // GH-90000
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            String hash = hasher.hash(password); // GH-90000
            boolean result = hasher.verify(password, hash); // GH-90000
            assertThat(result).isTrue(); // GH-90000
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("Hash and verify combined: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        assertThat(durationMs).isLessThan(400000); // < ~7 minutes for 50 iterations (jBCrypt cost 12) // GH-90000
    }

    /**
     * Benchmark verification failure performance.
     */
    @Test
    @DisplayName("Benchmark: Verification with wrong password [GH-90000]")
    void benchmarkVerificationFailure() { // GH-90000
        PasswordHasher hasher = new PasswordHasher(); // GH-90000
        String correctPassword = "test-password-12345";
        String wrongPassword = "wrong-password-67890";
        String hash = hasher.hash(correctPassword); // GH-90000

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) { // GH-90000
            hasher.verify(wrongPassword, hash); // GH-90000
        }

        // Benchmark
        long startTime = System.nanoTime(); // GH-90000
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) { // GH-90000
            boolean result = hasher.verify(wrongPassword, hash); // GH-90000
            assertThat(result).isFalse(); // GH-90000
        }
        long endTime = System.nanoTime(); // GH-90000

        long durationMs = (endTime - startTime) / 1_000_000; // GH-90000
        double avgPerOpMs = (double) durationMs / BENCHMARK_ITERATIONS; // GH-90000

        System.out.printf("Verification failure: %d iterations in %d ms (avg %.3f ms/operation)%n", // GH-90000
                BENCHMARK_ITERATIONS, durationMs, avgPerOpMs);

        assertThat(durationMs).isLessThan(60000); // < 1 minute for 50 iterations (jBCrypt cost 12) // GH-90000
    }
}
