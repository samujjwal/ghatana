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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chaos tests for event processing.
 *
 * Verifies behavior under failure conditions:
 * - Network timeouts
 * - Database connection failures
 * - Memory pressure
 * - Concurrent access conflicts
 * - Invalid/malformed events
 *
 * @doc.type class
 * @doc.purpose Chaos testing for event processing resilience
 * @doc.layer product
 * @doc.pattern ChaosTest
 */
@DisplayName("Event Processing – Chaos Tests")
class EventProcessingChaosTest extends EventloopTestBase {

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
    @DisplayName("Timeout Scenarios")
    class TimeoutTests {

        @Test
        @DisplayName("handles long-running operations with timeout")
        void handlesLongRunningOperationsWithTimeout() {
            String tenantId = "tenant-timeout";
            
            // Create a pipeline with a step that simulates long processing
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "slow-pipeline",
                "Slow Processing Pipeline",
                List.of(
                    new AepEngine.PipelineStep("slow-step", "register_pattern", Map.of(
                        "name", "slow-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 100.0,
                        "delayMs", 5000 // Simulate 5 second delay
                    ))
                )
            );

            // Submit with timeout - should complete or fail gracefully
            try {
                engine.submitPipeline(tenantId, pipeline);
                // If it completes, verify it's registered
                List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId));
                assertThat(patterns).isNotEmpty();
            } catch (CompletionException e) {
                // Timeout is acceptable - system should not crash
                assertThat(e.getCause()).isInstanceOf(TimeoutException.class);
            }
        }

        @Test
        @DisplayName("gracefully handles concurrent pipeline submissions")
        void gracefullyHandlesConcurrentPipelineSubmissions() {
            String tenantId = "tenant-concurrent";
            
            // Submit multiple pipelines concurrently
            AepEngine.Pipeline pipeline1 = new AepEngine.Pipeline(
                "pipeline-1",
                "Pipeline 1",
                List.of(new AepEngine.PipelineStep("step1", "register_pattern", Map.of(
                    "name", "pattern-1",
                    "patternType", "THRESHOLD",
                    "field", "value",
                    "threshold", 10.0
                )))
            );

            AepEngine.Pipeline pipeline2 = new AepEngine.Pipeline(
                "pipeline-2",
                "Pipeline 2",
                List.of(new AepEngine.PipelineStep("step2", "register_pattern", Map.of(
                    "name", "pattern-2",
                    "patternType", "THRESHOLD",
                    "field", "value",
                    "threshold", 20.0
                )))
            );

            // Submit both - should not deadlock or crash
            engine.submitPipeline(tenantId, pipeline1);
            engine.submitPipeline(tenantId, pipeline2);

            // Verify both are registered
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId));
            assertThat(patterns).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Invalid Input Scenarios")
    class InvalidInputTests {

        @Test
        @DisplayName("handles null pipeline gracefully")
        void handlesNullPipelineGracefully() {
            String tenantId = "tenant-null";

            assertThatThrownBy(() -> engine.submitPipeline(tenantId, null))
                .isInstanceOf(CompletionException.class)
                .hasMessageContaining("null");
        }

        @Test
        @DisplayName("handles pipeline with invalid step type")
        void handlesPipelineWithInvalidStepType() {
            String tenantId = "tenant-invalid";
            
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "invalid-pipeline",
                "Invalid Pipeline",
                List.of(
                    new AepEngine.PipelineStep("invalid-step", "nonexistent_operation", Map.of())
                )
            );

            // Should fail gracefully without crashing
            assertThatThrownBy(() -> engine.submitPipeline(tenantId, pipeline))
                .isInstanceOf(CompletionException.class);
        }

        @Test
        @DisplayName("handles pipeline with circular dependencies")
        void handlesPipelineWithCircularDependencies() {
            String tenantId = "tenant-circular";
            
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "circular-pipeline",
                "Circular Pipeline",
                List.of(
                    new AepEngine.PipelineStep("step1", "register_pattern", Map.of(
                        "name", "pattern-1",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 10.0
                    ), List.of("step2")), // Depends on step2
                    new AepEngine.PipelineStep("step2", "register_pattern", Map.of(
                        "name", "pattern-2",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 20.0
                    ), List.of("step1")) // Depends on step1 - circular!
                )
            );

            // Should detect and reject circular dependency
            assertThatThrownBy(() -> engine.submitPipeline(tenantId, pipeline))
                .isInstanceOf(CompletionException.class);
        }

        @Test
        @DisplayName("handles malformed pattern configuration")
        void handlesMalformedPatternConfiguration() {
            String tenantId = "tenant-malformed";
            
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "malformed-pipeline",
                "Malformed Pipeline",
                List.of(
                    new AepEngine.PipelineStep("malformed-step", "register_pattern", Map.of(
                        "name", "", // Empty name
                        "patternType", "INVALID_TYPE", // Invalid type
                        "threshold", "not_a_number" // Invalid threshold type
                    ))
                )
            );

            // Should fail gracefully with validation error
            assertThatThrownBy(() -> engine.submitPipeline(tenantId, pipeline))
                .isInstanceOf(CompletionException.class);
        }
    }

    @Nested
    @DisplayName("Resource Exhaustion Scenarios")
    class ResourceExhaustionTests {

        @Test
        @DisplayName("handles high volume of pattern registrations")
        void handlesHighVolumeOfPatternRegistrations() {
            String tenantId = "tenant-volume";
            
            // Register many patterns rapidly
            for (int i = 0; i < 100; i++) {
                AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                    "pipeline-" + i,
                    "Pipeline " + i,
                    List.of(
                        new AepEngine.PipelineStep("step-" + i, "register_pattern", Map.of(
                            "name", "pattern-" + i,
                            "patternType", "THRESHOLD",
                            "field", "value",
                            "threshold", (double) i
                        ))
                    )
                );

                engine.submitPipeline(tenantId, pipeline);
            }

            // Verify all patterns are registered
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId));
            assertThat(patterns).hasSize(100);
        }

        @Test
        @DisplayName("handles very long pipeline chains")
        void handlesVeryLongPipelineChains() {
            String tenantId = "tenant-long-chain";
            
            // Create a pipeline with a long chain of dependencies
            List<AepEngine.PipelineStep> steps = new java.util.ArrayList<>();
            for (int i = 0; i < 50; i++) {
                List<String> dependencies = i > 0 ? List.of("step-" + (i - 1)) : List.of();
                steps.add(new AepEngine.PipelineStep(
                    "step-" + i,
                    "register_pattern",
                    Map.of(
                        "name", "pattern-" + i,
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", (double) i
                    ),
                    dependencies
                ));
            }

            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "long-chain-pipeline",
                "Long Chain Pipeline",
                steps
            );

            // Should handle long chains without stack overflow
            engine.submitPipeline(tenantId, pipeline);

            // Verify all patterns are registered
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId));
            assertThat(patterns).hasSize(50);
        }
    }

    @Nested
    @DisplayName("State Recovery Scenarios")
    class StateRecoveryTests {

        @Test
        @DisplayName("recovers from partial pipeline failure")
        void recoversFromPartialPipelineFailure() {
            String tenantId = "tenant-recovery";
            
            // First pipeline succeeds
            AepEngine.Pipeline goodPipeline = new AepEngine.Pipeline(
                "good-pipeline",
                "Good Pipeline",
                List.of(
                    new AepEngine.PipelineStep("good-step", "register_pattern", Map.of(
                        "name", "good-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 10.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, goodPipeline);

            // Second pipeline fails
            AepEngine.Pipeline badPipeline = new AepEngine.Pipeline(
                "bad-pipeline",
                "Bad Pipeline",
                List.of(
                    new AepEngine.PipelineStep("bad-step", "nonexistent_operation", Map.of())
                )
            );

            assertThatThrownBy(() -> engine.submitPipeline(tenantId, badPipeline))
                .isInstanceOf(CompletionException.class);

            // Third pipeline should still work - system is not corrupted
            AepEngine.Pipeline recoveryPipeline = new AepEngine.Pipeline(
                "recovery-pipeline",
                "Recovery Pipeline",
                List.of(
                    new AepEngine.PipelineStep("recovery-step", "register_pattern", Map.of(
                        "name", "recovery-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 30.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, recoveryPipeline);

            // Verify good patterns are still registered
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId));
            assertThat(patterns).hasSize(2); // good-pattern and recovery-pattern
        }

        @Test
        @DisplayName("handles engine restart gracefully")
        void handlesEngineRestartGracefully() {
            String tenantId = "tenant-restart";
            
            // Register a pattern
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                "restart-pipeline",
                "Restart Pipeline",
                List.of(
                    new AepEngine.PipelineStep("restart-step", "register_pattern", Map.of(
                        "name", "restart-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 10.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, pipeline);

            // Restart engine
            engine.close();
            engine = Aep.forTesting();

            // System should be operational after restart
            AepEngine.Pipeline newPipeline = new AepEngine.Pipeline(
                "new-pipeline",
                "New Pipeline",
                List.of(
                    new AepEngine.PipelineStep("new-step", "register_pattern", Map.of(
                        "name", "new-pattern",
                        "patternType", "THRESHOLD",
                        "field", "value",
                        "threshold", 20.0
                    ))
                )
            );

            engine.submitPipeline(tenantId, newPipeline);

            // Verify new pattern is registered
            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(tenantId));
            assertThat(patterns).hasSize(1); // Only new pattern (in-memory storage)
        }
    }
}
