/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.core.pipeline.benchmark;

import com.ghatana.core.operator.*;
import com.ghatana.core.operator.catalog.DefaultOperatorCatalog;
import com.ghatana.core.pipeline.*;
import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JMH performance benchmarks for pipeline execution throughput and latency.
 *
 * <p>Production targets:
 * <ul>
 *   <li>Single-stage pipeline: p99 &lt;5ms</li>
 *   <li>3-stage linear pipeline: p99 &lt;15ms</li>
 *   <li>5-stage DAG pipeline: p99 &lt;50ms</li>
 *   <li>Pipeline throughput: &gt;10K executions/sec (single-stage)</li>
 * </ul>
 *
 * Run via {@link PipelineBenchmarkRunner} JUnit test.
 */
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class PipelineExecutionBenchmark {

    private PipelineExecutionEngine engine;
    private DefaultOperatorCatalog catalog;
    private Pipeline singleStagePipeline;
    private Pipeline threeStageLinear;
    private Pipeline fiveStageDAG;
    private Event testEvent;

    private static OperatorId opId(String name) {
        return OperatorId.of("bench", "stream", name, "1.0.0");
    }

    @Setup(Level.Trial)
    public void setup() {
        engine = new PipelineExecutionEngine();
        catalog = new DefaultOperatorCatalog();

        // Register fast pass-through operators (initialize + start each)
        for (int i = 1; i <= 5; i++) {
            NoopOperator op = new NoopOperator(opId("noop-" + i), "noop-" + i);
            op.initialize(OperatorConfig.empty()).getResult();
            op.start().getResult();
            catalog.register(op).getResult();
        }

        testEvent = Event.builder()
                .type("benchmark.event")
                .payload(Map.of("key", "value", "timestamp", System.currentTimeMillis()))
                .build();

        // Single-stage pipeline
        DefaultPipeline.DefaultPipelineBuilder singleBuilder = DefaultPipeline.builder("bench-single", "1.0.0");
        singleBuilder.executionEngine(engine, catalog);
        singleStagePipeline = singleBuilder
                .name("Single Stage Benchmark")
                .stage("s1", opId("noop-1"))
                .build();

        // 3-stage linear pipeline
        DefaultPipeline.DefaultPipelineBuilder linearBuilder = DefaultPipeline.builder("bench-3-linear", "1.0.0");
        linearBuilder.executionEngine(engine, catalog);
        threeStageLinear = linearBuilder
                .name("Three Stage Linear Benchmark")
                .stage("s1", opId("noop-1"))
                .stage("s2", opId("noop-2"))
                .stage("s3", opId("noop-3"))
                .edge("s1", "s2")
                .edge("s2", "s3")
                .build();

        // 5-stage DAG: s1 → s2 → s4, s1 → s3 → s4, s4 → s5
        DefaultPipeline.DefaultPipelineBuilder dagBuilder = DefaultPipeline.builder("bench-5-dag", "1.0.0");
        dagBuilder.executionEngine(engine, catalog);
        fiveStageDAG = dagBuilder
                .name("Five Stage DAG Benchmark")
                .stage("s1", opId("noop-1"))
                .stage("s2", opId("noop-2"))
                .stage("s3", opId("noop-3"))
                .stage("s4", opId("noop-4"))
                .stage("s5", opId("noop-5"))
                .edge("s1", "s2")
                .edge("s1", "s3")
                .edge("s2", "s4")
                .edge("s3", "s4")
                .edge("s4", "s5")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Benchmarks
    // ═══════════════════════════════════════════════════════════════════════════

    @Benchmark
    public void singleStageExecution(Blackhole bh) {
        PipelineExecutionResult result = singleStagePipeline.execute(testEvent).getResult();
        bh.consume(result);
    }

    @Benchmark
    public void threeStageLinearExecution(Blackhole bh) {
        PipelineExecutionResult result = threeStageLinear.execute(testEvent).getResult();
        bh.consume(result);
    }

    @Benchmark
    public void fiveStageDAGExecution(Blackhole bh) {
        PipelineExecutionResult result = fiveStageDAG.execute(testEvent).getResult();
        bh.consume(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test Operator
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Minimal pass-through operator for benchmarking pipeline overhead.
     */
    static class NoopOperator extends AbstractOperator {
        NoopOperator(OperatorId id, String name) {
            super(id, OperatorType.STREAM, name, "Noop benchmark operator", List.of("test"), null);
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            return Promise.of(OperatorResult.of(event));
        }

        @Override
        public Event toEvent() {
            return Event.builder().type("operator.registered")
                    .payload(Map.of("id", getId().toString())).build();
        }
    }
}
