/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.core.pipeline.benchmark;

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
 * JUnit runner for pipeline performance benchmarks.
 *
 * <p>Disabled by default in CI. Run manually:
 * <pre>{@code
 * ./gradlew :products:aep:platform:test --tests "*PipelineBenchmarkRunner*" -DrunBenchmarks=true
 * }</pre>
 */
@DisplayName("Pipeline Performance Benchmarks")
@Tag("integration")
class PipelineBenchmarkRunner {

    @Test
    @DisplayName("Pipeline execution meets latency targets")
    void runPipelineBenchmarks() throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(PipelineExecutionBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .shouldFailOnError(true)
                .build();

        Collection<RunResult> results = new Runner(opt).run();

        for (RunResult result : results) {
            String benchmarkName = result.getPrimaryResult().getLabel();
            double score = result.getPrimaryResult().getScore();
            String unit = result.getPrimaryResult().getScoreUnit();

            System.out.printf("Benchmark: %s → %.2f %s%n", benchmarkName, score, unit);

            // Throughput assertions (ops/sec mode results)
            if (unit.contains("op/s") && benchmarkName.contains("singleStage")) {
                assertThat(score)
                        .as("Single-stage throughput should exceed 10K ops/sec")
                        .isGreaterThan(10_000);
            }
        }
    }
}
