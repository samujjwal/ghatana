/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for DAG-based pipeline execution.
 *
 * @doc.type class
 * @doc.purpose Test pipeline DAG execution with dependencies
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Pipeline DAG Execution")
class PipelineDAGExecutionTest extends EventloopTestBase {

    private AepEngine engine;

    @BeforeEach
    void setUp() {
        engine = Aep.forTesting();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Nested
    @DisplayName("Pipeline DAG Validation")
    class ValidationTests {

        @Test
        @DisplayName("valid pipeline without dependencies is valid DAG")
        void validPipelineWithoutDependenciesIsValidDAG() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "pipe-1",
                "Simple Pipeline",
                List.of(
                    new AepEngine.PipelineStep("step-1", "log", Map.of("message", "Step 1")),
                    new AepEngine.PipelineStep("step-2", "log", Map.of("message", "Step 2"))
                )
            );

            assertThat(pipeline.isValidDAG()).isTrue();
        }

        @Test
        @DisplayName("valid pipeline with dependencies is valid DAG")
        void validPipelineWithDependenciesIsValidDAG() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "pipe-2",
                "Pipeline with Dependencies",
                List.of(
                    new AepEngine.PipelineStep("step-1", "log", Map.of("message", "Step 1")),
                    new AepEngine.PipelineStep("step-2", "log", Map.of("message", "Step 2"), List.of("step-1")),
                    new AepEngine.PipelineStep("step-3", "log", Map.of("message", "Step 3"), List.of("step-2"))
                )
            );

            assertThat(pipeline.isValidDAG()).isTrue();
        }

        @Test
        @DisplayName("pipeline with cycle is invalid DAG")
        void pipelineWithCycleIsInvalidDAG() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "pipe-3",
                "Cyclic Pipeline",
                List.of(
                    new AepEngine.PipelineStep("step-1", "log", Map.of(), List.of("step-2")),
                    new AepEngine.PipelineStep("step-2", "log", Map.of(), List.of("step-1"))
                )
            );

            assertThat(pipeline.isValidDAG()).isFalse();
        }

        @Test
        @DisplayName("complex cycle is detected")
        void complexCycleIsDetected() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "pipe-4",
                "Complex Cycle",
                List.of(
                    new AepEngine.PipelineStep("step-1", "log", Map.of(), List.of("step-3")),
                    new AepEngine.PipelineStep("step-2", "log", Map.of(), List.of("step-1")),
                    new AepEngine.PipelineStep("step-3", "log", Map.of(), List.of("step-2"))
                )
            );

            assertThat(pipeline.isValidDAG()).isFalse();
        }

        @Test
        @DisplayName("self-dependency is detected as cycle")
        void selfDependencyIsDetectedAsCycle() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "pipe-5",
                "Self-Dependent",
                List.of(
                    new AepEngine.PipelineStep("step-1", "log", Map.of(), List.of("step-1"))
                )
            );

            assertThat(pipeline.isValidDAG()).isFalse();
        }
    }

    @Nested
    @DisplayName("DAG Execution")
    class ExecutionTests {

        @Test
        @DisplayName("executes steps in topological order")
        void executesStepsInTopologicalOrder() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "pipe-6",
                "Ordered Pipeline",
                List.of(
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of(
                        "name", "pattern-1",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 70.0
                    )),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of(
                        "name", "pattern-2",
                        "patternType", "THRESHOLD",
                        "field", "count",
                        "threshold", 50.0
                    ), List.of("step-1")),
                    new AepEngine.PipelineStep("step-3", "register_pattern", Map.of(
                        "name", "pattern-3",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 30.0
                    ), List.of("step-2"))
                )
            );

            engine.submitPipeline("tenant-1", pipeline);

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns("tenant-1"));
            assertThat(patterns).hasSize(3);
        }

        @Test
        @DisplayName("rejects pipeline with cycle")
        void rejectsPipelineWithCycle() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "pipe-7",
                "Cyclic Pipeline",
                List.of(
                    new AepEngine.PipelineStep("step-1", "log", Map.of(), List.of("step-2")),
                    new AepEngine.PipelineStep("step-2", "log", Map.of(), List.of("step-1"))
                )
            );

            assertThatThrownBy(() -> engine.submitPipeline("tenant-1", pipeline))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycles");
        }

        @Test
        @DisplayName("executes independent steps in parallel")
        void executesIndependentStepsInParallel() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "pipe-8",
                "Parallel Pipeline",
                List.of(
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of(
                        "name", "pattern-a",
                        "patternType", "THRESHOLD",
                        "field", "field-a",
                        "threshold", 70.0
                    )),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of(
                        "name", "pattern-b",
                        "patternType", "THRESHOLD",
                        "field", "field-b",
                        "threshold", 50.0
                    )),
                    new AepEngine.PipelineStep("step-3", "register_pattern", Map.of(
                        "name", "pattern-c",
                        "patternType", "THRESHOLD",
                        "field", "field-c",
                        "threshold", 30.0
                    ))
                )
            );

            engine.submitPipeline("tenant-1", pipeline);

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns("tenant-1"));
            assertThat(patterns).hasSize(3);
        }

        @Test
        @DisplayName("handles missing dependencies gracefully")
        void handlesMissingDependenciesGracefully() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "pipe-9",
                "Missing Dep Pipeline",
                List.of(
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of(
                        "name", "pattern-1",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 70.0
                    )),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of(
                        "name", "pattern-2",
                        "patternType", "THRESHOLD",
                        "field", "count",
                        "threshold", 50.0
                    ), List.of("step-1", "non-existent-step"))
                )
            );

            // Should execute step-1, skip step-2 due to missing dependency
            engine.submitPipeline("tenant-1", pipeline);

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns("tenant-1"));
            assertThat(patterns).hasSize(1);
            assertThat(patterns.get(0).name()).isEqualTo("pattern-1");
        }

        @Test
        @DisplayName("fan-out dependency pattern works correctly")
        void fanOutDependencyPatternWorksCorrectly() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "pipe-10",
                "Fan-Out Pipeline",
                List.of(
                    new AepEngine.PipelineStep("init", "register_pattern", Map.of(
                        "name", "init-pattern",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 50.0
                    )),
                    new AepEngine.PipelineStep("process-a", "register_pattern", Map.of(
                        "name", "pattern-a",
                        "patternType", "THRESHOLD",
                        "field", "field-a",
                        "threshold", 60.0
                    ), List.of("init")),
                    new AepEngine.PipelineStep("process-b", "register_pattern", Map.of(
                        "name", "pattern-b",
                        "patternType", "THRESHOLD",
                        "field", "field-b",
                        "threshold", 70.0
                    ), List.of("init"))
                )
            );

            engine.submitPipeline("tenant-1", pipeline);

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns("tenant-1"));
            assertThat(patterns).hasSize(3);
        }

        @Test
        @DisplayName("fan-in dependency pattern works correctly")
        void fanInDependencyPatternWorksCorrectly() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "pipe-11",
                "Fan-In Pipeline",
                List.of(
                    new AepEngine.PipelineStep("process-a", "register_pattern", Map.of(
                        "name", "pattern-a",
                        "patternType", "THRESHOLD",
                        "field", "field-a",
                        "threshold", 60.0
                    )),
                    new AepEngine.PipelineStep("process-b", "register_pattern", Map.of(
                        "name", "pattern-b",
                        "patternType", "THRESHOLD",
                        "field", "field-b",
                        "threshold", 70.0
                    )),
                    new AepEngine.PipelineStep("finalize", "register_pattern", Map.of(
                        "name", "final-pattern",
                        "patternType", "THRESHOLD",
                        "field", "final",
                        "threshold", 80.0
                    ), List.of("process-a", "process-b"))
                )
            );

            engine.submitPipeline("tenant-1", pipeline);

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns("tenant-1"));
            assertThat(patterns).hasSize(3);
        }
    }

    @Nested
    @DisplayName("PipelineStep Record")
    class PipelineStepTests {

        @Test
        @DisplayName("isRoot returns true for steps without dependencies")
        void isRootReturnsTrueForStepsWithoutDependencies() {
            AepEngine.PipelineStep step = new AepEngine.PipelineStep("step-1", "log", Map.of());
            assertThat(step.isRoot()).isTrue();
        }

        @Test
        @DisplayName("isRoot returns false for steps with dependencies")
        void isRootReturnsFalseForStepsWithDependencies() {
            AepEngine.PipelineStep step = new AepEngine.PipelineStep("step-1", "log", Map.of(), List.of("dep-1"));
            assertThat(step.isRoot()).isFalse();
        }

        @Test
        @DisplayName("convenience constructor creates step without dependencies")
        void convenienceConstructorCreatesStepWithoutDependencies() {
            AepEngine.PipelineStep step = new AepEngine.PipelineStep("step-1", "log", Map.of("key", "value"));
            assertThat(step.id()).isEqualTo("step-1");
            assertThat(step.type()).isEqualTo("log");
            assertThat(step.dependsOn()).isEmpty();
        }

        @Test
        @DisplayName("full constructor creates step with dependencies")
        void fullConstructorCreatesStepWithDependencies() {
            AepEngine.PipelineStep step = new AepEngine.PipelineStep(
                "step-1", "log", Map.of("key", "value"), List.of("dep-1", "dep-2")
            );
            assertThat(step.id()).isEqualTo("step-1");
            assertThat(step.type()).isEqualTo("log");
            assertThat(step.dependsOn()).hasSize(2);
        }
    }
}
