/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("Pipeline DAG Execution [GH-90000]")
class PipelineDAGExecutionTest extends EventloopTestBase {

    private AepEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = Aep.forTesting(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Pipeline DAG Validation [GH-90000]")
    class ValidationTests {

        @Test
        @DisplayName("valid pipeline without dependencies is valid DAG [GH-90000]")
        void validPipelineWithoutDependenciesIsValidDAG() { // GH-90000
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "pipe-1",
                "Simple Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "log", Map.of("message", "Step 1")), // GH-90000
                    new AepEngine.PipelineStep("step-2", "log", Map.of("message", "Step 2")) // GH-90000
                )
            );

            assertThat(pipeline.isValidDAG()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("valid pipeline with dependencies is valid DAG [GH-90000]")
        void validPipelineWithDependenciesIsValidDAG() { // GH-90000
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "pipe-2",
                "Pipeline with Dependencies",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "log", Map.of("message", "Step 1")), // GH-90000
                    new AepEngine.PipelineStep("step-2", "log", Map.of("message", "Step 2"), List.of("step-1 [GH-90000]")),
                    new AepEngine.PipelineStep("step-3", "log", Map.of("message", "Step 3"), List.of("step-2 [GH-90000]"))
                )
            );

            assertThat(pipeline.isValidDAG()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("pipeline with cycle is invalid DAG [GH-90000]")
        void pipelineWithCycleIsInvalidDAG() { // GH-90000
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "pipe-3",
                "Cyclic Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "log", Map.of(), List.of("step-2 [GH-90000]")),
                    new AepEngine.PipelineStep("step-2", "log", Map.of(), List.of("step-1 [GH-90000]"))
                )
            );

