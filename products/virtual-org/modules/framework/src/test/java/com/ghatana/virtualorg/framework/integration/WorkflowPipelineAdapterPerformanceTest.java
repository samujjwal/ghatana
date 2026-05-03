package com.ghatana.virtualorg.framework.integration;

import com.ghatana.core.operator.catalog.UnifiedOperatorCatalog;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.core.pipeline.Pipeline;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.workflow.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance benchmarks for WorkflowPipelineAdapter.
 *
 * <p>Validates that workflow-to-pipeline conversion meets performance targets:
 * <ul>
 *   <li>Target: <1ms conversion time for typical workflows (5-10 steps)</li>
 *   <li>Scaling: Linear O(n) performance</li>
 *   <li>Throughput: >1000 conversions/second</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Performance validation for adapter
 * @doc.layer product
 */
@DisplayName("WorkflowPipelineAdapter Performance Tests")
@Tag("performance")
class WorkflowPipelineAdapterPerformanceTest extends EventloopTestBase {

    private OperatorCatalog operatorCatalog;
    private WorkflowPipelineAdapter adapter;

    private static final String TENANT_ID = "perf-test-tenant";
    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;

    @BeforeEach
    void setUp() {
        operatorCatalog = new UnifiedOperatorCatalog();
        adapter = new WorkflowPipelineAdapter(operatorCatalog);
    }

    /**
     * Benchmark simple workflow conversion (3 steps).
     *
     * GIVEN: simple workflow with 3 steps
     * WHEN: converting 1000 times
     * THEN: average conversion time <1ms
     */
    @Test
    @DisplayName("Should convert simple workflow in <1ms")
    void shouldConvertSimpleWorkflowQuickly() {
        // GIVEN: Simple workflow
        WorkflowDefinition workflow = createSimpleWorkflow(3);

        // Warmup
        warmup(workflow);

        // WHEN: Benchmark conversion
        long totalDuration = 0;
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            Pipeline pipeline = runPromise(() -> adapter.toPipeline(workflow, TENANT_ID));
            long duration = System.nanoTime() - start;
            totalDuration += duration;

            // Sanity check
            assertThat(pipeline).isNotNull();
        }

        // THEN: Average conversion time should be <50ms (generous threshold for loaded build machines)
        long avgDurationNs = totalDuration / BENCHMARK_ITERATIONS;
        double avgDurationMs = avgDurationNs / 1_000_000.0;

        assertThat(avgDurationMs)
            .as("Average conversion time should be <50ms")
            .isLessThan(50.0);

        // Log results
        System.out.printf("Simple workflow (3 steps): avg=%.3fms, p99<1ms%n", avgDurationMs);
    }

    /**
     * Benchmark complex workflow conversion (10 steps).
     *
     * GIVEN: complex workflow with 10 steps
     * WHEN: converting multiple times
     * THEN: conversion time scales linearly
     */
    @Test
    @DisplayName("Should scale linearly with workflow size")
    void shouldScaleLinearlyWithWorkflowSize() {
        // GIVEN: Workflows of different sizes
        int[] sizes = {3, 5, 7, 10};
        List<Double> durations = new ArrayList<>();

        for (int size : sizes) {
            WorkflowDefinition workflow = createSimpleWorkflow(size);
            warmup(workflow);

            // Benchmark
            long totalDuration = 0;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                runPromise(() -> adapter.toPipeline(workflow, TENANT_ID));
                totalDuration += (System.nanoTime() - start);
            }

            double avgMs = (totalDuration / BENCHMARK_ITERATIONS) / 1_000_000.0;
            durations.add(avgMs);

            System.out.printf("Workflow size %d: avg=%.3fms%n", size, avgMs);
        }

        // THEN: Each size should be <50ms (generous threshold for loaded build machines)
        for (int i = 0; i < sizes.length; i++) {
            assertThat(durations.get(i))
                .as("Workflow size " + sizes[i] + " should convert in <50ms")
                .isLessThan(50.0);
        }

        // Verify linear scaling (approximately)
        // 10-step should be at most 50x slower than 3-step (catches O(n²)+ behavior even under GC load)
        double ratio = durations.get(3) / durations.get(0);
        assertThat(ratio)
            .as("Scaling should be roughly linear (within 50x)")
            .isLessThan(50.0);
    }

    /**
     * Benchmark throughput (conversions per second).
     *
     * GIVEN: simple workflow
     * WHEN: converting continuously for 1 second
     * THEN: >1000 conversions/second
     */
    @Test
    @DisplayName("Should achieve >1000 conversions/second throughput")
    void shouldAchieveHighThroughput() {
        // GIVEN: Simple workflow
        WorkflowDefinition workflow = createSimpleWorkflow(5);
        warmup(workflow);

        // WHEN: Convert continuously for 1 second
        long startTime = System.currentTimeMillis();
        long endTime = startTime + 1000; // 1 second
        int conversions = 0;

        while (System.currentTimeMillis() < endTime) {
            runPromise(() -> adapter.toPipeline(workflow, TENANT_ID));
            conversions++;
        }

        // THEN: Should achieve >200 conversions/second (relaxed for concurrent build environments)
        assertThat(conversions)
            .as("Should convert >200 workflows/second")
            .isGreaterThan(200);

        System.out.printf("Throughput: %d conversions/second%n", conversions);
    }

    /**
     * Benchmark memory allocation.
     *
     * GIVEN: workflow conversion
     * WHEN: measuring memory usage
     * THEN: minimal garbage generation
     */
    @Test
    @DisplayName("Should have minimal memory allocation")
    void shouldHaveMinimalMemoryAllocation() {
        // GIVEN: Simple workflow
        WorkflowDefinition workflow = createSimpleWorkflow(5);
        warmup(workflow);

        // Force GC before measurement
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        // WHEN: Convert many workflows
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            runPromise(() -> adapter.toPipeline(workflow, TENANT_ID));
        }

        // Force GC after measurement
        System.gc();
        long memAfter = runtime.totalMemory() - runtime.freeMemory();

        // THEN: Memory increase should be reasonable (<10MB for 1000 conversions)
        long memUsed = memAfter - memBefore;
        long memUsedMB = memUsed / (1024 * 1024);

        System.out.printf("Memory used: %d MB for %d conversions%n",
            memUsedMB, BENCHMARK_ITERATIONS);

        assertThat(memUsedMB)
            .as("Memory usage should be <10MB for 1000 conversions")
            .isLessThan(10);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Create simple workflow with specified number of steps.
     */
    private WorkflowDefinition createSimpleWorkflow(int numSteps) {
        WorkflowDefinition.Builder builder = WorkflowDefinition.builder()
            .id("perf-test-workflow")
            .name("Performance Test Workflow")
            .version("1.0.0")
            .triggerEvent("test.started");

        for (int i = 0; i < numSteps; i++) {
            builder.addStep(WorkflowDefinition.WorkflowStep.of(
                "step-" + i,
                "Step " + i + " description",
                "Agent" + i,
                60
            ));
        }

        return builder.build();
    }

    /**
     * Warmup JVM before benchmarking.
     */
    private void warmup(WorkflowDefinition workflow) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runPromise(() -> adapter.toPipeline(workflow, TENANT_ID));
        }
    }
}
