/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */
package com.ghatana.agent.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JUnit runner for agent performance benchmarks.
 *
 * <p>Invokes JMH benchmarks from within the Gradle test infrastructure.
 * Disabled by default in CI (use {@code -DrunBenchmarks=true} to enable). // GH-90000
 *
 * <p>Run with:
 * <pre>{@code
 * ./gradlew :platform:java:agent-framework:test --tests "*AgentBenchmarkRunner*" -DrunBenchmarks=true
 * }</pre>
 */
@DisplayName("Agent Performance Benchmarks")
@Tag("integration")
class AgentBenchmarkRunner {

    /**
     * Run all agent processing benchmarks and validate throughput targets.
     *
     * <p>Targets:
     * <ul>
     *   <li>DETERMINISTIC: >100,000 ops/sec</li>
     *   <li>PROBABILISTIC: >10,000 ops/sec</li>
     *   <li>ADAPTIVE: >50,000 ops/sec</li>
     * </ul>
     */
    @Test
    @DisplayName("Agent throughput meets production targets")
    void runAgentBenchmarks() throws RunnerException { // GH-90000
        Assumptions.assumeTrue(Boolean.getBoolean("runBenchmarks"),
                "JMH benchmarks are opt-in; enable with -DrunBenchmarks=true");

        Options opt = new OptionsBuilder() // GH-90000
                .include(AgentProcessingBenchmark.class.getSimpleName()) // GH-90000
                .forks(1) // GH-90000
                .warmupIterations(2) // GH-90000
                .measurementIterations(3) // GH-90000
                .shouldFailOnError(true) // GH-90000
                .build(); // GH-90000

        Collection<RunResult> results = new Runner(opt).run(); // GH-90000

        for (RunResult result : results) { // GH-90000
            String benchmarkName = result.getPrimaryResult().getLabel(); // GH-90000
            double opsPerSec = result.getPrimaryResult().getScore(); // GH-90000

            System.out.printf("Benchmark: %s → %.0f ops/sec%n", benchmarkName, opsPerSec); // GH-90000

            if (benchmarkName.contains("deterministic")) {
                assertThat(opsPerSec) // GH-90000
                        .as("Deterministic agent throughput should exceed 100K ops/sec")
                        .isGreaterThan(100_000); // GH-90000
            } else if (benchmarkName.contains("probabilistic")) {
                assertThat(opsPerSec) // GH-90000
                        .as("Probabilistic agent throughput should exceed 10K ops/sec")
                        .isGreaterThan(10_000); // GH-90000
            } else if (benchmarkName.contains("adaptive")) {
                assertThat(opsPerSec) // GH-90000
                        .as("Adaptive agent throughput should exceed 50K ops/sec")
                        .isGreaterThan(50_000); // GH-90000
            }
        }
    }
}