            assertThat(pipeline.isValidDAG()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("complex cycle is detected [GH-90000]")
        void complexCycleIsDetected() { // GH-90000
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "pipe-4",
                "Complex Cycle",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "log", Map.of(), List.of("step-3 [GH-90000]")),
                    new AepEngine.PipelineStep("step-2", "log", Map.of(), List.of("step-1 [GH-90000]")),
                    new AepEngine.PipelineStep("step-3", "log", Map.of(), List.of("step-2 [GH-90000]"))
                )
            );

            assertThat(pipeline.isValidDAG()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("self-dependency is detected as cycle [GH-90000]")
        void selfDependencyIsDetectedAsCycle() { // GH-90000
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "pipe-5",
                "Self-Dependent",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "log", Map.of(), List.of("step-1 [GH-90000]"))
                )
            );

            assertThat(pipeline.isValidDAG()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("DAG Execution [GH-90000]")
    class ExecutionTests {

        @Test
        @DisplayName("executes steps in topological order [GH-90000]")
        void executesStepsInTopologicalOrder() { // GH-90000
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "pipe-6",
                "Ordered Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-1",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 70.0
                    )),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-2",
                        "patternType", "THRESHOLD",
                        "field", "count",
                        "threshold", 50.0
                    ), List.of("step-1 [GH-90000]")),
                    new AepEngine.PipelineStep("step-3", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-3",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 30.0
                    ), List.of("step-2 [GH-90000]"))
                )
            );

            engine.submitPipeline("tenant-1", pipeline); // GH-90000

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns("tenant-1 [GH-90000]"));
            assertThat(patterns).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("rejects pipeline with cycle [GH-90000]")
        void rejectsPipelineWithCycle() { // GH-90000
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "pipe-7",
                "Cyclic Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "log", Map.of(), List.of("step-2 [GH-90000]")),
                    new AepEngine.PipelineStep("step-2", "log", Map.of(), List.of("step-1 [GH-90000]"))
                )
            );

            assertThatThrownBy(() -> engine.submitPipeline("tenant-1", pipeline)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("cycles [GH-90000]");
        }

        @Test
        @DisplayName("executes independent steps in parallel [GH-90000]")
        void executesIndependentStepsInParallel() { // GH-90000
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "pipe-8",
                "Parallel Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-a",
                        "patternType", "THRESHOLD",
                        "field", "field-a",
                        "threshold", 70.0
                    )),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-b",
                        "patternType", "THRESHOLD",
                        "field", "field-b",
                        "threshold", 50.0
                    )),
                    new AepEngine.PipelineStep("step-3", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-c",
                        "patternType", "THRESHOLD",
                        "field", "field-c",
                        "threshold", 30.0
                    ))
                )
            );

            engine.submitPipeline("tenant-1", pipeline); // GH-90000

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns("tenant-1 [GH-90000]"));
            assertThat(patterns).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("handles missing dependencies gracefully [GH-90000]")
        void handlesMissingDependenciesGracefully() { // GH-90000
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "pipe-9",
                "Missing Dep Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-1",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 70.0
                    )),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-2",
                        "patternType", "THRESHOLD",
                        "field", "count",
                        "threshold", 50.0
                    ), List.of("step-1", "non-existent-step")) // GH-90000
                )
            );

            // Should execute step-1, skip step-2 due to missing dependency
            engine.submitPipeline("tenant-1", pipeline); // GH-90000

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns("tenant-1 [GH-90000]"));
            assertThat(patterns).hasSize(1); // GH-90000
            assertThat(patterns.get(0).name()).isEqualTo("pattern-1 [GH-90000]");
        }

        @Test
        @DisplayName("fan-out dependency pattern works correctly [GH-90000]")
        void fanOutDependencyPatternWorksCorrectly() { // GH-90000
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "pipe-10",
                "Fan-Out Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("init", "register_pattern", Map.of( // GH-90000
                        "name", "init-pattern",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 50.0
                    )),
                    new AepEngine.PipelineStep("process-a", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-a",
                        "patternType", "THRESHOLD",
                        "field", "field-a",
                        "threshold", 60.0
                    ), List.of("init [GH-90000]")),
                    new AepEngine.PipelineStep("process-b", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-b",
                        "patternType", "THRESHOLD",
                        "field", "field-b",
                        "threshold", 70.0
                    ), List.of("init [GH-90000]"))
                )
            );

            engine.submitPipeline("tenant-1", pipeline); // GH-90000

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns("tenant-1 [GH-90000]"));
            assertThat(patterns).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("fan-in dependency pattern works correctly [GH-90000]")
        void fanInDependencyPatternWorksCorrectly() { // GH-90000
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "pipe-11",
                "Fan-In Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("process-a", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-a",
                        "patternType", "THRESHOLD",
                        "field", "field-a",
                        "threshold", 60.0
                    )),
                    new AepEngine.PipelineStep("process-b", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-b",
                        "patternType", "THRESHOLD",
                        "field", "field-b",
                        "threshold", 70.0
                    )),
                    new AepEngine.PipelineStep("finalize", "register_pattern", Map.of( // GH-90000
                        "name", "final-pattern",
                        "patternType", "THRESHOLD",
                        "field", "final",
                        "threshold", 80.0
                    ), List.of("process-a", "process-b")) // GH-90000
                )
            );

            engine.submitPipeline("tenant-1", pipeline); // GH-90000

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns("tenant-1 [GH-90000]"));
            assertThat(patterns).hasSize(3); // GH-90000
        }
    }

    @Nested
    @DisplayName("PipelineStep Record [GH-90000]")
    class PipelineStepTests {

        @Test
        @DisplayName("isRoot returns true for steps without dependencies [GH-90000]")
        void isRootReturnsTrueForStepsWithoutDependencies() { // GH-90000
            AepEngine.PipelineStep step = new AepEngine.PipelineStep("step-1", "log", Map.of()); // GH-90000
            assertThat(step.isRoot()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isRoot returns false for steps with dependencies [GH-90000]")
        void isRootReturnsFalseForStepsWithDependencies() { // GH-90000
            AepEngine.PipelineStep step = new AepEngine.PipelineStep("step-1", "log", Map.of(), List.of("dep-1 [GH-90000]"));
            assertThat(step.isRoot()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("convenience constructor creates step without dependencies [GH-90000]")
        void convenienceConstructorCreatesStepWithoutDependencies() { // GH-90000
            AepEngine.PipelineStep step = new AepEngine.PipelineStep("step-1", "log", Map.of("key", "value")); // GH-90000
            assertThat(step.id()).isEqualTo("step-1 [GH-90000]");
            assertThat(step.type()).isEqualTo("log [GH-90000]");
            assertThat(step.dependsOn()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("full constructor creates step with dependencies [GH-90000]")
        void fullConstructorCreatesStepWithDependencies() { // GH-90000
            AepEngine.PipelineStep step = new AepEngine.PipelineStep( // GH-90000
                "step-1", "log", Map.of("key", "value"), List.of("dep-1", "dep-2") // GH-90000
            );
            assertThat(step.id()).isEqualTo("step-1 [GH-90000]");
            assertThat(step.type()).isEqualTo("log [GH-90000]");
            assertThat(step.dependsOn()).hasSize(2); // GH-90000
        }
    }
}
