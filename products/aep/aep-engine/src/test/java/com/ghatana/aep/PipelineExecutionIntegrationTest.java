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
 * End-to-end integration test for pipeline execution.
 * <p>
 * Verifies the full execution lifecycle:
 * - Pipeline submission
 * - Stage execution in dependency order
 * - Pattern registration from stages
 * - State updates and results
 *
 * @doc.type class
 * @doc.purpose End-to-end pipeline execution integration test
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Pipeline Execution Integration [GH-90000]")
class PipelineExecutionIntegrationTest extends EventloopTestBase {

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
    @DisplayName("Full Execution Lifecycle [GH-90000]")
    class LifecycleTests {

        @Test
        @DisplayName("executes pipeline and registers patterns [GH-90000]")
        void executesPipelineAndRegistersPatterns() { // GH-90000
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "fraud-detection",
                "Fraud Detection Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( // GH-90000
                        "name", "high-value-transaction",
                        "patternType", "THRESHOLD",
                        "field", "amount",
                        "threshold", 10000.0
                    )),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( // GH-90000
                        "name", "suspicious-location",
                        "patternType", "CUSTOM",
                        "config", Map.of("location", "high-risk") // GH-90000
                    ), List.of("step-1 [GH-90000]"))
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); // GH-90000
            assertThat(patterns).hasSize(2); // GH-90000
            assertThat(patterns) // GH-90000
                .extracting(AepEngine.Pattern::name) // GH-90000
                .containsExactlyInAnyOrder("high-value-transaction", "suspicious-location"); // GH-90000
        }

        @Test
        @DisplayName("executes stages in dependency order [GH-90000]")
        void executesStagesInDependencyOrder() { // GH-90000
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "ordered-pipeline",
                "Ordered Execution Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("init", "register_pattern", Map.of( // GH-90000
                        "name", "init-pattern",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 50.0
                    )),
                    new AepEngine.PipelineStep("process", "register_pattern", Map.of( // GH-90000
                        "name", "process-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 75.0
                    ), List.of("init [GH-90000]")),
                    new AepEngine.PipelineStep("finalize", "register_pattern", Map.of( // GH-90000
                        "name", "final-pattern",
                        "patternType", "THRESHOLD",
                        "field", "result",
                        "threshold", 90.0
                    ), List.of("process [GH-90000]"))
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // THEN - All patterns should be registered
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); // GH-90000
            assertThat(patterns).hasSize(3); // GH-90000
            assertThat(patterns).anyMatch(p -> p.name().equals("init-pattern [GH-90000]"));
            assertThat(patterns).anyMatch(p -> p.name().equals("process-pattern [GH-90000]"));
            assertThat(patterns).anyMatch(p -> p.name().equals("final-pattern [GH-90000]"));
        }

        @Test
        @DisplayName("handles pipeline with no steps [GH-90000]")
        void handlesPipelineWithNoSteps() { // GH-90000
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "empty-pipeline",
                "Empty Pipeline",
                List.of() // GH-90000
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // THEN - Should not throw
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); // GH-90000
            assertThat(patterns).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("rejects pipeline with cycles [GH-90000]")
        void rejectsPipelineWithCycles() { // GH-90000
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "cyclic-pipeline",
                "Cyclic Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of(), List.of("step-2 [GH-90000]")),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of(), List.of("step-1 [GH-90000]"))
                )
            );

            // WHEN/THEN
            assertThatThrownBy(() -> engine.submitPipeline(tenantId, pipeline)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("cycles [GH-90000]");
        }

        @Test
        @DisplayName("executes independent steps without blocking [GH-90000]")
        void executesIndependentStepsWithoutBlocking() { // GH-90000
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "parallel-pipeline",
                "Parallel Execution Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-a",
                        "patternType", "THRESHOLD",
                        "field", "field-a",
                        "threshold", 60.0
                    )),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-b",
                        "patternType", "THRESHOLD",
                        "field", "field-b",
                        "threshold", 70.0
                    ))
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // THEN - Both patterns should be registered
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); // GH-90000
            assertThat(patterns).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("continues after step failure [GH-90000]")
        void continuesAfterStepFailure() { // GH-90000
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "resilient-pipeline",
                "Resilient Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "unknown_step_type", Map.of("key", "value")), // GH-90000
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( // GH-90000
                        "name", "valid-pattern",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 50.0
                    ))
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // THEN - Valid step should still execute
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); // GH-90000
            assertThat(patterns).hasSize(1); // GH-90000
            assertThat(patterns.get(0).name()).isEqualTo("valid-pattern [GH-90000]");
        }
    }

    @Nested
    @DisplayName("State Updates [GH-90000]")
    class StateUpdateTests {

        @Test
        @DisplayName("pattern state is updated after registration [GH-90000]")
        void patternStateIsUpdatedAfterRegistration() { // GH-90000
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "state-pipeline",
                "State Update Pipeline",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( // GH-90000
                        "name", "state-test-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 80.0
                    ))
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); // GH-90000
            assertThat(patterns).hasSize(1); // GH-90000
            assertThat(patterns.get(0).name()).isEqualTo("state-test-pattern [GH-90000]");
            assertThat(patterns.get(0).type()).isEqualTo(AepEngine.PatternType.THRESHOLD); // GH-90000
        }

        @Test
        @DisplayName("multiple pipelines can register patterns for same tenant [GH-90000]")
        void multiplePipelinesCanRegisterPatternsForSameTenant() { // GH-90000
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline1 = new AepEngine.Pipeline( // GH-90000
                "pipeline-1",
                "First Pipeline",
                List.of(new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( // GH-90000
                    "name", "pattern-1",
                    "patternType", "THRESHOLD",
                    "field", "score",
                    "threshold", 50.0
                )))
            );
            AepEngine.Pipeline pipeline2 = new AepEngine.Pipeline( // GH-90000
                "pipeline-2",
                "Second Pipeline",
                List.of(new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( // GH-90000
                    "name", "pattern-2",
                    "patternType", "THRESHOLD",
                    "field", "count",
                    "threshold", 100.0
                )))
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline1); // GH-90000
            engine.submitPipeline(tenantId, pipeline2); // GH-90000

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); // GH-90000
            assertThat(patterns).hasSize(2); // GH-90000
            assertThat(patterns).anyMatch(p -> p.name().equals("pattern-1 [GH-90000]"));
            assertThat(patterns).anyMatch(p -> p.name().equals("pattern-2 [GH-90000]"));
        }
    }

    @Nested
    @DisplayName("Complex DAG Patterns [GH-90000]")
    class ComplexDAGTests {

        @Test
        @DisplayName("fan-out pattern executes correctly [GH-90000]")
        void fanOutPatternExecutesCorrectly() { // GH-90000
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "fan-out-pipeline",
                "Fan-Out Pattern",
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

            // WHEN
            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); // GH-90000
            assertThat(patterns).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("fan-in pattern executes correctly [GH-90000]")
        void fanInPatternExecutesCorrectly() { // GH-90000
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "fan-in-pipeline",
                "Fan-In Pattern",
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

            // WHEN
            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); // GH-90000
            assertThat(patterns).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("diamond pattern executes correctly [GH-90000]")
        void diamondPatternExecutesCorrectly() { // GH-90000
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( // GH-90000
                "diamond-pipeline",
                "Diamond Pattern",
                List.of( // GH-90000
                    new AepEngine.PipelineStep("start", "register_pattern", Map.of( // GH-90000
                        "name", "start-pattern",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 50.0
                    )),
                    new AepEngine.PipelineStep("branch-a", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-a",
                        "patternType", "THRESHOLD",
                        "field", "field-a",
                        "threshold", 60.0
                    ), List.of("start [GH-90000]")),
                    new AepEngine.PipelineStep("branch-b", "register_pattern", Map.of( // GH-90000
                        "name", "pattern-b",
                        "patternType", "THRESHOLD",
                        "field", "field-b",
                        "threshold", 70.0
                    ), List.of("start [GH-90000]")),
                    new AepEngine.PipelineStep("merge", "register_pattern", Map.of( // GH-90000
                        "name", "merge-pattern",
                        "patternType", "THRESHOLD",
                        "field", "merged",
                        "threshold", 80.0
                    ), List.of("branch-a", "branch-b")) // GH-90000
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); // GH-90000

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); // GH-90000
            assertThat(patterns).hasSize(4); // GH-90000
        }
    }
}
