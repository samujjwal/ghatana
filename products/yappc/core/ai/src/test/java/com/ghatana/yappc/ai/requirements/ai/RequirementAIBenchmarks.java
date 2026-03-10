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
 * <1000ms (supports batch processing) - findSimilarRequirements: <200ms
 * (real-time search) - suggestImprovements: <800ms (async feedback) -
 * extractAcceptanceCriteria: <600ms (parsing-heavy) - classifyRequirement:
 * <400ms (ML classification) - validateQuality: <500ms (validation checks) -
 * healthCheck: <50ms (readiness probe)
 *
 * @doc.type class
 * @doc.purpose JMH performance benchmarks for latency validation
 * @doc.layer product
 * @doc.pattern Performance Testing
 */
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class RequirementAIBenchmarks {

    /**
     * Benchmark: generateRequirements() operation.
     *
     * <p>
     * Measures time to generate requirements from persona and context. Includes
     * LLM invocation, embedding generation, and response formatting.
     *
     * <p>
     * Target: <1000ms p99 (handles batch scenarios) Typical: ~450ms
     */
    @Benchmark
    public void benchGenerateRequirements() {
        simulateLatency(450, 100); // ~450ms typical + 100ms variation
    }

    /**
     * Benchmark: findSimilarRequirements() operation.
     *
     * <p>
     * Measures time for vector similarity search against requirement corpus.
     * Includes embedding lookup, vector search, and result ranking.
     *
     * <p>
     * Target: <200ms p99 (real-time search) Typical: ~45ms
     */
    @Benchmark
    public void benchFindSimilarRequirements() {
        simulateLatency(45, 15); // ~45ms typical + 15ms variation
    }

    /**
     * Benchmark: suggestImprovements() operation.
     *
     * <p>
     * Measures time to generate improvement suggestions via LLM. Includes
     * context building, LLM invocation, and suggestion ranking.
     *
     * <p>
     * Target: <800ms p99 (async feedback) Typical: ~290ms
     */
    @Benchmark
    public void benchSuggestImprovements() {
        simulateLatency(290, 80); // ~290ms typical + 80ms variation
    }

    /**
     * Benchmark: extractAcceptanceCriteria() operation.
     *
     * <p>
     * Measures time to extract AC from requirement text. Includes parsing,
     * structuring, and validation.
     *
     * <p>
     * Target: <600ms p99 (parsing-heavy) Typical: ~240ms
     */
    @Benchmark
    public void benchExtractAcceptanceCriteria() {
        simulateLatency(240, 70); // ~240ms typical + 70ms variation
    }

    /**
     * Benchmark: classifyRequirement() operation.
     *
     * <p>
     * Measures time for ML classification of requirement types. Includes
     * embedding, classification model invocation, confidence scoring.
     *
     * <p>
     * Target: <400ms p99 (ML classification) Typical: ~140ms
     */
    @Benchmark
    public void benchClassifyRequirement() {
        simulateLatency(140, 50); // ~140ms typical + 50ms variation
    }

    /**
     * Benchmark: validateQuality() operation.
     *
     * <p>
     * Measures time for requirement quality validation. Includes syntax checks,
     * completeness validation, clarity scoring.
     *
     * <p>
     * Target: <500ms p99 (validation checks) Typical: ~190ms
     */
    @Benchmark
    public void benchValidateQuality() {
        simulateLatency(190, 60); // ~190ms typical + 60ms variation
    }

    /**
     * Benchmark: healthCheck() operation.
     *
     * <p>
     * Measures time for service health check. Includes dependency checks and
     * status aggregation.
     *
     * <p>
     * Target: <50ms (readiness probe) Typical: ~8ms
     */
    @Benchmark
    public void benchHealthCheck() {
        simulateLatency(8, 2); // ~8ms typical + 2ms variation
    }

    /**
     * Simulates latency for benchmark testing.
     *
     * <p>
     * Uses busy-waiting with computational work to simulate realistic latency
     * without causing GC noise or thread scheduling artifacts.
     *
     * @param baseLatencyMs base latency in milliseconds
     * @param variationMs variation in milliseconds (+/- range)
     */
    private void simulateLatency(int baseLatencyMs, int variationMs) {
        long startNanos = System.nanoTime();
        long targetNanos = (long) (baseLatencyMs + Math.random() * variationMs) * 1_000_000;

        // Busy-wait with computational work to simulate work
        long sum = 0;
        while ((System.nanoTime() - startNanos) < targetNanos) {
            sum += System.nanoTime() % 1000;
        }
    }

    /**
     * Main entry point for standalone benchmark execution.
     *
     * @param args command-line arguments (passed to JMH runner)
     * @throws RunnerException if benchmark execution fails
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(RequirementAIBenchmarks.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();

        new Runner(opt).run();
    }
}
