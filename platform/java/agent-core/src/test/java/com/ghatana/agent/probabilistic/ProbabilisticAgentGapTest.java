/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 * Phase 4 — Task 4.1: Gap-filling tests for ProbabilisticAgent.
 */

package com.ghatana.agent.probabilistic;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Gap-filling tests for {@link ProbabilisticAgent}.
 *
 * <p>Fills gaps identified in Phase 4 audit:
 * <ul>
 *   <li>Model inference timeout (simulated via exception)</li> // GH-90000
 *   <li>Batch inference (processBatch from AbstractTypedAgent)</li> // GH-90000
 *   <li>Fallback chain exhaustion with 2+ fallbacks</li>
 *   <li>Shadow mode records metrics but returns SKIPPED</li>
 *   <li>No model set → failure</li>
 *   <li>Calibration edge cases (extreme raw values)</li> // GH-90000
 * </ul>
 */
@DisplayName("Probabilistic Agent — Gap Tests [GH-90000]")
class ProbabilisticAgentGapTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("prob-gap-test [GH-90000]")
                .tenantId("test-tenant [GH-90000]")
                .memoryStore(mock(MemoryStore.class)) // GH-90000
                .build(); // GH-90000
    }

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) { // GH-90000
        AtomicReference<T> result = new AtomicReference<>(); // GH-90000
        AtomicReference<Exception> err = new AtomicReference<>(); // GH-90000
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
        eventloop.post(() -> supplier.get() // GH-90000
                .whenResult(result::set) // GH-90000
                .whenException(err::set)); // GH-90000
        eventloop.run(); // GH-90000
        if (err.get() != null) throw new RuntimeException(err.get()); // GH-90000
        return result.get(); // GH-90000
    }

    // ── Mock helpers ────────────────────────────────────────────────────────

    private ModelInference mockModel(String id, Map<String, Object> output, double confidence) { // GH-90000
        return new ModelInference() { // GH-90000
            @Override
            public @org.jetbrains.annotations.NotNull Promise<InferenceResult> infer( // GH-90000
                    @org.jetbrains.annotations.NotNull Map<String, Object> input) {
                return Promise.of(new InferenceResult(output, confidence, id, 10)); // GH-90000
            }
            @Override
            public @org.jetbrains.annotations.NotNull String modelId() { return id; } // GH-90000
        };
    }

    private ModelInference failingModel(String id) { // GH-90000
        return new ModelInference() { // GH-90000
            @Override
            public @org.jetbrains.annotations.NotNull Promise<InferenceResult> infer( // GH-90000
                    @org.jetbrains.annotations.NotNull Map<String, Object> input) {
                return Promise.ofException(new RuntimeException("Model " + id + " failed")); // GH-90000
            }
            @Override
            public @org.jetbrains.annotations.NotNull String modelId() { return id; } // GH-90000
        };
    }

    private ModelInference timeoutModel(String id) { // GH-90000
        return new ModelInference() { // GH-90000
            @Override
            public @org.jetbrains.annotations.NotNull Promise<InferenceResult> infer( // GH-90000
                    @org.jetbrains.annotations.NotNull Map<String, Object> input) {
                return Promise.ofException(new java.util.concurrent.TimeoutException( // GH-90000
                        "Model " + id + " timed out after 500ms"));
            }
            @Override
            public @org.jetbrains.annotations.NotNull String modelId() { return id; } // GH-90000
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Model inference timeout
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Model Inference Timeout [GH-90000]")
    class TimeoutTests {

        @Test
        void timeoutFallsToFallbackModel() { // GH-90000
            ModelInference primary = timeoutModel("slow-primary [GH-90000]");
            ModelInference fallback = mockModel("fast-fallback", Map.of("label", "ok"), 0.85); // GH-90000

            ProbabilisticAgent agent = new ProbabilisticAgent("timeout-fb", primary); // GH-90000
            agent.setFallbackModels(List.of(fallback)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("timeout-fb [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("label", "ok"); // GH-90000
        }

        @Test
        void timeoutWithNoFallbackReturnsFailed() { // GH-90000
            ModelInference primary = timeoutModel("slow-only [GH-90000]");

            ProbabilisticAgent agent = new ProbabilisticAgent("timeout-no-fb", primary); // GH-90000
            // No fallback models set

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("timeout-no-fb [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000

            assertThat(result.isFailed()).isTrue(); // GH-90000
        }

        @Test
        void timeoutChainsThroughMultipleFallbacks() { // GH-90000
            ModelInference primary = timeoutModel("primary [GH-90000]");
            ModelInference fb1 = failingModel("fallback-1 [GH-90000]");
            ModelInference fb2 = mockModel("fallback-2", Map.of("recovered", true), 0.75); // GH-90000

            ProbabilisticAgent agent = new ProbabilisticAgent("chain-fb", primary); // GH-90000
            agent.setFallbackModels(List.of(fb1, fb2)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("chain-fb [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("recovered", true); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch inference
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Batch Inference [GH-90000]")
    class BatchInferenceTests {

        @Test
        void processBatchExecutesForEachInput() { // GH-90000
            ModelInference model = mockModel("batch-model", Map.of("label", "pos"), 0.9); // GH-90000
            ProbabilisticAgent agent = new ProbabilisticAgent("batch-test", model); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("batch-test [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000

            List<Map<String, Object>> inputs = List.of( // GH-90000
                    Map.of("text", "good"), // GH-90000
                    Map.of("text", "great"), // GH-90000
                    Map.of("text", "excellent")); // GH-90000

            List<AgentResult<Map<String, Object>>> results =
                    runOnEventloop(() -> agent.processBatch(ctx, inputs)); // GH-90000

            assertThat(results).hasSize(3); // GH-90000
            for (AgentResult<Map<String, Object>> r : results) { // GH-90000
                assertThat(r.isSuccess()).isTrue(); // GH-90000
                assertThat(r.getOutput()).containsEntry("label", "pos"); // GH-90000
            }
        }

        @Test
        void processBatchWithEmptyListReturnsEmpty() { // GH-90000
            ModelInference model = mockModel("batch-empty", Map.of("ok", true), 0.9); // GH-90000
            ProbabilisticAgent agent = new ProbabilisticAgent("batch-empty", model); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("batch-empty [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000

            List<AgentResult<Map<String, Object>>> results =
                    runOnEventloop(() -> agent.processBatch(ctx, List.of())); // GH-90000

            assertThat(results).isEmpty(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Shadow mode — enriched observation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Shadow Mode — enriched [GH-90000]")
    class ShadowModeEnrichedTests {

        @Test
        void shadowModeOutputContainsModelMetadata() { // GH-90000
            ModelInference model = mockModel("shadow-model", Map.of("label", "A"), 0.92); // GH-90000
            ProbabilisticAgent agent = new ProbabilisticAgent("shadow-enrich", model); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("shadow-enrich [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .shadowMode(true) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
            // Even in shadow, output should contain model metadata
            assertThat(result.getOutput()).containsEntry("_model.id", "shadow-model"); // GH-90000
            assertThat(result.getOutput()).containsKey("_model.calibratedConfidence [GH-90000]");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // No model set
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("No model set [GH-90000]")
    class NoModelTests {

        @Test
        void processWithoutPrimaryModelReturnsFailed() { // GH-90000
            ProbabilisticAgent agent = new ProbabilisticAgent("no-model [GH-90000]");

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("no-model [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000

            assertThat(result.isFailed()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Calibration edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Calibration edge cases [GH-90000]")
    class CalibrationEdgeCases {

        @Test
        void extremelyHighRawConfidenceClampedToOne() { // GH-90000
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder() // GH-90000
                    .method(ConfidenceCalibrator.Method.IDENTITY).build(); // GH-90000
            assertThat(cal.calibrate(100.0)).isCloseTo(1.0, within(1e-9)); // GH-90000
        }

        @Test
        void negativeRawConfidenceClampedToZero() { // GH-90000
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder() // GH-90000
                    .method(ConfidenceCalibrator.Method.IDENTITY).build(); // GH-90000
            assertThat(cal.calibrate(-50.0)).isCloseTo(0.0, within(1e-9)); // GH-90000
        }

        @Test
        void temperatureScalingWithVeryHighTemperatureCompresses() { // GH-90000
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder() // GH-90000
                    .method(ConfidenceCalibrator.Method.TEMPERATURE) // GH-90000
                    .temperature(100.0) // GH-90000
                    .build(); // GH-90000
            double result = cal.calibrate(0.99); // GH-90000
            // Very high temperature compresses confidence toward zero
            assertThat(result).isBetween(0.0, 0.1); // GH-90000
        }

        @Test
        void temperatureScalingWithVeryLowTemperaturePolarizes() { // GH-90000
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder() // GH-90000
                    .method(ConfidenceCalibrator.Method.TEMPERATURE) // GH-90000
                    .temperature(0.01) // GH-90000
                    .build(); // GH-90000
            // 0.99 → logit is very positive → dividing by 0.01 makes it huge → sigmoid ≈ 1.0
            double result = cal.calibrate(0.99); // GH-90000
            assertThat(result).isGreaterThan(0.99); // GH-90000
        }
    }
}
