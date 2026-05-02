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
@DisplayName("Pipeline Execution Integration")
class PipelineExecutionIntegrationTest extends EventloopTestBase {

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
    @DisplayName("Full Execution Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("executes pipeline and registers patterns")
        void executesPipelineAndRegistersPatterns() { 
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( 
                "fraud-detection",
                "Fraud Detection Pipeline",
                List.of( 
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( 
                        "name", "high-value-transaction",
                        "patternType", "THRESHOLD",
                        "field", "amount",
                        "threshold", 10000.0
                    )),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( 
                        "name", "suspicious-location",
                        "patternType", "CUSTOM",
                        "config", Map.of("location", "high-risk") 
                    ), List.of("step-1"))
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); 

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); 
            assertThat(patterns).hasSize(2); 
            assertThat(patterns) 
                .extracting(AepEngine.Pattern::name) 
                .containsExactlyInAnyOrder("high-value-transaction", "suspicious-location"); 
        }

        @Test
        @DisplayName("executes stages in dependency order")
        void executesStagesInDependencyOrder() { 
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( 
                "ordered-pipeline",
                "Ordered Execution Pipeline",
                List.of( 
                    new AepEngine.PipelineStep("init", "register_pattern", Map.of( 
                        "name", "init-pattern",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 50.0
                    )),
                    new AepEngine.PipelineStep("process", "register_pattern", Map.of( 
                        "name", "process-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 75.0
                    ), List.of("init")),
                    new AepEngine.PipelineStep("finalize", "register_pattern", Map.of( 
                        "name", "final-pattern",
                        "patternType", "THRESHOLD",
                        "field", "result",
                        "threshold", 90.0
                    ), List.of("process"))
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); 

            // THEN - All patterns should be registered
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); 
            assertThat(patterns).hasSize(3); 
            assertThat(patterns).anyMatch(p -> p.name().equals("init-pattern"));
            assertThat(patterns).anyMatch(p -> p.name().equals("process-pattern"));
            assertThat(patterns).anyMatch(p -> p.name().equals("final-pattern"));
        }

        @Test
        @DisplayName("handles pipeline with no steps")
        void handlesPipelineWithNoSteps() { 
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( 
                "empty-pipeline",
                "Empty Pipeline",
                List.of() 
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); 

            // THEN - Should not throw
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); 
            assertThat(patterns).isEmpty(); 
        }

        @Test
        @DisplayName("rejects pipeline with cycles")
        void rejectsPipelineWithCycles() { 
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( 
                "cyclic-pipeline",
                "Cyclic Pipeline",
                List.of( 
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of(), List.of("step-2")),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of(), List.of("step-1"))
                )
            );

            // WHEN/THEN
            assertThatThrownBy(() -> engine.submitPipeline(tenantId, pipeline)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("cycles");
        }

        @Test
        @DisplayName("executes independent steps without blocking")
        void executesIndependentStepsWithoutBlocking() { 
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( 
                "parallel-pipeline",
                "Parallel Execution Pipeline",
                List.of( 
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( 
                        "name", "pattern-a",
                        "patternType", "THRESHOLD",
                        "field", "field-a",
                        "threshold", 60.0
                    )),
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( 
                        "name", "pattern-b",
                        "patternType", "THRESHOLD",
                        "field", "field-b",
                        "threshold", 70.0
                    ))
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); 

            // THEN - Both patterns should be registered
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); 
            assertThat(patterns).hasSize(2); 
        }

        @Test
        @DisplayName("continues after step failure")
        void continuesAfterStepFailure() { 
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( 
                "resilient-pipeline",
                "Resilient Pipeline",
                List.of( 
                    new AepEngine.PipelineStep("step-1", "unknown_step_type", Map.of("key", "value")), 
                    new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( 
                        "name", "valid-pattern",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 50.0
                    ))
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); 

            // THEN - Valid step should still execute
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); 
            assertThat(patterns).hasSize(1); 
            assertThat(patterns.get(0).name()).isEqualTo("valid-pattern");
        }
    }

    @Nested
    @DisplayName("State Updates")
    class StateUpdateTests {

        @Test
        @DisplayName("pattern state is updated after registration")
        void patternStateIsUpdatedAfterRegistration() { 
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( 
                "state-pipeline",
                "State Update Pipeline",
                List.of( 
                    new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( 
                        "name", "state-test-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 80.0
                    ))
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); 

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); 
            assertThat(patterns).hasSize(1); 
            assertThat(patterns.get(0).name()).isEqualTo("state-test-pattern");
            assertThat(patterns.get(0).type()).isEqualTo(AepEngine.PatternType.THRESHOLD); 
        }

        @Test
        @DisplayName("multiple pipelines can register patterns for same tenant")
        void multiplePipelinesCanRegisterPatternsForSameTenant() { 
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline1 = new AepEngine.Pipeline( 
                "pipeline-1",
                "First Pipeline",
                List.of(new AepEngine.PipelineStep("step-1", "register_pattern", Map.of( 
                    "name", "pattern-1",
                    "patternType", "THRESHOLD",
                    "field", "score",
                    "threshold", 50.0
                )))
            );
            AepEngine.Pipeline pipeline2 = new AepEngine.Pipeline( 
                "pipeline-2",
                "Second Pipeline",
                List.of(new AepEngine.PipelineStep("step-2", "register_pattern", Map.of( 
                    "name", "pattern-2",
                    "patternType", "THRESHOLD",
                    "field", "count",
                    "threshold", 100.0
                )))
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline1); 
            engine.submitPipeline(tenantId, pipeline2); 

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); 
            assertThat(patterns).hasSize(2); 
            assertThat(patterns).anyMatch(p -> p.name().equals("pattern-1"));
            assertThat(patterns).anyMatch(p -> p.name().equals("pattern-2"));
        }
    }

    @Nested
    @DisplayName("Complex DAG Patterns")
    class ComplexDAGTests {

        @Test
        @DisplayName("fan-out pattern executes correctly")
        void fanOutPatternExecutesCorrectly() { 
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( 
                "fan-out-pipeline",
                "Fan-Out Pattern",
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

            // WHEN
            engine.submitPipeline(tenantId, pipeline); 

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); 
            assertThat(patterns).hasSize(3); 
        }

        @Test
        @DisplayName("fan-in pattern executes correctly")
        void fanInPatternExecutesCorrectly() { 
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( 
                "fan-in-pipeline",
                "Fan-In Pattern",
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

            // WHEN
            engine.submitPipeline(tenantId, pipeline); 

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); 
            assertThat(patterns).hasSize(3); 
        }

        @Test
        @DisplayName("diamond pattern executes correctly")
        void diamondPatternExecutesCorrectly() { 
            // GIVEN
            String tenantId = "tenant-test";
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline( 
                "diamond-pipeline",
                "Diamond Pattern",
                List.of( 
                    new AepEngine.PipelineStep("start", "register_pattern", Map.of( 
                        "name", "start-pattern",
                        "patternType", "THRESHOLD",
                        "field", "score",
                        "threshold", 50.0
                    )),
                    new AepEngine.PipelineStep("branch-a", "register_pattern", Map.of( 
                        "name", "pattern-a",
                        "patternType", "THRESHOLD",
                        "field", "field-a",
                        "threshold", 60.0
                    ), List.of("start")),
                    new AepEngine.PipelineStep("branch-b", "register_pattern", Map.of( 
                        "name", "pattern-b",
                        "patternType", "THRESHOLD",
                        "field", "field-b",
                        "threshold", 70.0
                    ), List.of("start")),
                    new AepEngine.PipelineStep("merge", "register_pattern", Map.of( 
                        "name", "merge-pattern",
                        "patternType", "THRESHOLD",
                        "field", "merged",
                        "threshold", 80.0
                    ), List.of("branch-a", "branch-b")) 
                )
            );

            // WHEN
            engine.submitPipeline(tenantId, pipeline); 

            // THEN
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId)); 
            assertThat(patterns).hasSize(4); 
        }
    }
}
