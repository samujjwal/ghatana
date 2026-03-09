/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.agent.benchmark;

import org.junit.jupiter.api.DisplayName;
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
 * Disabled by default in CI (use {@code -DrunBenchmarks=true} to enable).
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
    void runAgentBenchmarks() throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AgentProcessingBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .shouldFailOnError(true)
                .build();

        Collection<RunResult> results = new Runner(opt).run();

        for (RunResult result : results) {
            String benchmarkName = result.getPrimaryResult().getLabel();
            double opsPerSec = result.getPrimaryResult().getScore();

            System.out.printf("Benchmark: %s → %.0f ops/sec%n", benchmarkName, opsPerSec);

            if (benchmarkName.contains("deterministic")) {
                assertThat(opsPerSec)
                        .as("Deterministic agent throughput should exceed 100K ops/sec")
                        .isGreaterThan(100_000);
            } else if (benchmarkName.contains("probabilistic")) {
                assertThat(opsPerSec)
                        .as("Probabilistic agent throughput should exceed 10K ops/sec")
                        .isGreaterThan(10_000);
            } else if (benchmarkName.contains("adaptive")) {
                assertThat(opsPerSec)
                        .as("Adaptive agent throughput should exceed 50K ops/sec")
                        .isGreaterThan(50_000);
            }
        }
    }
}
