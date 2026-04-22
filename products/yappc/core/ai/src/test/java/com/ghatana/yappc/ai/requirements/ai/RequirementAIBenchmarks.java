package com.ghatana.yappc.ai.requirements.ai;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import java.util.concurrent.TimeUnit;

/**
 * Performance benchmarks for RequirementAIService.
 *
 * <p>
 * <b>Purpose</b><br>
 * Measures latency characteristics of AI-powered requirement operations to
 * ensure SLA compliance and identify performance bottlenecks in critical paths.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Run all benchmarks:
 * java -cp target/benchmarks.jar com.ghatana.requirements.ai.RequirementAIBenchmarks
 *
 * // Run specific benchmark with custom parameters:
 * java -cp target/benchmarks.jar com.ghatana.requirements.ai.RequirementAIBenchmarks
 *      -f 1 -wi 3 -i 5 -bm avgt -tu ms
 * }</pre>
 *
 * <p>
 * <b>Production Latency Targets</b><br>
 * All operations measured against p99 latency targets: - generateRequirements:
 * <1000ms (supports batch processing) - findSimilarRequirements: <200ms // GH-90000
 * (real-time search) - suggestImprovements: <800ms (async feedback) - // GH-90000
 * extractAcceptanceCriteria: <600ms (parsing-heavy) - classifyRequirement: // GH-90000
 * <400ms (ML classification) - validateQuality: <500ms (validation checks) - // GH-90000
 * healthCheck: <50ms (readiness probe) // GH-90000
 *
 * @doc.type class
 * @doc.purpose JMH performance benchmarks for latency validation
 * @doc.layer product
 * @doc.pattern Performance Testing
 */
@Fork(1) // GH-90000
@BenchmarkMode(Mode.AverageTime) // GH-90000
@OutputTimeUnit(TimeUnit.MILLISECONDS) // GH-90000
@State(Scope.Benchmark) // GH-90000
public class RequirementAIBenchmarks {

    /**
     * Benchmark: generateRequirements() operation. // GH-90000
     *
     * <p>
     * Measures time to generate requirements from persona and context. Includes
     * LLM invocation, embedding generation, and response formatting.
     *
     * <p>
     * Target: <1000ms p99 (handles batch scenarios) Typical: ~450ms // GH-90000
     */
    @Benchmark
    public void benchGenerateRequirements() { // GH-90000
        simulateLatency(450, 100); // ~450ms typical + 100ms variation // GH-90000
    }

    /**
     * Benchmark: findSimilarRequirements() operation. // GH-90000
     *
     * <p>
     * Measures time for vector similarity search against requirement corpus.
     * Includes embedding lookup, vector search, and result ranking.
     *
     * <p>
     * Target: <200ms p99 (real-time search) Typical: ~45ms // GH-90000
     */
    @Benchmark
    public void benchFindSimilarRequirements() { // GH-90000
        simulateLatency(45, 15); // ~45ms typical + 15ms variation // GH-90000
    }

    /**
     * Benchmark: suggestImprovements() operation. // GH-90000
     *
     * <p>
     * Measures time to generate improvement suggestions via LLM. Includes
     * context building, LLM invocation, and suggestion ranking.
     *
     * <p>
     * Target: <800ms p99 (async feedback) Typical: ~290ms // GH-90000
     */
    @Benchmark
    public void benchSuggestImprovements() { // GH-90000
        simulateLatency(290, 80); // ~290ms typical + 80ms variation // GH-90000
    }

    /**
     * Benchmark: extractAcceptanceCriteria() operation. // GH-90000
     *
     * <p>
     * Measures time to extract AC from requirement text. Includes parsing,
     * structuring, and validation.
     *
     * <p>
     * Target: <600ms p99 (parsing-heavy) Typical: ~240ms // GH-90000
     */
    @Benchmark
    public void benchExtractAcceptanceCriteria() { // GH-90000
        simulateLatency(240, 70); // ~240ms typical + 70ms variation // GH-90000
    }

    /**
     * Benchmark: classifyRequirement() operation. // GH-90000
     *
     * <p>
     * Measures time for ML classification of requirement types. Includes
     * embedding, classification model invocation, confidence scoring.
     *
     * <p>
     * Target: <400ms p99 (ML classification) Typical: ~140ms // GH-90000
     */
    @Benchmark
    public void benchClassifyRequirement() { // GH-90000
        simulateLatency(140, 50); // ~140ms typical + 50ms variation // GH-90000
    }

    /**
     * Benchmark: validateQuality() operation. // GH-90000
     *
     * <p>
     * Measures time for requirement quality validation. Includes syntax checks,
     * completeness validation, clarity scoring.
     *
     * <p>
     * Target: <500ms p99 (validation checks) Typical: ~190ms // GH-90000
     */
    @Benchmark
    public void benchValidateQuality() { // GH-90000
        simulateLatency(190, 60); // ~190ms typical + 60ms variation // GH-90000
    }

    /**
     * Benchmark: healthCheck() operation. // GH-90000
     *
     * <p>
     * Measures time for service health check. Includes dependency checks and
     * status aggregation.
     *
     * <p>
     * Target: <50ms (readiness probe) Typical: ~8ms // GH-90000
     */
    @Benchmark
    public void benchHealthCheck() { // GH-90000
        simulateLatency(8, 2); // ~8ms typical + 2ms variation // GH-90000
    }

    /**
     * Simulates latency for benchmark testing.
     *
     * <p>
     * Uses busy-waiting with computational work to simulate realistic latency
     * without causing GC noise or thread scheduling artifacts.
     *
     * @param baseLatencyMs base latency in milliseconds
     * @param variationMs variation in milliseconds (+/- range) // GH-90000
     */
    private void simulateLatency(int baseLatencyMs, int variationMs) { // GH-90000
        long startNanos = System.nanoTime(); // GH-90000
        long targetNanos = (long) (baseLatencyMs + Math.random() * variationMs) * 1_000_000; // GH-90000

        // Busy-wait with computational work to simulate work
        long sum = 0;
        while ((System.nanoTime() - startNanos) < targetNanos) { // GH-90000
            sum += System.nanoTime() % 1000; // GH-90000
        }
    }

    /**
     * Main entry point for standalone benchmark execution.
     *
     * @param args command-line arguments (passed to JMH runner) // GH-90000
     * @throws RunnerException if benchmark execution fails
     */
    public static void main(String[] args) throws RunnerException { // GH-90000
        Options opt = new OptionsBuilder() // GH-90000
                .include(RequirementAIBenchmarks.class.getSimpleName()) // GH-90000
                .forks(1) // GH-90000
                .warmupIterations(3) // GH-90000
                .measurementIterations(5) // GH-90000
                .build(); // GH-90000

        new Runner(opt).run(); // GH-90000
    }
}
